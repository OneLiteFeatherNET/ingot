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

@file:OptIn(net.onelitefeather.ingot.format.ExperimentalIngotFormatApi::class)

package com.reposilite.maven.application

import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.console.api.CommandsSetupEvent
import com.reposilite.frontend.DashboardPaths
import com.reposilite.frontend.application.FrontendSettings
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.MirrorEnvironment
import com.reposilite.maven.PreservedBuildsListener
import com.reposilite.maven.PrivateAsPublicSetting
import com.reposilite.maven.readInstanceUploadChecksumPolicy
import com.reposilite.maven.metadataLocksFor
import com.reposilite.maven.infrastructure.SqlMirrorCacheLedger
import com.reposilite.maven.infrastructure.CacheCommand
import com.reposilite.maven.infrastructure.MavenApiEndpoints
import com.reposilite.maven.infrastructure.MavenLatestApiEndpoints
import com.reposilite.maven.infrastructure.MavenStorageEndpoints
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.parameters
import com.reposilite.plugin.reposilite
import com.reposilite.shared.http.ConfiguredHttpRemoteClientProvider
import com.reposilite.shared.http.UpstreamStatusMode
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.format.ExtensionsFormatContext
import net.onelitefeather.ingot.format.RepositoryFormats
import net.onelitefeather.ingot.format.RepositoryRouteDispatcher
import net.onelitefeather.ingot.repository.index.SearchFacadeArtifactIndexSink
import java.time.Clock
import panda.std.reactive.computed

@Plugin(
    name = "maven",
    dependencies = ["failure", "local-configuration", "shared-configuration", "statistics", "frontend", "authentication", "access-token", "storage"],
    settings = MavenSettings::class
)
internal class MavenPlugin : ReposilitePlugin() {

    override fun initialize(): MavenFacade {
        val sharedConfigurationFacade = facade<SharedConfigurationFacade>()
        val mavenSettings = sharedConfigurationFacade.getDomainSettings<MavenSettings>()
        val artifactIndexSink = SearchFacadeArtifactIndexSink(extensions())
        val clock = Clock.systemDefaultZone()

        val mavenFacade =
            MavenComponents(
                clock = clock,
                workingDirectory = parameters().workingDirectory,
                journalist = this,
                extensions = extensions(),
                remoteClientProvider = ConfiguredHttpRemoteClientProvider(UpstreamStatusMode.read()),
                failureFacade = facade(),
                storageFacade = facade(),
                authenticationFacade = facade(),
                accessTokenFacade = facade(),
                statisticsFacade = facade(),
                ioService = reposilite().ioService,
                mavenSettings = mavenSettings,
                frontendSettings = sharedConfigurationFacade.getDomainSettings<FrontendSettings>(),
                mirrorCacheLedger = SqlMirrorCacheLedger(reposilite().database),
                artifactIndexSink = artifactIndexSink,
                cachePrivateAsPublic = PrivateAsPublicSetting.read(),
                metadataLocks = metadataLocksFor(reposilite().database, clock),
                mirrorEnvironment = MirrorEnvironment.read(),
                uploadChecksumDefault = readInstanceUploadChecksumPolicy(),
            ).mavenFacade()

        // Consumers that start before this plugin look the registry up lazily, so it only has to exist by then.
        extensions().registerFacade(mavenFacade.repositoryRegistry())

        // The routes of every repository format, served by the handler of the format of each repository. Registered as a facade
        // so that the frontend and the redirect plugin hand their repository requests to the same dispatcher.
        val repositoryRouteDispatcher = RepositoryRouteDispatcher(
            registry = mavenFacade.repositoryRegistry(),
            formats = RepositoryFormats.installed(),
            context = ExtensionsFormatContext(extensions()),
        )
        extensions().registerFacade(repositoryRouteDispatcher)

        logger.info("")
        logger.info("--- Repositories")
        mavenFacade.getRepositories().forEach { logger.info("+ ${it.name} (${it.visibility.toString().lowercase()})") }
        logger.info("${mavenFacade.getRepositories().size} repositories have been found")

        // Checked at startup and again on every settings change, so an id added at runtime is reported as well.
        warnAboutReservedRepositoryIds(mavenSettings.get())
        computed(mavenSettings) { warnAboutReservedRepositoryIds(mavenSettings.get()) }

        event { event: RoutingSetupEvent ->
            val localConfiguration = facade<LocalConfiguration>()
            event.registerRoutes(MavenApiEndpoints(mavenFacade))
            event.registerRoutes(repositoryRouteDispatcher)
            event.registerRoutes(MavenLatestApiEndpoints(mavenFacade, localConfiguration.compressionStrategy.get()))
            event.registerRoutes(MavenStorageEndpoints(mavenFacade))
        }

        event(PreservedBuildsListener(mavenFacade, artifactIndexSink))

        event { event: CommandsSetupEvent ->
            event.registerCommand(CacheCommand(mavenFacade))
        }

        event { _: ReposiliteDisposeEvent ->
            mavenFacade.getRepositories().forEach {
                it.shutdown()
            }
        }

        return mavenFacade
    }

    private fun warnAboutReservedRepositoryIds(settings: MavenSettings) {
        DashboardPaths.reservedRepositoryWarnings(settings.repositories.map { it.id })
            .forEach { logger.warn(it) }
    }

}