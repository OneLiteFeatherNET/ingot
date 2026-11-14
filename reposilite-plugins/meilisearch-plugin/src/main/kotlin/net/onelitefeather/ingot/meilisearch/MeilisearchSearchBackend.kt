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

package net.onelitefeather.ingot.meilisearch

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
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.BackendHealth
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.backend.SamplePage
import net.onelitefeather.ingot.search.backend.SampledRow
import net.onelitefeather.ingot.search.backend.StoredRow
import net.onelitefeather.ingot.search.database.pathHash
import net.onelitefeather.ingot.search.http.HttpExchange
import net.onelitefeather.ingot.search.http.JdkHttpExchange
import net.onelitefeather.ingot.search.Origin
import java.time.Clock
import java.time.Duration
import kotlin.random.Random

/**
 * The `meilisearch` backend (design D1 to D8): the artifact index of one Meilisearch index. The core runs the outbox, the rebuild
 * and the verify job against this class. Writes are tasks awaited within a bound ([MeilisearchWriter]); reads are filters and
 * searches ([MeilisearchReader]). Every request goes through one circuit breaker on the clock of the server.
 *
 * [exchange] is null in production, which uses the JDK client. [log] receives the changes of the index settings (open question 6).
 */
internal class MeilisearchSearchBackend(
    val settings: MeilisearchSettings,
    private val clock: Clock,
    exchange: HttpExchange? = null,
    waiter: MeilisearchWaiter = SleepingWaiter,
    private val log: (String) -> Unit = {},
) : ArtifactSearchBackend {

    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
    private val index: String = settings.index
    private val timeout: Duration = Duration.ofSeconds(settings.timeoutSeconds.toLong())
    private val client = MeilisearchClient(settings.url, settings.apiKey, timeout, clock, exchange ?: JdkHttpExchange(timeout))
    private val writer = MeilisearchWriter(
        client,
        index,
        settings.batchSize,
        Duration.ofSeconds(settings.taskWaitSeconds.toLong()),
        clock,
        waiter,
    )
    private val reader = MeilisearchReader(client, index, settings.aggregateSortLimit) { status() }

    /** The task of a settings change that outlived the bound of [open]. Searches wait for it, and the health reports it. */
    @Volatile
    private var pendingSettingsTask: Long? = null

    override val id: String = MeilisearchBackendFactory.ID

    /** A write is a task of the server, so the rebuild cursor is committed after the write, never in the same transaction. */
    override val joinsDatabaseTransaction: Boolean = false

    /**
     * Checks the server version (the minor version this plugin is tested with), creates the index when it is missing, and applies
     * the settings of the index. A changed setting is logged with its old and new value. A settings task that outlives the bound
     * is kept pending: the health is down and searches are refused until it has finished.
     */
    override fun open() {
        val version = client.json("GET", "/version").path("pkgVersion").asText()
        check(version.startsWith("$SUPPORTED_MINOR.")) {
            "the server runs meilisearch $version, this plugin supports $SUPPORTED_MINOR.x"
        }
        val present = client.send("GET", "/indexes/$index")
        when (present.status) {
            in 200..299 -> Unit
            404 -> {
                val task = client.json("POST", "/indexes", mapOf("uid" to index, "primaryKey" to "id")).path("taskUid").asLong()
                writer.await(listOf(task))
            }
            else -> throw IllegalStateException(client.redact("the index $index cannot be read, the server answered ${present.status}"))
        }
        applySettings()
    }

    override fun close() = Unit

    override fun health(): BackendHealth {
        if (client.pausedFor() != null) return BackendHealth.Down(PAUSED)
        return try {
            when {
                settingsRunning() -> BackendHealth.Down(SETTINGS_RUNNING)
                client.json("GET", "/health").path("status").asText() == "available" -> BackendHealth.Up
                else -> BackendHealth.Down("the search server does not report itself as available")
            }
        } catch (unavailable: SearchUnavailableException) {
            BackendHealth.Down(client.redact(unavailable.message ?: PAUSED))
        } catch (busy: BackendBusyException) {
            BackendHealth.Down(PAUSED)
        }
    }

    override fun apply(actions: List<IndexAction>) = writer.apply(actions)

    override fun pathsAt(repository: String, prefix: String, after: String?, limit: Int): List<String> {
        val scope = "repository = ${MeilisearchFilters.quote(repository)} AND ${MeilisearchFilters.path(PathPredicate.Exact(prefix))}"
        val paths = fetch(scope, listOf("path")).map { it.path("path").asText() }.sorted()
        val rest = if (after == null) paths else paths.dropWhile { it <= after }
        return rest.take(limit)
    }

    /** The stored state of the rows of [paths]: a read by document id, so a write whose task succeeded is seen. */
    override fun existing(repository: String, paths: Collection<String>): Map<String, StoredRow> {
        if (paths.isEmpty()) return emptyMap()
        val ids = paths.associateBy { MeilisearchDocument.id(repository, it) }
        val found = HashMap<String, StoredRow>()
        ids.keys.chunked(LOOKUP_CHUNK).forEach { chunk ->
            val docs = client.json(
                "POST",
                "/indexes/$index/documents/fetch",
                mapOf(
                    "ids" to chunk,
                    "fields" to listOf("id", "size_bytes", "last_modified", "sha1", "sha256"),
                    "limit" to chunk.size,
                ),
            ).path("results")
            docs.forEach { doc ->
                val path = ids[doc.path("id").asText()] ?: return@forEach
                found[path] = StoredRow(
                    sizeBytes = doc.path("size_bytes").asLong(),
                    lastModified = doc.path("last_modified").asLong(),
                    sha1 = doc.textOrNull("sha1"),
                    sha256 = doc.textOrNull("sha256"),
                )
            }
        }
        return found
    }

    override fun touch(repository: String, paths: Collection<String>, indexedAt: Long): Int {
        val present = existing(repository, paths).keys.toList()
        if (present.isEmpty()) return 0
        writer.update(present.map { path -> mapper.createObjectNode().put("id", pathHash(repository, path)).put("indexed_at", indexedAt) })
        return present.size
    }

    override fun deleteStale(repository: String, before: Long, limit: Int): Int {
        val scope = "repository = ${MeilisearchFilters.quote(repository)} AND indexed_at < $before"
        val ids = writer.idsWhere(scope, limit)
        if (ids.isEmpty()) return 0
        writer.deleteWhere("$scope AND id IN ${MeilisearchFilters.inList(ids)}")
        return ids.size
    }

    override fun deleteRepository(repository: String, limit: Int): Int {
        val scope = "repository = ${MeilisearchFilters.quote(repository)}"
        val ids = writer.idsWhere(scope, limit)
        if (ids.isEmpty()) return 0
        writer.deleteWhere("$scope AND id IN ${MeilisearchFilters.inList(ids)}")
        return ids.size
    }

    override fun repositories(): List<String> = client.json(
        "POST",
        "/indexes/$index/search",
        mapOf("q" to "", "facets" to listOf("repository"), "hitsPerPage" to 0, "page" to 1),
    ).path("facetDistribution").path("repository").fieldNames().asSequence().sorted().toList()

    override fun count(): Long = client.json("GET", "/indexes/$index/stats").path("numberOfDocuments").asLong()

    /** A random row of the index, as the cursor of a verify sample, or null for an empty index. */
    override fun sampleStart(random: Random): String? {
        val rows = sampledRows()
        if (rows.isEmpty()) return null
        val row = rows[random.nextInt(rows.size)]
        return cursorOf(row.repository, row.path)
    }

    /**
     * Up to [limit] rows after [cursor] in the order of repository and path. When the rows after the cursor end before the limit, the
     * sample continues from the start, so a small index is covered completely and no row is returned twice.
     */
    override fun sample(cursor: String?, limit: Int): SamplePage {
        val rows = sampledRows()
        val after = cursor?.let(::decodeCursor)
        val ordered = if (after == null) rows else rows.dropWhile { cursorOf(it.repository, it.path) <= cursorOf(after.first, after.second) }
        val picked = ordered.take(limit).toMutableList()
        if (cursor != null && picked.size < limit) {
            picked += rows.filter { cursorOf(it.repository, it.path) <= cursor }.take(limit - picked.size)
        }
        return SamplePage(picked, picked.lastOrNull()?.let { cursorOf(it.repository, it.path) })
    }

    override fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage {
        refuseWhileSettingsRun()
        return reader.search(query, access)
    }

    override fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets {
        refuseWhileSettingsRun()
        return reader.facets(query, access)
    }

    override fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage {
        refuseWhileSettingsRun()
        return reader.files(query, access)
    }

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

    /** The identity of the index: its creation time, which changes only when the operator recreates the index. */
    override fun indexIdentity(): String? {
        val answer = client.send("GET", "/indexes/$index")
        if (answer.status !in 200..299) return null
        return mapper.readTree(answer.body).path("createdAt").asText().takeIf { it.isNotEmpty() }
    }

    /** The consistency statement of the status block. No connection detail is reported. */
    override fun properties(): Map<String, String> = mapOf(
        "index" to index,
        "consistency" to "a write is visible once its task has succeeded",
    )

    // ---- settings -------------------------------------------------------------------------------------------------------

    private fun applySettings() {
        val stored = client.json("GET", "/indexes/$index/settings")
        val changes = MeilisearchIndexSettings.differences(stored)
        if (changes.isEmpty()) return
        changes.forEach { log("settings of index $index changed: ${it.name} from ${it.stored} to ${it.desired}") }
        val task = client.json("PATCH", "/indexes/$index/settings", MeilisearchIndexSettings.desired).path("taskUid").asLong()
        try {
            writer.await(listOf(task))
        } catch (busy: BackendBusyException) {
            pendingSettingsTask = task
        }
    }

    /** Whether the settings task of [applySettings] still runs. A finished task is forgotten, and a failed one is logged. */
    private fun settingsRunning(): Boolean {
        val task = pendingSettingsTask ?: return false
        val status = client.json("GET", "/tasks/$task")
        return when (status.path("status").asText()) {
            "succeeded" -> false.also { pendingSettingsTask = null }
            "failed" -> false.also {
                pendingSettingsTask = null
                log("settings task $task of index $index failed: ${status.path("error").path("code").asText("unknown")}")
            }
            else -> true
        }
    }

    private fun refuseWhileSettingsRun() {
        if (settingsRunning()) throw SearchUnavailableException("the index is being reindexed for a settings change, try again later")
    }

    // ---- sampling -------------------------------------------------------------------------------------------------------

    /** Every row of the index in the order of repository and path. The sample reads them all: a cost of the offset-free design (gap G2). */
    private fun sampledRows(): List<SampledRow> = fetch(null, SAMPLE_FIELDS)
        .map { source ->
            SampledRow(
                repository = source.path("repository").asText(),
                path = source.path("path").asText(),
                sizeBytes = source.path("size_bytes").asLong(),
                lastModified = source.path("last_modified").asLong(),
                origin = Origin.valueOf(source.path("origin").asText()),
                sourceHost = source.textOrNull("source_host"),
            )
        }
        .sortedWith(compareBy({ it.repository }, { it.path }))

    /** The documents of [scope] (every document when null) with [fields], up to the total limit of the index. */
    private fun fetch(scope: String?, fields: List<String>): List<JsonNode> {
        val body = linkedMapOf<String, Any>("fields" to fields, "limit" to MAX_ROWS)
        scope?.let { body["filter"] = it }
        return client.json("POST", "/indexes/$index/documents/fetch", body).path("results").toList()
    }

    private fun cursorOf(repository: String, path: String): String = "$repository\n$path"

    private fun decodeCursor(cursor: String): Pair<String, String> {
        val separator = cursor.indexOf('\n')
        require(separator >= 0) { "the cursor is not a sample cursor of this backend" }
        return cursor.substring(0, separator) to cursor.substring(separator + 1)
    }

    private fun JsonNode.textOrNull(field: String): String? = path(field).takeUnless { it.isNull || it.isMissingNode }?.asText()

    private companion object {
        const val SUPPORTED_MINOR = "1.15"
        const val PAUSED = "the search server is not reachable, requests are paused"
        const val SETTINGS_RUNNING = "settings task running"
        const val LOOKUP_CHUNK = 500
        const val MAX_ROWS = 100_000
        val SAMPLE_FIELDS = listOf("repository", "path", "size_bytes", "last_modified", "origin", "source_host")
    }

}
