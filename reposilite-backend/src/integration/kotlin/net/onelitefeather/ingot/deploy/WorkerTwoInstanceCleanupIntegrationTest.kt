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

package net.onelitefeather.ingot.deploy

import com.reposilite.IsolatedResources
import com.reposilite.Reposilite
import com.reposilite.ReposiliteFactory
import com.reposilite.ReposiliteParameters
import com.reposilite.SharedContainers
import com.reposilite.cleanup.CleanupService
import com.reposilite.cleanup.CleanupTrigger
import com.reposilite.cleanup.application.CleanupPolicy
import com.reposilite.cleanup.application.CleanupScope
import com.reposilite.cleanup.application.CleanupSettings
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.local.infrastructure.LOCAL_CONFIGURATION_FILE
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.databaseToken
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.natsUrl
import com.reposilite.s3Endpoint
import com.reposilite.storage.s3.S3StorageProviderSettings
import com.reposilite.token.AccessTokenSecurityProvider
import net.dzikoysk.cdn.KCdnFactory
import net.dzikoysk.cdn.source.Source
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.ResultAssertions.assertOk
import panda.std.reactive.ReferenceUtils
import java.io.File
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * A worker and a server with `INGOT_BACKGROUND_JOBS=false` on one database and one bus. A cleanup policy that is due at the
 * injected clock runs on the worker only, and the server never takes the day claim of the policy.
 *
 * Both instances run [ClusterRequirements], so each one needs the shared state it asks for: a MariaDB database, a bus URL
 * and object storage for every repository. The bus URL is read from the system properties, which are shared by the whole
 * JVM, so the instances are created one after the other under the same lock as the cluster test. The shared configuration
 * is stored in the database and the check runs before any port is bound, so a short bootstrap instance writes the
 * repository and the policy first.
 *
 * The schedule is driven by the injected clock and [CleanupService.runScheduledTick]. Nothing waits on the wall clock.
 * Every test makes a database and a bucket of its own.
 */
internal class WorkerTwoInstanceCleanupIntegrationTest {

    @TempDir
    lateinit var workingDirectories: File

    private val instances = mutableListOf<Node>()
    private val cleanups = mutableListOf<() -> Unit>()

    @AfterEach
    fun shutdown() {
        instances.forEach { runCatching { it.reposilite.shutdown() } }
        cleanups.forEach { runCatching { it() } }
    }

    @Test
    fun `a due cleanup policy is run once and only by the worker`() {
        // given: a worker and a server with the background jobs off, on one database, and no run recorded yet
        val fixture = startPair()
        assertThat(cleanup(fixture.server).runs(POLICY_ID))
            .describedAs("runs of $POLICY_ID before the worker tick")
            .isEmpty()

        // when: the worker runs its schedule tick at the due minute
        cleanup(fixture.worker).runScheduledTick()

        // then: the run is recorded once, as a scheduled run, and the server sees that same single record
        val runs = cleanup(fixture.worker).runs(POLICY_ID)
        assertThat(runs)
            .describedAs("runs of $POLICY_ID recorded by the worker tick")
            .hasSize(1)
        assertThat(runs.single().trigger)
            .describedAs("trigger of the run recorded by the worker tick")
            .isEqualTo(CleanupTrigger.SCHEDULED)
        assertThat(cleanup(fixture.server).runs(POLICY_ID))
            .describedAs("runs of $POLICY_ID seen by the server")
            .hasSize(1)
    }

    @Test
    fun `the server tick takes no day claim and the day claim is held once`() {
        // given: a worker that has run the due policy for the day
        val fixture = startPair()
        cleanup(fixture.worker).runScheduledTick()
        assertThat(claimRows(DAY_KEY))
            .describedAs("claim rows of $DAY_KEY after the worker tick")
            .isEqualTo(1)

        // when: the server runs its schedule tick on the same day
        cleanup(fixture.server).runScheduledTick()

        // then: the server submits nothing, so no run and no second claim row appear
        assertThat(cleanup(fixture.worker).runs(POLICY_ID))
            .describedAs("runs of $POLICY_ID after the server tick")
            .hasSize(1)
        assertThat(claimRows(DAY_KEY))
            .describedAs("claim rows of $DAY_KEY after the server tick")
            .isEqualTo(1)
        assertThat(claimCompleted(DAY_KEY))
            .describedAs("the day claim of $DAY_KEY is completed by the worker run")
            .isTrue()
    }

    @Test
    fun `with the switch off the cleanup schedule is never submitted and the worker submits it`() {
        // given: a worker and a server with the background jobs switched off, started on one database
        val fixture = startPair()

        // then: the worker submitted the cleanup schedule, and the server with the switch off submitted none
        assertThat(fixture.worker.reposilite.submittedSingletonJobs)
            .describedAs("singleton jobs submitted by the worker")
            .contains(SingletonJob.CLEANUP_SCHEDULE)
        assertThat(fixture.server.reposilite.submittedSingletonJobs)
            .describedAs("singleton jobs submitted by the server with INGOT_BACKGROUND_JOBS=false")
            .doesNotContain(SingletonJob.CLEANUP_SCHEDULE)
    }

    // ---- fixture

    private class Node(val instanceId: String, val reposilite: Reposilite)

    private class Fixture(val worker: Node, val server: Node)

    private val database: String by lazy {
        IsolatedResources.createMariaDbDatabase(SharedContainers.mariaDb).also { name ->
            cleanups += { IsolatedResources.dropMariaDbDatabase(SharedContainers.mariaDb, name) }
        }
    }

    private val bucket = IsolatedResources.newBucketName("worker-cleanup")

    /**
     * Writes the shared repository and the policy from a bootstrap instance, then starts the worker and the server on the
     * same database. The bootstrap instance is stopped before the pair starts.
     */
    private fun startPair(): Fixture {
        bootstrapSharedConfiguration()
        val worker = start("worker-a", target = "worker", backgroundJobs = null, busUrl = SharedContainers.nats.natsUrl())
        val server = start("server-b", target = "server", backgroundJobs = "false", busUrl = SharedContainers.nats.natsUrl())
        return Fixture(worker, server)
    }

    /**
     * Stores the S3 repository and the due policy in the shared configuration of the database. The check of the worker
     * and of the server reads the repositories before they launch, so they have to be stored first.
     */
    private fun bootstrapSharedConfiguration() {
        val node = start("bootstrap", target = null, backgroundJobs = null, busUrl = null)
        // updateSharedSettings stores the document in the database; getDomainSettings().update only changes memory.
        val configuration = node.reposilite.extensions.facade<SharedConfigurationFacade>()
        val maven = configuration.getDomainSettings<MavenSettings>().get()
        assertThat(configuration.updateSharedSettings("maven", maven.copy(repositories = listOf(s3Repository())))?.isOk)
            .describedAs("the S3 repository is stored in the shared configuration")
            .isTrue()
        val cleanup = configuration.getDomainSettings<CleanupSettings>().get()
        assertThat(configuration.updateSharedSettings("cleanup", cleanup.copy(policies = listOf(duePolicy())))?.isOk)
            .describedAs("the due policy is stored in the shared configuration")
            .isTrue()
        node.reposilite.shutdown()
    }

    private fun start(instanceId: String, target: String?, backgroundJobs: String?, busUrl: String?): Node {
        val directory = File(workingDirectories, instanceId).also { it.mkdirs() }
        val parameters = ReposiliteParameters()
        parameters.tokenEntries = arrayOf("$MANAGER_NAME:$MANAGER_SECRET")
        parameters.workingDirectoryName = directory.absolutePath
        parameters.testEnv = true
        parameters.port = 0
        parameters.database = SharedContainers.mariaDb.databaseToken(database)
        parameters.tokenHashStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH
        parameters.clock = DUE_CLOCK
        parameters.target = target
        parameters.backgroundJobs = backgroundJobs
        parameters.run()

        val localConfiguration = LocalConfiguration().also {
            ReferenceUtils.setValue(it.webThreadPool, 6)
            ReferenceUtils.setValue(it.ioThreadPool, 4)
        }
        KCdnFactory.createStandard().render(localConfiguration, Source.of(directory.resolve(LOCAL_CONFIGURATION_FILE)))

        val logger = PrintStreamLogger(System.out, System.err, Channel.ALL, false)
        val reposilite = synchronized(PROPERTY_LOCK) {
            busUrl?.let { System.setProperty(CLUSTER_URL_PROPERTY, it) }
            System.setProperty(CLUSTER_INSTANCE_PROPERTY, instanceId)
            try {
                ReposiliteFactory.createReposilite(parameters, logger)
            } finally {
                System.clearProperty(CLUSTER_URL_PROPERTY)
                System.clearProperty(CLUSTER_INSTANCE_PROPERTY)
            }
        }
        reposilite.journalist.setVisibleThreshold(Channel.WARN)
        val node = Node(instanceId, reposilite).also { instances += it }
        assertOk(reposilite.launch())
        return node
    }

    private fun cleanup(node: Node): CleanupService =
        node.reposilite.extensions.facade<CleanupService>()

    private fun s3Repository(): RepositorySettings =
        RepositorySettings(
            id = REPOSITORY_ID,
            storageProvider = S3StorageProviderSettings(
                bucketName = bucket,
                endpoint = SharedContainers.floci.s3Endpoint(),
                accessKey = "test",
                secretKey = "test",
                region = "us-east-1",
            ),
        )

    private fun duePolicy(): CleanupPolicy =
        CleanupPolicy(
            id = POLICY_ID,
            enabled = true,
            repositories = listOf(REPOSITORY_ID),
            scope = CleanupScope.RELEASES,
            keepLatest = 1,
            schedule = DUE_SCHEDULE,
        )

    /** The number of claim rows of [key] in the database of the pair. */
    private fun claimRows(key: String): Int =
        withClaims("SELECT COUNT(*) FROM scheduled_job_claim WHERE job_key = ?", key) { result ->
            result.next()
            result.getInt(1)
        }

    /** Whether the claim row of [key] is marked as done, which only a completed run does. */
    private fun claimCompleted(key: String): Boolean =
        withClaims("SELECT done_at FROM scheduled_job_claim WHERE job_key = ?", key) { result ->
            result.next() && result.getObject(1) != null
        }

    private fun <T> withClaims(sql: String, key: String, read: (java.sql.ResultSet) -> T): T {
        val mariaDb = SharedContainers.mariaDb
        val url = "jdbc:mariadb://${mariaDb.host}:${mariaDb.getMappedPort(MARIADB_PORT)}/$database"
        DriverManager.getConnection(url, mariaDb.username, mariaDb.password).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, key)
                statement.executeQuery().use { return read(it) }
            }
        }
    }

    private companion object {
        const val MANAGER_NAME = "manager"
        const val MANAGER_SECRET = "manager-secret"
        const val POLICY_ID = "nightly"
        const val REPOSITORY_ID = "releases"
        const val DUE_SCHEDULE = "03:30"
        const val DAY_KEY = "cleanup:nightly:2026-11-15"
        const val CLUSTER_URL_PROPERTY = "ingot.cluster.url"
        const val CLUSTER_INSTANCE_PROPERTY = "ingot.cluster.instance"
        const val MARIADB_PORT = 3306
        val DUE_CLOCK: Clock = Clock.fixed(Instant.parse("2026-11-15T03:30:00Z"), ZoneOffset.UTC)
        val PROPERTY_LOCK = Any()
    }

}
