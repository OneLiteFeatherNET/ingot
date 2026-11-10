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

    /** Waits out the pre-stop delay. Tests replace it with a recorder, so no test waits on the wall clock. */
    protected open fun awaitDelay(duration: Duration) {
        Thread.sleep(duration.toMillis())
    }

    /** Runs once the instance is built and before it launches, for listeners and routes the test adds. */
    protected open fun beforeLaunch(reposilite: Reposilite) { }

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

        reposilite = ReposiliteFactory.createReposilite(
            parameters,
            logger,
            shutdownSettings = shutdownSettings(),
            maxConcurrentRequests = maxConcurrentRequests(),
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
