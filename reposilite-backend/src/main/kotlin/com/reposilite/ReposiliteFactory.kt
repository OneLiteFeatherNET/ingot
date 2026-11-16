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

import com.reposilite.audit.decision.DecisionLogSettings
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.local.LocalConfigurationFactory
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.journalist.Channel
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.maven.application.MavenSettings
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.PluginLoader
import com.reposilite.shared.extensions.newFixedThreadPool
import com.reposilite.shared.extensions.newSingleThreadScheduledExecutor
import com.reposilite.web.HttpServer
import net.onelitefeather.ingot.cluster.ClusterSettings
import net.onelitefeather.ingot.cluster.ClusterSources
import net.onelitefeather.ingot.deploy.BackgroundJobsSetting
import net.onelitefeather.ingot.deploy.ClusterRequirements
import net.onelitefeather.ingot.deploy.DeployTarget
import net.onelitefeather.ingot.deploy.DeployTargetResolver
import net.onelitefeather.ingot.deploy.SharedStateRefusalException
import net.onelitefeather.ingot.deploy.logWarnings
import net.onelitefeather.ingot.plugin.store.PluginStoreFactory
import net.onelitefeather.ingot.readonly.ReadOnlySetting
import net.onelitefeather.ingot.support.SupportBundleSetting
import net.onelitefeather.ingot.schema.SchemaLock
import net.onelitefeather.ingot.schema.SchemaLockFactory
import net.onelitefeather.ingot.schema.SchemaLockRequest
import java.time.Clock
import net.onelitefeather.ingot.logging.AccessLogSetting
import net.onelitefeather.ingot.logging.JsonLogger
import net.onelitefeather.ingot.logging.LogFormat
import net.onelitefeather.ingot.logging.LogSettings
import net.onelitefeather.ingot.logging.OpenTelemetryTraceContext
import net.onelitefeather.ingot.logging.applyToTinyLog
import net.onelitefeather.ingot.web.RequestLimitSettings
import net.onelitefeather.ingot.web.ShutdownSettings
import com.reposilite.storage.s3.S3PresignConfig
import java.time.Duration
import kotlin.io.path.absolutePathString

object ReposiliteFactory {

    fun createReposilite(parameters: ReposiliteParameters): Reposilite {
        // Read first, because tinylog reads its configuration once, when the first logger uses it
        val logSettings = LogSettings.read()
        logSettings.applyToTinyLog()
        val console = when (logSettings.format) {
            LogFormat.TEXT -> PrintStreamLogger(System.out, System.err, Channel.ALL, false)
            LogFormat.JSON -> JsonLogger(System.out, System.err, Channel.ALL, Clock.systemUTC(), OpenTelemetryTraceContext)
        }
        return createReposilite(
            parameters,
            console,
            logSettings = logSettings,
            accessLogSetting = AccessLogSetting.read(),
            decisionLogSettings = DecisionLogSettings.read(),
        )
    }

    fun createReposilite(parameters: ReposiliteParameters, rootJournalist: Journalist): Reposilite =
        createReposilite(parameters, rootJournalist, SchemaLockFactory.DEFAULT)

    fun createReposilite(
        parameters: ReposiliteParameters,
        rootJournalist: Journalist,
        schemaLockFactory: SchemaLockFactory = SchemaLockFactory.DEFAULT,
        // Read before anything binds a port, so an invalid value stops the start here
        shutdownSettings: ShutdownSettings = ShutdownSettings.read(),
        // Read before anything binds a port, so an invalid value stops the start here
        maxConcurrentRequests: Int? = RequestLimitSettings.read(),
        // Read before anything binds a port, like the shutdown settings
        readOnlySetting: ReadOnlySetting = ReadOnlySetting.read(),
        // Read before anything binds a port, like the read-only switch
        supportBundleSetting: SupportBundleSetting = SupportBundleSetting.read(),
        // The defaults of the logging variables, so a test does not depend on the environment of its machine
        logSettings: LogSettings = LogSettings(),
        // The access record is off unless the caller switches it on, like the other opt-in handlers
        accessLogSetting: AccessLogSetting = AccessLogSetting(enabled = false),
        // Read before anything binds a port, like the read-only switch
        presignConfig: S3PresignConfig = S3PresignConfig.read(),
        // The decision log is off unless the caller switches it on, so a start without the setting writes no record
        decisionLogSettings: DecisionLogSettings = DecisionLogSettings(),
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
        // Resolved next to the target, so an invalid switch or false on all and worker stops the start before a port binds.
        parameters.backgroundJobsEnabled = BackgroundJobsSetting.resolve(
            target = deployTargetResolution.target,
            commandLine = parameters.backgroundJobs,
            properties = System.getProperty(BackgroundJobsSetting.PROPERTY)
                ?.let { mapOf(BackgroundJobsSetting.PROPERTY to it) }
                ?: emptyMap(),
            environment = System.getenv(),
        )

        val journalist = ReposiliteJournalist(
            visibleJournalist = rootJournalist,
            cachedLogSize = localConfiguration.cachedLogSize.get(),
            defaultVisibilityThreshold = logSettings.consoleThreshold(parameters.level),
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
                databaseThreadPoolSize = localConfiguration.databaseThreadPool.get(),
                poolTimings = DatabaseConnectionFactory.hikariTimings(
                    maxLifetimeSeconds = localConfiguration.databaseMaxLifetime.get(),
                    connectionTimeoutSeconds = localConfiguration.databaseConnectionTimeout.get(),
                    keepaliveSeconds = localConfiguration.databaseKeepalive.get(),
                )
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
            maxConcurrentRequests = maxConcurrentRequests,
            readOnlySetting = readOnlySetting,
            supportBundleSetting = supportBundleSetting,
            accessLogSetting = accessLogSetting,
            presignConfig = presignConfig,
            decisionLogSettings = decisionLogSettings,
            awaitDelay = awaitDelay,
        )

        // Taken before any plugin creates a table. It is held until the post-initialise event in Reposilite.launch.
        reposilite.schemaLock = acquireSchemaLock(reposilite, localConfiguration, schemaLockFactory)

        journalist.logger.info("")
        journalist.logger.info("--- Loading plugins:")

        try {
            val pluginStore = PluginStoreFactory.create(
                environment = System.getenv(),
                localSelection = localConfiguration.plugins.get(),
                dataDirectory = parameters.workingDirectory,
                journalist = journalist,
            )
            val pluginLoader = PluginLoader(parameters.pluginDirectory, reposilite.extensions, pluginStore = pluginStore)
            pluginLoader.extensions.registerFacade(reposilite)
            pluginLoader.loadPluginsByServiceFiles()
            pluginLoader.initialize()
        } catch (throwable: Throwable) {
            reposilite.schemaLock.release()
            throw throwable
        }

        // Checked once the shared configuration is loaded, so the repositories are known, and before anything binds a port.
        checkSharedState(reposilite, parameters, deployTargetResolution.target)

        return reposilite
    }

    private fun checkSharedState(reposilite: Reposilite, parameters: ReposiliteParameters, target: DeployTarget) {
        if (!ClusterRequirements.applies(target, parameters.backgroundJobsEnabled)) return

        val repositories = reposilite.extensions.facade<SharedConfigurationFacade>()
            .getDomainSettings<MavenSettings>()
            .get()
            .repositories
        val repositoryStorageTypes = repositories.associate { it.id to it.storageProvider.type }
        val groupRepositories = ClusterRequirements.declaredGroupRepositories(repositories)

        val refusal = ClusterRequirements.refusalFor(
            target = target,
            backgroundJobsEnabled = parameters.backgroundJobsEnabled,
            database = parameters.database,
            clusterBusConfigured = ClusterSettings.read(ClusterSources.system()).url != null,
            repositoryStorageTypes = repositoryStorageTypes,
            groupRepositories = groupRepositories,
            allowSharedFilesystem = System.getenv(ClusterRequirements.ALLOW_SHARED_FILESYSTEM_VARIABLE)?.trim() == "true",
        ) ?: return

        // The start stops here, so the instance is shut down like a failed schema lock: the connection closes and no port is bound.
        reposilite.shutdown()
        throw SharedStateRefusalException(refusal)
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
