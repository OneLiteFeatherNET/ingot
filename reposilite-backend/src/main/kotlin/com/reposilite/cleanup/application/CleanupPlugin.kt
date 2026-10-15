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

package com.reposilite.cleanup.application

import com.reposilite.cleanup.CleanupActor
import com.reposilite.cleanup.CleanupScheduler
import com.reposilite.cleanup.CleanupService
import com.reposilite.cleanup.CleanupTarget
import com.reposilite.cleanup.VersionDeleter
import com.reposilite.cleanup.infrastructure.CleanupEndpoints
import com.reposilite.cleanup.infrastructure.SqlCleanupRunRepository
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.api.DeleteRequest
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.reposilite
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.storage.api.Location
import com.reposilite.web.api.RoutingSetupEvent
import panda.std.Result
import java.time.Clock
import java.util.concurrent.TimeUnit.SECONDS

@Plugin(name = "cleanup", dependencies = ["shared-configuration", "maven"], settings = CleanupSettings::class)
internal class CleanupPlugin : ReposilitePlugin() {

    override fun initialize(): CleanupService {
        val clock = Clock.systemDefaultZone()
        val cleanupSettings = facade<SharedConfigurationFacade>().getDomainSettings<CleanupSettings>()
        val mavenFacade = facade<MavenFacade>()

        val service = CleanupService(
            journalist = this,
            clock = clock,
            policies = { cleanupSettings.get().policies },
            targets = {
                mavenFacade.getRepositories().map { repository ->
                    CleanupTarget(
                        name = repository.name,
                        storage = repository.storageProvider,
                        storesMirrors = repository.mirrorHosts.any { it.configuration.store },
                        cachedVersions = mavenFacade.mirrorCacheEntries(repository.name),
                    )
                }
            },
            deleter = VersionDeleter { repository, path, actor -> deleteVersion(mavenFacade, repository, path, actor) },
            runs = SqlCleanupRunRepository(reposilite().database),
        )

        val scheduler = CleanupScheduler(
            clock = clock,
            policies = { cleanupSettings.get().policies },
            start = { policy ->
                reposilite().ioService.execute {
                    service.runScheduled(policy.id).onError { logger.warn("CLEANUP | Scheduled run of ${policy.id} failed: ${it.message}") }
                }
            },
        )
        val tick = reposilite().scheduler.scheduleWithFixedDelay({
            runCatching { scheduler.tick() }.onFailure { logger.warn("CLEANUP | Scheduler tick failed: ${it.message}") }
        }, 30, 30, SECONDS)

        event { _: ReposiliteDisposeEvent ->
            tick.cancel(false)
        }

        event { event: RoutingSetupEvent ->
            event.registerRoutes(CleanupEndpoints(service))
        }

        return service
    }

    /**
     * Deletes one version directory. A manager's run goes through the token-checked delete service, a scheduled run
     * through the unchecked path of the same service, because the scheduler has no token. Both update the metadata.
     */
    private fun deleteVersion(mavenFacade: MavenFacade, repositoryName: String, path: String, actor: CleanupActor): Result<Unit, ErrorResponse> {
        val repository = mavenFacade.getRepository(repositoryName) ?: return notFoundError("Repository $repositoryName not found")
        val gav = Location.of(path)
        val accessToken = actor.accessToken
        return if (accessToken != null) {
            mavenFacade.deleteFile(DeleteRequest(accessToken, repository, gav, actor.by))
        } else {
            mavenFacade.deleteUnchecked(repository, gav, actor.by)
        }
    }

}
