/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.opensearch

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.ArtifactFilePage
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.IndexState
import net.onelitefeather.ingot.search.IndexStatus

import net.onelitefeather.ingot.search.SearchFacets
import net.onelitefeather.ingot.search.SearchUnavailableException
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.BackendHealth
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.backend.SamplePage
import net.onelitefeather.ingot.search.backend.StoredRow
import net.onelitefeather.ingot.search.database.pathHash
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.backend.SampledRow
import java.time.Clock
import java.time.Duration
import javax.net.ssl.SSLContext
import kotlin.random.Random

/**
 * The `opensearch` backend (design D1 to D6): the index of artifact search in one OpenSearch alias. The core runs the outbox,
 * the rebuild and the verify job against this class; the reads are [OpenSearchReader], the writes [OpenSearchWriter], and every
 * request goes through one circuit breaker on the clock of the server. Every message it returns is redacted of the password and
 * of the user name, even when the cluster echoes them.
 *
 * [sslContext] is null in production, which uses the JVM default context with hostname verification. The container tests pass
 * the context of their own authority.
 */
internal class OpenSearchSearchBackend(
    val settings: OpenSearchSettings,
    clock: Clock,
    sslContext: SSLContext?,
    exchange: HttpExchange? = null,
) : ArtifactSearchBackend {

    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
    private val alias: String = settings.index
    private val physical: String = "$alias-v1"
    private val redactor = OpenSearchRedactor(listOfNotNull(settings.password, settings.username))
    private val timeout: Duration = Duration.ofSeconds(settings.timeoutSeconds.toLong())
    private val breaker = OpenSearchCircuitBreaker(clock)
    private val http = OpenSearchHttp(
        settings.urls,
        settings.username,
        settings.password,
        timeout,
        exchange ?: JdkHttpExchange(timeout, sslContext),
    )
    private val client = OpenSearchClient(http, breaker, redactor)
    private val writer = OpenSearchWriter(http, breaker, alias, settings.bulkSize, redactor)
    private val reader = OpenSearchReader(client, alias) { status() }

    override val id: String = OpenSearchBackendFactory.ID

    /** The cluster cannot commit with the cursor of a rebuild, so the rebuild applies its page first and commits the cursor after. */
    override val joinsDatabaseTransaction: Boolean = false

    /**
     * Connects to the cluster, creates the physical index and the alias when they are missing, refuses an index that another
     * mapping version wrote, and installs the reorigin script. A failure throws with a redacted message, which the core reports.
     */
    override fun open() {
        if (client.read("GET", "/_cluster/health").path("status").asText() == "red") {
            throw IllegalStateException("the cluster is red, the index cannot be opened")
        }
        val answer = client.send("GET", "/_alias/$alias")
        when (answer.status) {
            200 -> {
                val indices = mapper.readTree(answer.body).fieldNames().asSequence().toList()
                check(indices.size == 1) { "the alias $alias points to ${indices.size} indices, one is expected" }
                verifyVersion(indices.single())
            }
            404 -> {
                createIndex()
                verifyVersion(physical)
                client.read("POST", "/_aliases", mapOf("actions" to listOf(mapOf("add" to mapOf("index" to physical, "alias" to alias)))))
            }
            else -> throw IllegalStateException(redactor.redact("the alias $alias cannot be read, the cluster answered ${answer.status}"))
        }
        client.read(
            "PUT",
            "/_scripts/${OpenSearchRequests.REORIGIN_SCRIPT_ID}",
            mapOf("script" to mapOf("lang" to "painless", "source" to OpenSearchRequests.REORIGIN_SCRIPT_SOURCE)),
        )
    }

    /** Nothing to release: the connections belong to the JDK client, which closes with the process. */
    override fun close() = Unit

    /** Up when the cluster answers and is not red; down while the breaker pauses the requests, and with the reason otherwise. */
    override fun health(): BackendHealth {
        if (breaker.openRemaining() != null) return BackendHealth.Down(redactor.redact(PAUSED))
        return try {
            if (client.read("GET", "/_cluster/health").path("status").asText() == "red") {
                BackendHealth.Down("the cluster status is red")
            } else {
                BackendHealth.Up
            }
        } catch (unavailable: SearchUnavailableException) {
            BackendHealth.Down(redactor.redact(unavailable.message ?: OpenSearchClient.UNREACHABLE))
        }
    }

    override fun apply(actions: List<IndexAction>) = writer.apply(actions)

    override fun pathsAt(repository: String, prefix: String, after: String?, limit: Int): List<String> {
        val filter = listOf(
            mapOf("term" to mapOf("repository" to repository)),
            mapOf(
                "bool" to mapOf(
                    "should" to listOf(
                        mapOf("term" to mapOf("path" to prefix)),
                        mapOf("prefix" to mapOf("path" to "$prefix/")),
                    ),
                    "minimum_should_match" to 1,
                ),
            ),
        )
        val body = linkedMapOf<String, Any>(
            "size" to limit,
            "_source" to listOf("path"),
            "query" to mapOf("bool" to mapOf("filter" to filter)),
            "sort" to listOf(mapOf("path" to "asc")),
        )
        after?.let { body["search_after"] = listOf(it) }
        return client.read("POST", "/$alias/_search", body).path("hits").path("hits").map { it.path("_source").path("path").asText() }
    }

    /** The stored state of the rows of [paths]: a realtime read by document id, so a write is seen at once. */
    override fun existing(repository: String, paths: Collection<String>): Map<String, StoredRow> {
        if (paths.isEmpty()) return emptyMap()
        val ids = paths.associateBy { pathHash(repository, it) }
        val found = HashMap<String, StoredRow>()
        ids.keys.chunked(LOOKUP_CHUNK).forEach { chunk ->
            val docs = client.read(
                "POST",
                "/$alias/_mget?_source_includes=size_bytes,last_modified,sha1,sha256",
                mapOf("ids" to chunk),
            ).path("docs")
            docs.filter { it.path("found").asBoolean() }.forEach { doc ->
                val source = doc.path("_source")
                found[ids.getValue(doc.path("_id").asText())] = StoredRow(
                    sizeBytes = source.path("size_bytes").asLong(),
                    lastModified = source.path("last_modified").asLong(),
                    sha1 = source.textOrNull("sha1"),
                    sha256 = source.textOrNull("sha256"),
                )
            }
        }
        return found
    }

    /** Sets the indexed time of the rows that exist. A row removed meanwhile is not an error, as the database touch is not. */
    override fun touch(repository: String, paths: Collection<String>, indexedAt: Long): Int {
        val present = existing(repository, paths).keys.toList()
        present.chunked(settings.bulkSize).forEach { chunk ->
            val lines = chunk.flatMap { path ->
                listOf(
                    mapOf("update" to mapOf("_index" to alias, "_id" to pathHash(repository, path))),
                    mapOf("doc" to mapOf("indexed_at" to indexedAt)),
                )
            }
            bulk(lines)
        }
        return present.size
    }

    override fun deleteStale(repository: String, before: Long, limit: Int): Int = deleteByQuery(
        listOf(
            mapOf("term" to mapOf("repository" to repository)),
            mapOf("range" to mapOf("indexed_at" to mapOf("lt" to before))),
        ),
        limit,
    )

    override fun deleteRepository(repository: String, limit: Int): Int =
        deleteByQuery(listOf(mapOf("term" to mapOf("repository" to repository))), limit)

    override fun repositories(): List<String> {
        val body = mapOf(
            "size" to 0,
            "aggs" to mapOf("repositories" to mapOf("terms" to mapOf("field" to "repository", "size" to REPOSITORY_LIMIT))),
        )
        return client.read("POST", "/$alias/_search", body)
            .path("aggregations").path("repositories").path("buckets")
            .map { it.path("key").asText() }
            .sorted()
    }

    override fun count(): Long = client.read("GET", "/$alias/_count").path("count").asLong()

    /** A random row, drawn with a seed from [random]: the cursor of a verify sample, or null for an empty index. */
    override fun sampleStart(random: Random): String? {
        if (count() == 0L) return null
        val body = mapOf(
            "size" to 1,
            "_source" to listOf("repository", "path"),
            "query" to mapOf(
                "function_score" to mapOf(
                    "query" to mapOf("match_all" to emptyMap<String, Any>()),
                    "random_score" to mapOf("seed" to random.nextInt(), "field" to "_seq_no"),
                ),
            ),
        )
        val source = client.read("POST", "/$alias/_search", body).path("hits").path("hits").firstOrNull()?.path("_source")
            ?: return null
        return cursorOf(source.path("repository").asText(), source.path("path").asText())
    }

    /**
     * Up to [limit] rows after [cursor] in the order of repository and path. When the rows after the cursor end before the limit,
     * the sample continues with the rows at or before the cursor, from the start, so a small index is covered completely and no
     * row is returned twice.
     */
    override fun sample(cursor: String?, limit: Int): SamplePage {
        val after = cursor?.let(::decodeCursor)
        val rows = sampleRows(after, limit).toMutableList()
        if (cursor != null && rows.size < limit) {
            rows += sampleRows(null, limit)
                .filter { cursorOf(it.repository, it.path) <= cursor }
                .take(limit - rows.size)
        }
        return SamplePage(rows, rows.lastOrNull()?.let { cursorOf(it.repository, it.path) })
    }

    override fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage = reader.search(query, access)

    override fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets = reader.facets(query, access)

    override fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage = reader.files(query, access)

    /**
     * The state of the index from the cluster (4.8): missing when it holds no row, ready otherwise, degraded when the cluster does
     * not answer. The outbox counters and the rebuild history are kept by the core, which reports them with this state.
     */
    override fun status(): IndexStatus {
        val state = when (runCatching { count() }.getOrNull()) {
            null -> IndexState.DEGRADED
            0L -> IndexState.MISSING
            else -> IndexState.READY
        }
        return IndexStatus(
            state = state,
            lagSeconds = 0L,
            lastFullRebuild = null,
            deadLetters = 0L,
            droppedEnqueues = 0L,
            pendingEntries = 0L,
            tokensComplete = true,
        )
    }

    // ---- writes by query ----------------------------------------------------------------------------------------------

    private fun deleteByQuery(filter: List<Map<String, Any>>, limit: Int): Int {
        val body = mapOf("query" to mapOf("bool" to mapOf("filter" to filter)))
        val answer = client.send(
            "POST",
            "/$alias/_delete_by_query?conflicts=proceed&max_docs=$limit",
            mapper.writeValueAsString(body),
        )
        if (answer.status !in 200..299) {
            throw SearchUnavailableException(redactor.redact("the delete request answered ${answer.status}"))
        }
        return mapper.readTree(answer.body).path("deleted").asInt()
    }

    private fun bulk(lines: List<Map<String, Any>>) {
        val body = lines.joinToString(separator = "") { mapper.writeValueAsString(it) + "\n" }
        val answer = client.send("POST", "/_bulk", body, OpenSearchHttp.NDJSON)
        if (answer.status !in 200..299) {
            throw OpenSearchRejectedException(redactor.redact("the bulk request answered ${answer.status}"))
        }
        val report = mapper.readTree(answer.body)
        if (!report.path("errors").asBoolean()) return
        val rejected = report.path("items").map { it.elements().next() }.firstOrNull { item ->
            val status = item.path("status").asInt()
            status !in 200..299 && status != 404
        }
        if (rejected != null) {
            throw OpenSearchRejectedException(redactor.redact("the cluster rejected an update: ${rejected.path("error").path("reason").asText()}"))
        }
    }

    // ---- open -----------------------------------------------------------------------------------------------------------

    /** Creates the physical index. An index that exists already is kept, so the version check decides what happens to it. */
    private fun createIndex() {
        val answer = client.send("PUT", "/$physical", mapper.writeValueAsString(OpenSearchIndexDefinition.create(settings.replicas)))
        val existed = answer.status == 400 && answer.body.contains("resource_already_exists_exception")
        if (answer.status !in 200..299 && !existed) {
            throw IllegalStateException(redactor.redact("the index $physical cannot be created, the cluster answered ${answer.status}"))
        }
    }

    /** Refuses an index whose recorded mapping version is not the one this plugin writes (design D3). */
    private fun verifyVersion(index: String) {
        val version = client.read("GET", "/$index/_mapping")
            .path(index).path("mappings").path("_meta").path("ingot_mapping_version").asInt(-1)
        check(version == OpenSearchIndexDefinition.MAPPING_VERSION) {
            "the index $index records mapping version $version, this plugin writes mapping version ${OpenSearchIndexDefinition.MAPPING_VERSION}"
        }
    }

    // ---- sampling -------------------------------------------------------------------------------------------------------

    private fun sampleRows(after: Pair<String, String>?, limit: Int): List<SampledRow> {
        val body = linkedMapOf<String, Any>(
            "size" to limit,
            "_source" to SAMPLE_FIELDS,
            "query" to mapOf("match_all" to emptyMap<String, Any>()),
            "sort" to listOf(mapOf("repository" to "asc"), mapOf("path" to "asc")),
        )
        after?.let { body["search_after"] = listOf(it.first, it.second) }
        return client.read("POST", "/$alias/_search", body).path("hits").path("hits").map { hit ->
            val source = hit.path("_source")
            SampledRow(
                repository = source.path("repository").asText(),
                path = source.path("path").asText(),
                sizeBytes = source.path("size_bytes").asLong(),
                lastModified = source.path("last_modified").asLong(),
                origin = Origin.valueOf(source.path("origin").asText()),
                sourceHost = source.textOrNull("source_host"),
            )
        }
    }

    private fun cursorOf(repository: String, path: String): String = "$repository\n$path"

    private fun decodeCursor(cursor: String): Pair<String, String> {
        val separator = cursor.indexOf('\n')
        require(separator >= 0) { "the cursor is not a sample cursor of this backend" }
        return cursor.substring(0, separator) to cursor.substring(separator + 1)
    }

    private fun JsonNode.textOrNull(field: String): String? = path(field).takeUnless { it.isNull || it.isMissingNode }?.asText()

    private companion object {
        const val PAUSED = "the search cluster is not reachable, requests are paused"
        const val LOOKUP_CHUNK = 500
        const val REPOSITORY_LIMIT = 10_000
        val SAMPLE_FIELDS = listOf("repository", "path", "size_bytes", "last_modified", "origin", "source_host")
    }

}
