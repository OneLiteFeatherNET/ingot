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
import com.reposilite.maven.ChecksumResult
import com.reposilite.maven.api.DeleteEvent
import com.reposilite.maven.api.DeployEvent
import com.reposilite.maven.api.MirrorIntegrityEvent
import com.reposilite.maven.api.ProxyRefusedEvent
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.api.ReposiliteInitializeEvent
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.reposilite
import com.reposilite.token.AccessTokenFacade
import com.reposilite.web.api.RoutingSetupEvent
import java.time.Clock
import java.util.concurrent.TimeUnit.HOURS

/**
 * Keeps the audit log. Administrative events arrive as [AuditEvent] from the services that changed
 * something, artifact events are read from the repository events, and a daily job applies the retention.
 */
@Plugin(name = "audit", dependencies = ["shared-configuration", "access-token"], settings = AuditSettings::class)
internal class AuditPlugin : ReposilitePlugin() {

    override fun initialize(): AuditFacade {
        val sharedConfigurationFacade = facade<SharedConfigurationFacade>()

        val components = AuditComponents(
            journalist = this,
            database = reposilite().database,
            settings = sharedConfigurationFacade.getDomainSettings<AuditSettings>(),
            clock = Clock.systemUTC(),
        )
        val lineSink = components.lineSink()
        event { _: ReposiliteDisposeEvent -> lineSink.close() }
        val auditFacade = components.auditFacade(lineSink = lineSink)

        event { event: AuditEvent ->
            recordSafely { auditFacade.record(event) }
        }

        event { event: DeployEvent ->
            if (!deployAuditRecorded(event.by, auditFacade)) return@event
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
                artifactDeletedEvent(event.repository.name, event.gav.toString(), event.by, event.cleanupRunId)
                    ?.let(auditFacade::record)
            }
        }

        val accessTokenFacade = facade<AccessTokenFacade>()
        event { event: ProxyRefusedEvent ->
            recordSafely {
                val tokenName = event.accessToken?.let { accessTokenFacade.getAccessTokenById(it)?.name }
                auditFacade.record(proxyRefusedAuditEvent(event, tokenName))
            }
        }

        event { event: MirrorIntegrityEvent ->
            recordSafely { auditFacade.record(mirrorIntegrityAuditEvent(event)) }
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

/**
 * The audit event of a request that a protected group refused. The actor is the token that made the request, or the
 * anonymous actor when the token is unknown. The details name the matched prefix and the proxy members that were not asked,
 * and the address of the client is not known at this layer, so it is left out.
 */
internal fun proxyRefusedAuditEvent(event: ProxyRefusedEvent, tokenName: String?): AuditEvent =
    AuditEvent(
        action = AuditAction.NAMESPACE_PROXY_REFUSED,
        actor = tokenName?.let { AuditActor.named(name = it, address = null) } ?: AuditActor.anonymous(address = null),
        repository = event.repository,
        path = event.gav.toString(),
        details = mapOf("prefix" to event.prefix, "skippedMembers" to event.skippedMembers),
        success = false,
    )

/**
 * The audit event of a mirror copy that did not match its checksum, or whose host published none. The fetch has no request
 * actor, so the actor is the system. The details carry the host, the policy, the algorithm and the outcome, never a digest.
 */
internal fun mirrorIntegrityAuditEvent(event: MirrorIntegrityEvent): AuditEvent =
    AuditEvent(
        action = when (event.result) {
            ChecksumResult.MISMATCH -> AuditAction.MIRROR_CHECKSUM_MISMATCH
            ChecksumResult.MISSING -> AuditAction.MIRROR_CHECKSUM_MISSING
            ChecksumResult.VERIFIED -> error("A verified copy raises no integrity event")
        },
        actor = AuditActor.SYSTEM_ACTOR,
        repository = event.repository,
        path = event.path,
        details = mapOf(
            "host" to event.host,
            "policy" to event.policy.name.lowercase(),
            "algorithm" to event.algorithm,
            "outcome" to event.outcome.name.lowercase(),
        ),
        success = false,
    )

/**
 * The audit event of a removed file. A file removed by a cleanup run gets none: the run records one
 * [AuditAction.CLEANUP_RUN] event with its totals, so a run that removes thousands of versions stays readable.
 */
internal fun artifactDeletedEvent(repository: String, path: String, by: String, cleanupRunId: String?): AuditEvent? =
    if (cleanupRunId != null) {
        null
    } else {
        AuditEvent(
            action = AuditAction.ARTIFACT_DELETED,
            actor = AuditActor.fromSessionIdentifier(by),
            repository = repository,
            path = path,
        )
    }

/**
 * Whether a deploy made under the session identifier [by] is audited per file. A session registered as bulk by the Nexus import
 * is summarised by its run event instead. The match is exact, so a token that only shares the name is still audited.
 */
fun deployAuditRecorded(by: String, auditFacade: AuditFacade): Boolean = !auditFacade.isBulkSession(by)
