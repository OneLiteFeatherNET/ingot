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

package net.onelitefeather.ingot.search.meilisearch

import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.SearchUnavailableException
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.BackendHealth
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.http.JdkHttpExchange
import net.onelitefeather.ingot.search.opensearch.newIndexName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * The container cases of the Meilisearch backend (design D8, task 5.2) that the shared contract does not cover: a restart during a
 * drain, a task that fails, a task that outlives the bound, a settings change that reindexes, and an index replaced by the operator.
 * Every case writes to an index of its own and deletes it afterwards. The bound of a task wait is one second, so a held task is
 * really outlived.
 */
internal class MeilisearchServerCasesTest {

    @Test
    fun `entries the server did not acknowledge are delivered once after a restart and no document is lost or doubled`() {
        val server = startMeilisearch()
        try {
            val index = newIndexName()
            val batches = (1..BATCHES).map { batch -> (1..BATCH_SIZE).map { row -> upsertOf("com/example/batch-$batch/lib-$row/1.0/lib-$row-1.0.jar") } }
            val before = createMeilisearchBackend(meilisearchTestEnvironment(server.url(), index))
            before.open()
            before.apply(batches[0])
            before.apply(batches[1])

            server.container.stopKeepingData()
            assertThatThrownBy { before.apply(batches[2]) }
                .`as`("the batch sent while the server is down")
                .isInstanceOf(SearchUnavailableException::class.java)

            server.startAgain()
            val after = createMeilisearchBackend(meilisearchTestEnvironment(server.url(), index))
            after.open()
            after.apply(batches[2])
            after.apply(batches[3])

            val paths = batches.flatten().map { it.row.file.path }
            assertThat(after.count())
                .`as`("documents after the restart, with the unacknowledged batch applied once more")
                .isEqualTo(paths.size.toLong())
            assertThat(after.existing(REPOSITORY, paths).keys)
                .`as`("paths stored after the restart")
                .containsExactlyInAnyOrderElementsOf(paths)
            MeilisearchAdminClient(server).deleteIndex(index)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a document write whose task fails fails its batch with the server's code and indexes none of its rows`() {
        val exchange = SteeredExchange(JdkHttpExchange(Duration.ofSeconds(30)))
        val index = newIndexName()
        val backend = createMeilisearchBackend(meilisearchTestEnvironment(sharedServer().url(), index), exchange)
        backend.open()
        exchange.dropNextDocumentIds = true

        assertThatThrownBy { backend.apply(listOf(upsertOf("com/example/failing/1.0/failing-1.0.jar"))) }
            .`as`("the batch whose task the server fails")
            .hasMessageContaining("missing_document_id")
        assertThat(backend.count()).`as`("documents after the failed task").isZero()

        backend.apply(listOf(upsertOf("com/example/ok/1.0/ok-1.0.jar")))
        assertThat(backend.count()).`as`("documents after a good batch following the failure").isEqualTo(1L)
        cleanUp(index, backend)
    }

    @Test
    fun `a task that outlives the bound releases its batch, and the replay leaves the index as the database states it`() {
        val exchange = SteeredExchange(JdkHttpExchange(Duration.ofSeconds(30)))
        val index = newIndexName()
        val backend = createMeilisearchBackend(
            meilisearchTestEnvironment(sharedServer().url(), index) + ("INGOT_MEILISEARCH_TASK_WAIT_SECONDS" to "1"),
            exchange,
        )
        backend.open()
        val rows = (1..5).map { upsertOf("com/example/held/1.0/held-1.0.$it.jar") }
        exchange.holdTasks = true

        assertThatThrownBy { backend.apply(rows) }
            .`as`("the batch whose task is still processing after the bound")
            .isInstanceOf(BackendBusyException::class.java)

        // The replay runs in a backend with the default bound: the shared server also serves the other cases of this class.
        exchange.holdTasks = false
        val replay = createMeilisearchBackend(meilisearchTestEnvironment(sharedServer().url(), index))
        replay.open()
        replay.apply(rows)
        replay.apply(rows)
        val paths = rows.map { it.row.file.path }
        assertThat(replay.existing(REPOSITORY, paths).keys)
            .`as`("paths stored after the replay")
            .containsExactlyInAnyOrderElementsOf(paths)
        assertThat(replay.count()).`as`("documents after the replay, applied twice").isEqualTo(5L)
        cleanUp(index, replay)
    }

    @Test
    fun `a settings change that reindexes refuses search and reports down until its task has finished`() {
        val index = newIndexName()
        val server = sharedServer()
        createMeilisearchBackend(meilisearchTestEnvironment(server.url(), index)).open()
        // The stored settings now differ from the plugin's, so the next open applies them.
        MeilisearchAdminClient(server).putSettings(index, mapOf("filterableAttributes" to listOf("id")))
        val exchange = SteeredExchange(JdkHttpExchange(Duration.ofSeconds(30)))
        val backend = createMeilisearchBackend(
            meilisearchTestEnvironment(server.url(), index) + ("INGOT_MEILISEARCH_TASK_WAIT_SECONDS" to "1"),
            exchange,
        )
        exchange.holdTasks = true

        backend.open()

        assertThatThrownBy { backend.search(net.onelitefeather.ingot.search.ArtifactSearchQuery(), net.onelitefeather.ingot.search.access.SearchAccess.NONE) }
            .`as`("search while the settings task runs")
            .isInstanceOf(SearchUnavailableException::class.java)
            .hasMessageContaining("reindexed")
        assertThat(backend.health()).`as`("health while the settings task runs").isEqualTo(BackendHealth.Down("settings task running"))

        exchange.holdTasks = false
        assertThat(backend.health()).`as`("health once the settings task has finished").isEqualTo(BackendHealth.Up)
        cleanUp(index, backend)
    }

    @Test
    fun `an index replaced by the operator changes the identity of the backend`() {
        val index = newIndexName()
        val server = sharedServer()
        val first = createMeilisearchBackend(meilisearchTestEnvironment(server.url(), index))
        first.open()
        val firstIdentity = first.indexIdentity()
        MeilisearchAdminClient(server).deleteIndex(index)

        val second = createMeilisearchBackend(meilisearchTestEnvironment(server.url(), index))
        second.open()

        assertThat(firstIdentity).`as`("identity of the index before the replacement").isNotNull()
        assertThat(second.indexIdentity())
            .`as`("identity of the index the operator recreated")
            .isNotEqualTo(firstIdentity)
        cleanUp(index, second)
    }

    private fun cleanUp(index: String, backend: ArtifactSearchBackend) {
        backend.close()
        MeilisearchAdminClient(sharedServer()).deleteIndex(index)
    }

    private fun upsertOf(path: String): IndexAction.Upsert {
        val parsed = ArtifactPathParser.parse(REPOSITORY, path) as ArtifactPathResult.Row
        return IndexAction.Upsert(
            IndexRow(
                file = parsed.file,
                provenance = Provenance.HOSTED,
                sizeBytes = 10L,
                lastModified = Instant.parse(NOW).toEpochMilli(),
                sha1 = null,
                sha256 = null,
                indexedAt = Instant.parse(NOW).toEpochMilli(),
            ),
        )
    }

    private companion object {
        const val NOW = "2026-10-17T10:00:00Z"
        const val REPOSITORY = "releases"
        const val BATCHES = 4
        const val BATCH_SIZE = 10

        /** One server for the cases that do not restart it, started once for the class. */
        val shared: StartedMeilisearch by lazy { startMeilisearch() }

        fun sharedServer(): StartedMeilisearch = shared
    }

}
