@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.elasticsearch

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.elasticsearch.elasticsearchBackendFactory
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
import javax.net.ssl.SSLContext

/**
 * The shared search contract on the Elasticsearch backend (search backend seam, D10), against a secured Elasticsearch 8.x container.
 * The cluster is shared by the tests of its image, and every test writes to an index of its own with an API key that holds only
 * the privileges of the design. The test authority is trusted through the context of the test; production code keeps the JVM default.
 */
internal open class ElasticsearchSearchBackendContractTest : ArtifactSearchBackendContract() {

    /** The image of the cluster this suite runs against. The 9.x suite overrides it. */
    protected open val image: String get() = ELASTICSEARCH_8_IMAGE

    private val cluster: StartedElasticsearch get() = ElasticsearchClusters.of(image)

    private val sslContext: SSLContext get() = cluster.sslContext()

    private val admin: ElasticsearchAdminClient get() = ElasticsearchAdminClient(cluster, sslContext)

    override fun open(directory: Path, clock: Clock): Opened {
        val index = newElasticsearchIndexName()
        val apiKey = admin.createLeastPrivilegeApiKey(index)
        val backend = createElasticsearchBackend(elasticsearchTestEnvironment(cluster.url(), index, apiKey), clock, sslContext)
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

}

/** The same contract against a secured Elasticsearch 9.x container (the 9.3 minor), with the same expected values. */
internal class ElasticsearchSearchBackend9ContractTest : ElasticsearchSearchBackendContractTest() {

    override val image: String get() = ELASTICSEARCH_9_IMAGE

}

/**
 * A backend created by the factory of the plugin, the way the core creates one, and not yet opened. [sslContext] is the trust of
 * the test cluster; the plugin itself reads the JVM default.
 */
internal fun createElasticsearchBackend(environment: Map<String, String>, clock: Clock, sslContext: SSLContext): ArtifactSearchBackend {
    val context = CoreSearchBackendContext(
        clock = clock,
        instanceId = "test-replica",
        backendId = "elasticsearch",
        variables = environment::get,
    ) { _, _ -> }
    return elasticsearchBackendFactory(environment, sslContext).create(context)
}

/**
 * The restart case of the design (D8): the cluster stops in the middle of a drain, and the batch the replica got no answer for is
 * delivered again once the cluster is back. The index keeps its data across the restart, as a stopped and started container does,
 * so the count of documents must equal the count of rows.
 */
internal class ElasticsearchRestartIntegrationTest {

    @Test
    fun `entries the cluster did not acknowledge are delivered once after a restart and no document is lost or doubled`() {
        val cluster = startSecuredElasticsearch(ELASTICSEARCH_8_IMAGE)
        try {
            val sslContext = cluster.sslContext()
            val admin = ElasticsearchAdminClient(cluster, sslContext)
            val index = newElasticsearchIndexName()
            val apiKey = admin.createLeastPrivilegeApiKey(index)
            val clock = MutableClock(Instant.parse(NOW))
            val batches = (1..BATCHES).map { batch ->
                (1..BATCH_SIZE).map { row -> upsertOf("com/example/batch-$batch/lib-$row/1.0/lib-$row-1.0.jar") }
            }

            val before = createElasticsearchBackend(elasticsearchTestEnvironment(cluster.url(), index, apiKey, BATCH_SIZE), clock, sslContext)
            before.open()
            before.apply(batches[0])
            before.apply(batches[1])

            cluster.container.stopKeepingData()
            assertThatThrownBy { before.apply(batches[2]) }
                .`as`("the batch sent while the cluster is down")
                .isInstanceOf(IOException::class.java)

            cluster.container.startAgain(cluster.readiness)
            val after = createElasticsearchBackend(elasticsearchTestEnvironment(cluster.url(), index, apiKey, BATCH_SIZE), clock, sslContext)
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
