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
import com.reposilite.scheduling.infrastructure.SqlScheduledJobClaims
import io.javalin.http.HttpStatus.CONFLICT
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
        // One claim store for the service and the scheduler, so that the day lease and the run lease have the same owner.
        val claims = SqlScheduledJobClaims(reposilite().database, clock)

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
            deleter = VersionDeleter { repository, path, actor, runId -> deleteVersion(mavenFacade, repository, path, actor, runId) },
            runs = SqlCleanupRunRepository(reposilite().database),
            extensions = extensions(),
            claims = claims,
        )

        val scheduler = CleanupScheduler(
            clock = clock,
            policies = { cleanupSettings.get().policies },
            // The run is synchronous, so the scheduler can mark the day done once it has ended. The tick runs on the
            // IO pool for the same reason: a run must not hold the scheduler thread.
            start = { policy ->
                service.runScheduled(policy.id, CleanupScheduler.dayKey(policy.id, clock))
                    .onError { if (it.status != CONFLICT.code) logger.warn("CLEANUP | Scheduled run of ${policy.id} failed: ${it.message}") }
            },
            claims = claims,
            journalist = this,
        )
        val tick = reposilite().scheduler.scheduleWithFixedDelay({
            reposilite().ioService.execute {
                runCatching { scheduler.tick() }.onFailure { logger.warn("CLEANUP | Scheduler tick failed: ${it.message}") }
            }
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
    private fun deleteVersion(
        mavenFacade: MavenFacade,
        repositoryName: String,
        path: String,
        actor: CleanupActor,
        runId: String,
    ): Result<Unit, ErrorResponse> {
        val repository = mavenFacade.getRepository(repositoryName) ?: return notFoundError("Repository $repositoryName not found")
        val gav = Location.of(path)
        val accessToken = actor.accessToken
        return if (accessToken != null) {
            mavenFacade.deleteFile(DeleteRequest(accessToken, repository, gav, actor.by, cleanupRunId = runId))
        } else {
            mavenFacade.deleteUnchecked(repository, gav, actor.by, cleanupRunId = runId)
        }
    }

}
