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

package com.reposilite.audit.application

import com.reposilite.audit.AuditFacade
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.audit.infrastructure.AuditEndpoints
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.api.DeleteEvent
import com.reposilite.maven.api.DeployEvent
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.api.ReposiliteInitializeEvent
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.reposilite
import com.reposilite.web.api.RoutingSetupEvent
import java.time.Clock
import java.util.concurrent.TimeUnit.HOURS

/**
 * Keeps the audit log. Administrative events arrive as [AuditEvent] from the services that changed
 * something, artifact events are read from the repository events, and a daily job applies the retention.
 */
@Plugin(name = "audit", dependencies = ["shared-configuration"], settings = AuditSettings::class)
internal class AuditPlugin : ReposilitePlugin() {

    override fun initialize(): AuditFacade {
        val sharedConfigurationFacade = facade<SharedConfigurationFacade>()

        val auditFacade = AuditComponents(
            journalist = this,
            database = reposilite().database,
            settings = sharedConfigurationFacade.getDomainSettings<AuditSettings>(),
            clock = Clock.systemUTC(),
        ).auditFacade()

        event { event: AuditEvent ->
            recordSafely { auditFacade.record(event) }
        }

        event { event: DeployEvent ->
            recordSafely {
                auditFacade.record(
                    AuditEvent(
                        action = AuditAction.ARTIFACT_DEPLOYED,
                        actor = AuditActor.fromSessionIdentifier(event.by),
                        repository = event.repository.name,
                        path = event.gav.toString(),
                    )
                )
            }
        }

        event { event: DeleteEvent ->
            recordSafely {
                auditFacade.record(
                    AuditEvent(
                        action = AuditAction.ARTIFACT_DELETED,
                        actor = AuditActor.fromSessionIdentifier(event.by),
                        repository = event.repository.name,
                        path = event.gav.toString(),
                    )
                )
            }
        }

        event { _: ReposiliteInitializeEvent ->
            reposilite().scheduler.scheduleWithFixedDelay({ recordSafely { auditFacade.purgeExpired() } }, 1, 24, HOURS)
        }

        event { event: RoutingSetupEvent ->
            event.registerRoutes(AuditEndpoints(auditFacade))
        }

        return auditFacade
    }

    /**
     * Auditing must never fail the action it describes: a deploy or a token change is not undone
     * because its log line could not be written. The failure is logged instead.
     */
    private fun recordSafely(block: () -> Unit) {
        try {
            block()
        } catch (exception: Exception) {
            logger.warn("AUDIT | Event could not be recorded: ${exception.message}")
        }
    }

}
