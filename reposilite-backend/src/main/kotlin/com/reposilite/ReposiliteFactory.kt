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
import com.reposilite.configuration.local.LocalConfigurationFactory
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.Channel
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.PluginLoader
import com.reposilite.shared.extensions.newFixedThreadPool
import com.reposilite.shared.extensions.newSingleThreadScheduledExecutor
import com.reposilite.web.HttpServer
import net.onelitefeather.ingot.deploy.DeployTargetResolver
import net.onelitefeather.ingot.deploy.logWarnings
import net.onelitefeather.ingot.schema.SchemaLock
import net.onelitefeather.ingot.schema.SchemaLockFactory
import net.onelitefeather.ingot.schema.SchemaLockRequest
import java.time.Clock
import net.onelitefeather.ingot.web.ShutdownSettings
import java.time.Duration
import kotlin.io.path.absolutePathString

object ReposiliteFactory {

    fun createReposilite(parameters: ReposiliteParameters): Reposilite =
        createReposilite(parameters, PrintStreamLogger(System.out, System.err, Channel.ALL, false))

    fun createReposilite(parameters: ReposiliteParameters, rootJournalist: Journalist): Reposilite =
        createReposilite(parameters, rootJournalist, SchemaLockFactory.DEFAULT)

    fun createReposilite(
        parameters: ReposiliteParameters,
        rootJournalist: Journalist,
        schemaLockFactory: SchemaLockFactory = SchemaLockFactory.DEFAULT,
        // Read before anything binds a port, so an invalid value stops the start here
        shutdownSettings: ShutdownSettings = ShutdownSettings.read(),
        awaitDelay: (Duration) -> Unit = { duration -> Thread.sleep(duration.toMillis()) },
    ): Reposilite {
        val localConfiguration = LocalConfigurationFactory.createLocalConfiguration(null, parameters)
        parameters.applyLoadedConfiguration(localConfiguration)

        // Resolved before anything binds a port, so an unknown target stops the start here.
        val deployTargetResolution = DeployTargetResolver.resolve(
            commandLine = parameters.target,
            properties = System.getProperty(DeployTargetResolver.PROPERTY)
                ?.let { mapOf(DeployTargetResolver.PROPERTY to it) }
                ?: emptyMap(),
            environment = System.getenv(),
            legacyDefaultFrontend = localConfiguration.defaultFrontend.get(),
        )
        parameters.deployTarget = deployTargetResolution.target

        val journalist = ReposiliteJournalist(
            visibleJournalist = rootJournalist,
            cachedLogSize = localConfiguration.cachedLogSize.get(),
            defaultVisibilityThreshold = Channel.of(parameters.level).orElseGet { Channel.INFO },
            testEnv = parameters.testEnv,
            noColor = parameters.noColor,
        )

        journalist.logger.info("")
        journalist.logger.info("${journalist.effect { MAGENTA_BOLD }}Ingot $VERSION${journalist.effect { RESET }}")
        journalist.logger.info("")
        journalist.logger.info("--- Environment")
        journalist.logger.info("Platform: ${System.getProperty("java.version")} (${System.getProperty("os.name")} :: ${System.getProperty("os.arch")})")
        journalist.logger.info("Running as: ${System.getProperty("user.name")}")
        journalist.logger.info("Working directory: ${parameters.workingDirectory.toAbsolutePath()}")
        journalist.logger.info("Plugin directory: ${parameters.pluginDirectory.toAbsolutePath()}")
        journalist.logger.info("Configuration: ${parameters.localConfigurationPath.absolutePathString()}")
        journalist.logger.info("Pool limits: ${localConfiguration.webThreadPool.get()} WEB threads / ${localConfiguration.ioThreadPool.get()} IO threads / ${localConfiguration.databaseThreadPool.get()} DB connections")
        if (parameters.testEnv) journalist.logger.info("Test environment: Enabled")
        deployTargetResolution.logWarnings(journalist.logger)

        val reposilite = Reposilite(
            journalist = journalist,
            parameters = parameters,
            localConfiguration = localConfiguration,
            databaseConnection = DatabaseConnectionFactory.createConnection(
                workingDirectory = parameters.workingDirectory,
                databaseConfiguration = parameters.database,
                databaseThreadPoolSize = localConfiguration.databaseThreadPool.get()
            ),
            webServer = HttpServer(),
            ioService = newFixedThreadPool(
                min = 0,
                max = localConfiguration.ioThreadPool.get(),
                prefix = "Ingot | IO"
            ),
            scheduler = newSingleThreadScheduledExecutor("Ingot | Scheduler"),
            extensions = Extensions(journalist),
            shutdownSettings = shutdownSettings,
            awaitDelay = awaitDelay,
        )

        // Taken before any plugin creates a table. It is held until the post-initialise event in Reposilite.launch.
        reposilite.schemaLock = acquireSchemaLock(reposilite, localConfiguration, schemaLockFactory)

        journalist.logger.info("")
        journalist.logger.info("--- Loading plugins:")

        try {
            val pluginLoader = PluginLoader(parameters.pluginDirectory, reposilite.extensions)
            pluginLoader.extensions.registerFacade(reposilite)
            pluginLoader.loadPluginsByServiceFiles()
            pluginLoader.initialize()
        } catch (throwable: Throwable) {
            reposilite.schemaLock.release()
            throw throwable
        }

        return reposilite
    }

    private fun acquireSchemaLock(reposilite: Reposilite, localConfiguration: LocalConfiguration, schemaLockFactory: SchemaLockFactory): SchemaLock {
        val source = reposilite.databaseConnection.databaseSource
        return try {
            schemaLockFactory.acquire(
                SchemaLockRequest(
                    database = reposilite.database,
                    jdbcUrl = source.jdbcUrl,
                    username = source.username,
                    password = source.password,
                    timeout = Duration.ofSeconds(localConfiguration.schemaLockTimeout.get()),
                    clock = Clock.systemUTC(),
                    scheduler = reposilite.scheduler,
                )
            )
        } catch (exception: Exception) {
            // The start stops here, so the database connection is closed and the port is never bound.
            reposilite.shutdown()
            throw exception
        }
    }

}
