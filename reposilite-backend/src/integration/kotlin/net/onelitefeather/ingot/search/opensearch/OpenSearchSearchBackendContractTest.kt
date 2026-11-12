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

package net.onelitefeather.ingot.search.opensearch

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.opensearch.openSearchBackendFactory
import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendContract
import net.onelitefeather.ingot.search.backend.CoreSearchBackendContext
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.net.ssl.SSLContext

/**
 * The shared search contract on the OpenSearch backend (search backend seam, D10), against a secured OpenSearch 2.x container. The
 * container is started once for the class, and every test writes to an index of its own, so no test sees another test's rows.
 * The test authority is trusted through the context of the test; production code keeps the JVM default.
 */
internal class OpenSearchSearchBackendContractTest : ArtifactSearchBackendContract() {

    override fun open(directory: Path, clock: Clock): Opened {
        val index = newIndexName()
        val backend = createBackend(openSearchTestEnvironment(cluster.url(), index), clock, sslContext)
        backend.open()
        return Opened(VisibleAfterWrites(backend) { admin.refresh(index) }) {
            backend.close()
            admin.deleteIndex("$index-v1")
        }
    }

    /**
     * A backend whose writes are visible to the next read: the fixture calls `_refresh` after each write. Production code never
     * refreshes; the one-second refresh interval makes the writes visible in the running server.
     */
    private class VisibleAfterWrites(
        private val delegate: ArtifactSearchBackend,
        private val refresh: () -> Unit,
    ) : ArtifactSearchBackend by delegate {

        override fun apply(actions: List<IndexAction>) {
            delegate.apply(actions)
            refresh()
        }

        override fun touch(repository: String, paths: Collection<String>, indexedAt: Long): Int =
            delegate.touch(repository, paths, indexedAt).also { refresh() }

        override fun deleteStale(repository: String, before: Long, limit: Int): Int =
            delegate.deleteStale(repository, before, limit).also { refresh() }

        override fun deleteRepository(repository: String, limit: Int): Int =
            delegate.deleteRepository(repository, limit).also { refresh() }

    }

    private companion object {

        val cluster: StartedOpenSearch by lazy { startSecuredOpenSearch() }
        val sslContext: SSLContext by lazy { cluster.sslContext() }
        val admin: OpenSearchAdminClient by lazy { OpenSearchAdminClient(cluster, sslContext) }

    }

}

/** A fresh alias name for one test: lower case, within the 64 characters the plugin accepts. */
internal fun newIndexName(): String = "ingot-" + UUID.randomUUID().toString().replace("-", "")

/**
 * A backend created by the factory of the plugin, the way the core creates one, and not yet opened. [sslContext] is the trust of
 * the test cluster; the plugin itself reads the JVM default.
 */
internal fun createBackend(environment: Map<String, String>, clock: Clock, sslContext: SSLContext): ArtifactSearchBackend {
    val context = CoreSearchBackendContext(
        clock = clock,
        instanceId = "test-replica",
        backendId = "opensearch",
        variables = environment::get,
    ) { _, _ -> }
    return openSearchBackendFactory(environment, sslContext).create(context)
}

/**
 * The restart case of the design (D8): the cluster stops in the middle of a drain, and the batch the replica got no answer for is
 * delivered again once the cluster is back. The index keeps its data across the restart, as a stopped and started container does,
 * so the count of documents must equal the count of rows.
 */
internal class OpenSearchRestartIntegrationTest {

    @Test
    fun `entries the cluster did not acknowledge are delivered once after a restart and no document is lost or doubled`() {
        val cluster = startSecuredOpenSearch()
        try {
            val sslContext = cluster.sslContext()
            val admin = OpenSearchAdminClient(cluster, sslContext)
            val index = newIndexName()
            val clock = MutableClock(Instant.parse(NOW))
            val batches = (1..BATCHES).map { batch ->
                (1..BATCH_SIZE).map { row -> upsertOf("com/example/batch-$batch/lib-$row/1.0/lib-$row-1.0.jar") }
            }

            val before = createBackend(openSearchTestEnvironment(cluster.url(), index, BATCH_SIZE), clock, sslContext)
            before.open()
            before.apply(batches[0])
            before.apply(batches[1])

            cluster.container.stopKeepingData()
            assertThatThrownBy { before.apply(batches[2]) }
                .`as`("the batch sent while the cluster is down")
                .isInstanceOf(IOException::class.java)

            cluster.container.startAgain(cluster.readiness)
            val after = createBackend(openSearchTestEnvironment(cluster.url(), index, BATCH_SIZE), clock, sslContext)
            after.open()
            after.apply(batches[2])
            after.apply(batches[3])
            admin.refresh(index)

            val paths = batches.flatten().map { (it.row.file.path) }
            assertThat(after.count())
                .`as`("documents after the restart, with the unacknowledged batch applied once more")
                .isEqualTo(paths.size.toLong())
            assertThat(after.existing(REPOSITORY, paths).keys)
                .`as`("paths stored after the restart")
                .containsExactlyInAnyOrderElementsOf(paths)

            after.close()
            admin.deleteIndex("$index-v1")
        } finally {
            cluster.container.stop()
        }
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
    }

}
