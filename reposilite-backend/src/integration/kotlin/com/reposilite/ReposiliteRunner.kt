/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite

import com.reposilite.storage.s3.S3PresignConfig
import com.reposilite.audit.decision.DecisionLogSettings
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.local.infrastructure.LOCAL_CONFIGURATION_FILE
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.journalist.Channel
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.storage.StorageProviderSettings
import com.reposilite.token.AccessTokenSecurityProvider
import net.dzikoysk.cdn.KCdnFactory
import net.onelitefeather.ingot.logging.AccessLogSetting
import net.onelitefeather.ingot.readonly.ReadOnlySetting
import net.onelitefeather.ingot.support.SupportBundleSetting
import net.onelitefeather.ingot.web.ShutdownSettings
import net.dzikoysk.cdn.source.Source
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import panda.std.ResultAssertions.assertOk
import panda.std.reactive.ReferenceUtils
import java.io.File
import java.time.Duration

/**
 * This is a dirty launcher of Reposilite instance for integration tests.
 * Every integration test is launched twice, with local and remote integrations, through dedicated extensions:
 * - [RecommendedLocalSpecificationJunitExtension]
 * - [RecommendedRemoteSpecificationJunitExtension]
 */
@Suppress("PropertyName")
internal abstract class ReposiliteRunner {

    companion object {
        val DEFAULT_TOKEN = "manager" to "manager-secret"

        /** Stands in for an upstream that no test has started yet. Tests replace it through [useUpstreamPort]. */
        const val UNSTARTED_UPSTREAM_PORT = 0

        fun upstreamReference(port: Int): String =
            "http://localhost:$port/releases"
    }

    @TempDir
    lateinit var reposiliteWorkingDirectory: File
    @JvmField
    var _extensionInitialized = false
    @JvmField
    var _database: String = ""
    @JvmField
    var _storageProvider: StorageProviderSettings? = null

    lateinit var reposilite: Reposilite

    @BeforeEach
    fun bootApplication() {
        if (!_extensionInitialized) {
            throw IllegalStateException("Missing Reposilite extension on integration test")
        }

        val logger = PrintStreamLogger(System.out, System.err, Channel.ALL, false)
        // Port 0: the OS picks a free port and the server reports it back, so nothing has to be retried.
        assertOk(prepareInstance(logger))
    }

    /** The drain settings of the instance. The default is the strict stop of earlier versions. */
    protected open fun shutdownSettings(): ShutdownSettings =
        ShutdownSettings()

    /** The concurrent request limit of the instance. The default is no limit, as in earlier versions. */
    protected open fun maxConcurrentRequests(): Int? =
        null

    /** The read-only switch of the instance. Tests default to off, so no test depends on the environment of the machine. */
    protected open fun readOnlySetting(): ReadOnlySetting =
        ReadOnlySetting(forced = false)

    /** The support bundle switch of the instance. Tests default to on, so no test depends on the environment of the machine. */
    protected open fun supportBundleSetting(): SupportBundleSetting =
        SupportBundleSetting(enabled = true)

    /** The decision log switch of the instance. Tests default to off, as the server does without `INGOT_DECISION_LOG`. */
    protected open fun decisionLogSettings(): DecisionLogSettings =
        DecisionLogSettings()

    /** The access record switch of the instance. Tests default to off, as the server does without `INGOT_ACCESS_LOG`. */
    protected open fun accessLogSetting(): AccessLogSetting =
        AccessLogSetting(enabled = false)

    /** The S3 presigned redirect switch of the instance. Tests default to off, so no test depends on the environment of the machine. */
    protected open fun presignConfig(): S3PresignConfig =
        S3PresignConfig(enabled = false, ttl = Duration.ofSeconds(300), endpoint = null)

    /** Waits out the pre-stop delay. Tests replace it with a recorder, so no test waits on the wall clock. */
    protected open fun awaitDelay(duration: Duration) {
        Thread.sleep(duration.toMillis())
    }

    /** Runs once the instance is built and before it launches, for listeners and routes the test adds. */
    protected open fun beforeLaunch(reposilite: Reposilite) { }

    /**
     * Whether the shared database gets the object storage of this test before the instance starts. The start check of a worker
     * reads the repositories while the instance is created, before a test can change them, so the storage is written first.
     * The default is off, so every other test starts exactly the instance it asks for.
     */
    protected open fun seedsSharedStorage(): Boolean = false

    /**
     * Writes the storage of this test into the shared configuration of the database, through a server of the default target
     * on the same database, the way the operator's configuration reaches the database. That server is stopped before the
     * instance under test is created.
     */
    private fun seedSharedStorage(logger: Logger) {
        val parameters = ReposiliteParameters()
        parameters.tokenEntries = arrayOf("${DEFAULT_TOKEN.first}:${DEFAULT_TOKEN.second}")
        parameters.workingDirectoryName = reposiliteWorkingDirectory.absolutePath
        parameters.testEnv = true
        parameters.port = 0
        parameters.database = _database
        parameters.tokenHashStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH
        parameters.run()

        val storage = requireNotNull(_storageProvider) { "Missing storage provider for the seeded shared configuration" }
        val seeding = ReposiliteFactory.createReposilite(parameters, logger)
        try {
            assertOk(seeding.launch())
            val configuration = seeding.extensions.facade<SharedConfigurationFacade>()
            val current = configuration.getDomainSettings<MavenSettings>().get()
            val stored = configuration.updateSharedSettings(
                "maven",
                current.copy(repositories = current.repositories.map { it.copy(storageProvider = storage) }),
            )
            check(stored?.isOk == true) { "The seeded storage was not stored in the shared configuration: ${stored?.error}" }
        } finally {
            seeding.shutdown()
        }
    }

    private fun prepareInstance(logger: Logger): Result<Reposilite, Exception> {
        val parameters = ReposiliteParameters()
        parameters.tokenEntries = arrayOf("${DEFAULT_TOKEN.first}:${DEFAULT_TOKEN.second}")
        parameters.workingDirectoryName = reposiliteWorkingDirectory.absolutePath
        parameters.testEnv = true
        parameters.port = 0
        parameters.database = _database
        parameters.tokenHashStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH
        parameters.run()
        overrideParameters(parameters)

        val localConfiguration = LocalConfiguration().also {
            ReferenceUtils.setValue(it.webThreadPool, 6)
            ReferenceUtils.setValue(it.ioThreadPool, 4)
        }

        overrideLocalConfiguration(localConfiguration)
        KCdnFactory.createStandard().render(localConfiguration, Source.of(reposiliteWorkingDirectory.resolve(LOCAL_CONFIGURATION_FILE)))

        if (seedsSharedStorage()) {
            seedSharedStorage(logger)
        }

        reposilite = ReposiliteFactory.createReposilite(
            parameters,
            logger,
            shutdownSettings = shutdownSettings(),
            maxConcurrentRequests = maxConcurrentRequests(),
            readOnlySetting = readOnlySetting(),
            supportBundleSetting = supportBundleSetting(),
            accessLogSetting = accessLogSetting(),
            presignConfig = presignConfig(),
            decisionLogSettings = decisionLogSettings(),
            awaitDelay = ::awaitDelay,
        )
        reposilite.journalist.setVisibleThreshold(Channel.WARN)

        val sharedConfigurationFacade = reposilite.extensions.facade<SharedConfigurationFacade>()

        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { old ->
            val proxiedConfiguration = RepositorySettings(
                id = "proxied",
                proxied = listOf(
                    MirroredRepositorySettings(
                        reference = upstreamReference(UNSTARTED_UPSTREAM_PORT)
                    )
                )
            )
            val proxiedStoredConfiguration = RepositorySettings(
                id = "proxied-stored",
                proxied = listOf(
                    MirroredRepositorySettings(
                        reference = upstreamReference(UNSTARTED_UPSTREAM_PORT),
                        store = true,
                    )
                )
            )

            return@update old.copy(
                repositories = old.repositories.associateBy { it.id }.toMutableMap()
                    .also { repositories ->
                        repositories["proxied"] = proxiedConfiguration
                        repositories["proxied-stored"] = proxiedStoredConfiguration
                    }
                    .mapValues { (_, repositoryConfiguration) ->
                        repositoryConfiguration.copy(
                            redeployment = true,
                            storageProvider = _storageProvider!!,
                        )
                    }
                    .toMutableMap()
                    .also { it["immutable"] = RepositorySettings(id = "immutable", redeployment = false) }
                    .values
                    .toList()
            )
        }

        overrideSharedConfiguration(sharedConfigurationFacade)
        beforeLaunch(reposilite)
        return reposilite.launch()
    }

    protected open fun overrideParameters(parameters: ReposiliteParameters) { }

    protected open fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) { }

    protected open fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) { }

    @AfterEach
    fun shutdownApplication() {
        reposilite.shutdown()
    }

}
