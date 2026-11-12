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

@file:OptIn(net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.backend

import net.onelitefeather.ingot.search.ArtifactFileItem
import net.onelitefeather.ingot.search.ArtifactFilePage
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchItem
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.FacetBucket
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.IndexState
import net.onelitefeather.ingot.search.IndexStatus
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.RepositoryPresence
import net.onelitefeather.ingot.search.SearchFacets
import net.onelitefeather.ingot.search.access.SearchAccess
import java.time.Instant
import kotlin.random.Random

/**
 * An in-memory [ArtifactSearchBackend] for the core tests and the reference of the contract suite. It keeps the rows of a
 * map and implements the access rule, the removal rules, the paging and the sampling the seam promises. The read side
 * ignores the text and range filters of [ArtifactSearchQuery]: the contract never asks for them.
 *
 * [joinsDatabaseTransaction] is a constructor flag so a test can pick the rebuild branch it exercises. [failApply] makes
 * the next calls of [apply] throw, and [onApply] sees every batch before it is applied.
 */
internal class FakeArtifactSearchBackend(
    override val joinsDatabaseTransaction: Boolean = true,
) : ArtifactSearchBackend {

    override val id: String = "fake"

    private class Stored(val sequence: Long, var row: IndexRow)

    private val rows = linkedMapOf<Pair<String, String>, Stored>()
    private var sequence = 0L

    /** The 1-based number of the call of [apply] that throws, or null for none. */
    var failApplyOnCall: Int? = null

    /** Every batch [apply] received, in order, including the one that failed. */
    val batches = mutableListOf<List<IndexAction>>()

    /** Called with each batch before it is applied. */
    var onApply: (List<IndexAction>) -> Unit = {}

    /** How many times [open] was called. */
    var openCalls = 0
        private set

    /** What [open] throws, or null when it opens. */
    var openFailure: RuntimeException? = null

    override fun open() {
        openCalls++
        openFailure?.let { throw it }
    }

    override fun close() {
        closeCalls++
    }

    /** How many times [close] was called. */
    var closeCalls = 0
        private set

    /** What [health] answers. */
    var healthAnswer: BackendHealth = BackendHealth.Up

    /** How many times [health] was asked. */
    var healthChecks = 0
        private set

    override fun health(): BackendHealth {
        healthChecks++
        return healthAnswer
    }

    override fun apply(actions: List<IndexAction>) {
        batches += actions
        onApply(actions)
        if (batches.size == failApplyOnCall) throw IllegalStateException("simulated write failure")
        actions.forEach(::applyOne)
    }

    private fun applyOne(action: IndexAction) {
        when (action) {
            is IndexAction.Upsert -> {
                val key = action.row.file.repository to action.row.file.path
                val existing = rows[key]
                if (existing == null) {
                    rows[key] = Stored(++sequence, action.row)
                } else {
                    existing.row = action.row
                }
            }
            is IndexAction.RemovePath -> rows.remove(action.repository to action.path)
            is IndexAction.RemovePrefix -> rows.entries.removeIf { (key, stored) ->
                key.first == action.repository && isAt(key.second, action.prefix) && stored.row.indexedAt <= action.notAfter.toEpochMilli()
            }
            is IndexAction.Reorigin -> rows.entries.forEach { (key, stored) ->
                if (key.first == action.repository && isBelow(key.second, action.directory)) {
                    stored.row = stored.row.copy(provenance = action.provenance)
                }
            }
        }
    }

    override fun pathsAt(repository: String, prefix: String, after: String?, limit: Int): List<String> =
        rows.keys
            .filter { it.first == repository && isAt(it.second, prefix) && (after == null || it.second > after) }
            .map { it.second }
            .sorted()
            .take(limit)

    override fun existing(repository: String, paths: Collection<String>): Map<String, StoredRow> =
        paths.mapNotNull { path ->
            rows[repository to path]?.row?.let { path to StoredRow(it.sizeBytes, it.lastModified, it.sha1, it.sha256) }
        }.toMap()

    override fun touch(repository: String, paths: Collection<String>, indexedAt: Long): Int {
        var touched = 0
        paths.forEach { path ->
            rows[repository to path]?.let { stored ->
                stored.row = stored.row.copy(indexedAt = indexedAt)
                touched++
            }
        }
        return touched
    }

    override fun deleteStale(repository: String, before: Long, limit: Int): Int =
        removeWhere(limit) { key, stored -> key.first == repository && stored.row.indexedAt < before }

    override fun deleteRepository(repository: String, limit: Int): Int =
        removeWhere(limit) { key, _ -> key.first == repository }

    override fun repositories(): List<String> = rows.keys.map { it.first }.distinct()

    override fun count(): Long = rows.size.toLong()

    override fun sampleStart(random: Random): String? {
        if (rows.isEmpty()) return null
        val sequences = rows.values.map { it.sequence }
        return random.nextLong(sequences.min(), sequences.max() + 1).toString()
    }

    override fun sample(cursor: String?, limit: Int): SamplePage {
        val start = cursor?.toLongOrNull() ?: Long.MIN_VALUE
        val ordered = rows.entries.sortedBy { it.value.sequence }
        val upper = ordered.filter { it.value.sequence >= start }.take(limit)
        val found = if (upper.size >= limit) upper else upper + ordered.filter { it.value.sequence < start }.take(limit - upper.size)
        return SamplePage(
            rows = found.map { (key, stored) ->
                SampledRow(
                    repository = key.first,
                    path = key.second,
                    sizeBytes = stored.row.sizeBytes,
                    lastModified = stored.row.lastModified,
                    origin = stored.row.provenance.origin,
                    sourceHost = stored.row.provenance.sourceHost,
                )
            },
            next = found.lastOrNull()?.let { (it.value.sequence + 1).toString() },
        )
    }

    override fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage {
        val visible = visibleRows(access)
        val groups = visible.groupBy { Triple(it.file.groupId, it.file.artifactId, it.file.version) }
            .toSortedMap(compareBy({ it.first }, { it.second }, { it.third }))
        val items = groups.entries.drop(query.page * query.size).take(query.size).map { (key, group) ->
            ArtifactSearchItem(
                groupId = key.first,
                artifactId = key.second,
                version = key.third,
                latestVersion = null,
                snapshot = key.third.endsWith("-SNAPSHOT"),
                repositories = group.map { RepositoryPresence(it.file.repository, it.provenance.origin) }.distinct(),
                fileCount = group.size.toLong(),
                lastModified = Instant.ofEpochMilli(group.maxOf { it.lastModified }),
                browseLink = "/browse/${group.first().file.repository}/${key.first.replace('.', '/')}/${key.second}/${key.third}",
            )
        }
        return ArtifactSearchPage(
            items = items,
            page = query.page,
            size = query.size,
            total = groups.size.toLong(),
            totalCapped = false,
            indexStatus = status(),
        )
    }

    override fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets {
        val visible = visibleRows(access)
        return SearchFacets(
            repository = buckets(visible) { it.file.repository },
            extension = buckets(visible) { it.file.extension },
            classifier = buckets(visible) { it.file.classifier ?: "-" },
            origin = buckets(visible) { it.provenance.origin.name },
            snapshot = buckets(visible) { it.file.snapshot.toString() },
        )
    }

    override fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage {
        val matching = visibleRows(access)
            .filter {
                it.file.groupId.equals(query.groupId, ignoreCase = true) &&
                    it.file.artifactId.equals(query.artifactId, ignoreCase = true) &&
                    it.file.version.equals(query.version, ignoreCase = true)
            }
            .sortedBy { it.file.path }
        val items = matching.drop(query.page * query.size).take(query.size).map { row ->
            ArtifactFileItem(
                repository = row.file.repository,
                path = row.file.path,
                name = row.file.path.substringAfterLast('/'),
                classifier = row.file.classifier,
                extension = row.file.extension,
                size = row.sizeBytes,
                lastModified = Instant.ofEpochMilli(row.lastModified),
                origin = row.provenance.origin,
                sourceHost = row.provenance.sourceHost,
                sha1 = row.sha1,
                browseLink = "/browse/${row.file.repository}/${row.file.path}",
            )
        }
        return ArtifactFilePage(items, query.page, query.size, matching.size.toLong(), status())
    }

    override fun status(): IndexStatus = IndexStatus(
        state = IndexState.READY,
        lagSeconds = 0L,
        lastFullRebuild = null,
        deadLetters = 0L,
        droppedEnqueues = 0L,
    )

    private fun visibleRows(access: SearchAccess): List<IndexRow> =
        rows.entries.filter { access.matches(it.key.first, it.key.second) }.map { it.value.row }

    private fun buckets(rows: List<IndexRow>, value: (IndexRow) -> String): List<FacetBucket> =
        rows.groupingBy(value).eachCount().map { (name, count) -> FacetBucket(name, count.toLong()) }.sortedBy { it.value }

    private fun removeWhere(limit: Int, predicate: (Pair<String, String>, Stored) -> Boolean): Int {
        val victims = rows.entries.filter { predicate(it.key, it.value) }.take(limit).map { it.key }
        victims.forEach(rows::remove)
        return victims.size
    }

    private fun isAt(path: String, prefix: String): Boolean = path == prefix || isBelow(path, prefix)

    private fun isBelow(path: String, directory: String): Boolean = path.startsWith("$directory/")

}
