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

package net.onelitefeather.ingot.cluster

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.ReposiliteJournalist
import com.reposilite.auth.AuthenticationFacade
import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.journalist.Channel
import com.reposilite.maven.MavenFacade
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.api.SessionFamilyRevokedEvent
import com.reposilite.plugin.api.StorageUsageRefreshedEvent
import com.reposilite.plugin.api.TokenStateChangedEvent
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.reposilite
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.identity.login.LoginFacade
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The optional cluster bus. Without a bus URL in any source it does nothing: no transport is opened, no thread starts and
 * no event is published. With a URL it creates the cluster identifier of the database and opens the transport of the scheme.
 *
 * [transports] maps each accepted scheme to its factory. A scheme without a factory leaves the instance without the bus.
 */
@Plugin(
    name = "cluster",
    dependencies = [
        "access-token",
        "authentication",
        "configuration",
        "shared-configuration",
        "maven",
        "console",
        "cleanup",
        "audit",
        "local-configuration",
        "login",
        "failure",
    ],
)
class ClusterPlugin(
    private val sources: ClusterSources = ClusterSources.system(),
    private val transports: Map<String, ClusterTransportFactory> = emptyMap(),
    /** Replaces the lookup of the cluster identifier in the configuration table, for tests. */
    private val clusterIdentifier: (() -> String)? = null,
    private val clock: Clock = Clock.systemUTC(),
    /** Replaces the hand-over of a received usage snapshot to the maven plugin, for tests. */
    private val storageUsage: ((StorageUsageRefreshedEvent) -> Unit)? = null,
    /** Applies the cache events through the running facades when null. */
    private val cacheHooks: ClusterCacheHooks? = null,
    /** The journalist whose lines are forwarded. Without it the instance's own journalist of the running server is used. */
    private val consoleJournalist: ReposiliteJournalist? = null,
) : ReposilitePlugin() {

    internal val counters = ClusterCounters()
    private val payloads = ObjectMapper()
    private val handlers = ConcurrentHashMap<ClusterEventType, CopyOnWriteArrayList<(ClusterEnvelope) -> Unit>>()

    /**
     * Registers what this instance does with a received event of [type]. The handler runs after the message has been
     * accepted by [ClusterReceiver], and it must not publish anything: applying an event never causes another one.
     */
    internal fun onReceived(type: ClusterEventType, handler: (ClusterEnvelope) -> Unit) {
        handlers.computeIfAbsent(type) { CopyOnWriteArrayList() }.add(handler)
    }

    override fun initialize(): Facade? {
        val settings = ClusterSettings.read(sources)
        val url = settings.url ?: return null
        settings.warnings.forEach { logger.warn("Cluster | $it") }

        val identifier = clusterIdentifier?.invoke()
            ?: facade<ConfigurationFacade>().findOrCreateConfiguration(CLUSTER_IDENTIFIER_NAME) { UUID.randomUUID().toString() }
        logger.debug("Cluster | cluster identifier $identifier, instance ${settings.instanceId}")

        val factory = transports[url.scheme]
        if (factory == null) {
            logger.warn("Cluster | no transport is available for ${url.scheme} in this build, the instance runs without the bus")
            return null
        }

        logger.info("Cluster | connecting to ${url.redacted()}")
        val transport = factory.open(url, settings)
        registerInvalidations()
        // Console lines are shown and forwarded only while the switch is on, so the switch turns both directions off.
        val journalist = if (settings.console) consoleJournalist ?: reposilite().journalist else null
        if (journalist != null) {
            onReceived(ClusterEventType.CONSOLE_LINE) { deliverRemote(journalist, it) }
        }

        val receiver = ClusterReceiver(identifier, settings.instanceId, counters)
        onReceived(ClusterEventType.STORAGE_USAGE) { envelope ->
            StorageUsageMessage.fromPayload(envelope.payload)?.let { applyStorageUsage(it) }
        }
        val remoteFailures = if (settings.failures) {
            RemoteFailures().also { remote -> onReceived(ClusterEventType.FAILURE_RECORDED) { remote.accept(it) } }
        } else {
            null
        }
        transport.subscribe { raw -> receiver.accept(raw)?.let { dispatch(it) } }

        val outbox = ClusterOutbox(OUTBOX_CAPACITY)
        outbox.drainTo(
            sink = { transport.publish(it) },
            onError = { logger.warn("Cluster | a message could not be sent: ${it.message}") },
        )

        ClusterCacheEvents.install(this, cacheHooks ?: FacadeCacheHooks(this)) { type, fields ->
            publish(outbox, identifier, settings.instanceId, type, fields)
        }
        event { event: TokenStateChangedEvent ->
            publish(outbox, identifier, settings.instanceId, ClusterEventType.TOKEN_CHANGED, mapOf("name" to event.tokenName))
        }
        event { event: SessionFamilyRevokedEvent ->
            publish(outbox, identifier, settings.instanceId, ClusterEventType.SESSION_REVOKED, mapOf("family" to event.familyId))
        }
        event { event: StorageUsageRefreshedEvent ->
            publishPayload(outbox, identifier, settings.instanceId, ClusterEventType.STORAGE_USAGE, StorageUsageMessage.toPayload(event))
        }
        remoteFailures?.let { aggregateFailures(outbox, identifier, settings.instanceId, it) }
        val stopConsole = journalist?.let { forwardConsole(it, transport, identifier, settings.instanceId) }
        event { _: ReposiliteDisposeEvent ->
            outbox.close()
            stopConsole?.invoke()
            transport.close()
        }
        return null
    }

    /**
     * What a received token change and session revocation do on this instance. The handlers call the internal invalidation
     * functions of the facades, never the facade methods that emit the event, so an applied event is not announced again.
     * A facade that is not registered in this build leaves its part unapplied; the database still decides.
     */
    private fun registerInvalidations() {
        onReceived(ClusterEventType.TOKEN_CHANGED) { envelope ->
            val name = envelope.payload.path("name").asText()
            val authentication = facadeOrNull<AuthenticationFacade>()
            if (name.isNotEmpty() && authentication != null) {
                authentication.forgetCachedCredentials(name)
            } else {
                logger.debug("Cluster | token change of '$name' not applied, no authentication facade")
            }
        }
        onReceived(ClusterEventType.SESSION_REVOKED) { envelope ->
            val family = envelope.payload.path("family").asText()
            val login = facadeOrNull<LoginFacade>()
            if (family.isNotEmpty() && login != null) {
                login.denySessionFamily(family)
            } else {
                logger.debug("Cluster | session revocation not applied, no login facade")
            }
        }
    }

    private inline fun <reified F : Facade> facadeOrNull(): F? =
        extensions().getFacades().filterIsInstance<F>().firstOrNull()

    private fun dispatch(envelope: ClusterEnvelope) {
        handlers[envelope.type]?.forEach { it(envelope) }
    }

    /** Publishes each failure this instance records, and merges the ones the other instances published into the failure list. */
    private fun aggregateFailures(outbox: ClusterOutbox, cluster: String, origin: String, remote: RemoteFailures) {
        val failures = facade<FailureFacade>()
        failures.mergeRemote { remote.snapshot() }
        failures.onRecorded { record ->
            val payload = payloads.createObjectNode()
                .put("source", record.source)
                .put("type", record.type)
            record.message?.let { payload.put("message", it) }
            publishPayload(outbox, cluster, origin, ClusterEventType.FAILURE_RECORDED, payload)
        }
    }

    /** Hands a received snapshot to the maven plugin, which keeps it in place of its own figures. */
    private fun applyStorageUsage(event: StorageUsageRefreshedEvent) {
        val receiver = storageUsage
        if (receiver != null) {
            receiver(event)
        } else {
            facade<MavenFacade>().applyReceivedStorageUsage(event)
        }
    }

    /**
     * Publishes the lines this instance logs at its visibility threshold. The lines wait in a queue of their own, so a burst of
     * log lines can only drop log lines and never a token or configuration event. Returns the function that stops forwarding.
     */
    private fun forwardConsole(journalist: ReposiliteJournalist, transport: ClusterTransport, cluster: String, origin: String): () -> Unit {
        val outbox = ClusterOutbox(CONSOLE_OUTBOX_CAPACITY)
        // Failures are not logged here: a log line would be forwarded as a new line, and the failure would repeat.
        outbox.drainTo(onError = {}, sink = { transport.publish(it) })
        val subscriberId = journalist.subscribeLocalLines { line ->
            publish(
                outbox,
                cluster,
                origin,
                ClusterEventType.CONSOLE_LINE,
                mapOf(
                    "channel" to line.key.name,
                    "message" to line.value,
                    "timestamp" to Instant.now(clock).toString(),
                ),
            )
        }
        return {
            journalist.unsubscribeLocalLines(subscriberId)
            outbox.close()
        }
    }

    /** Shows a line another instance logged, labelled with that instance. */
    private fun deliverRemote(journalist: ReposiliteJournalist, envelope: ClusterEnvelope) {
        val channel = Channel.of(envelope.payload.path("channel").asText()).orElse(Channel.INFO)
        journalist.deliverRemote(channel, envelope.origin, envelope.payload.path("message").asText())
    }

    private fun publish(
        outbox: ClusterOutbox,
        cluster: String,
        origin: String,
        type: ClusterEventType,
        fields: Map<String, String>,
    ) {
        val payload = payloads.createObjectNode()
        fields.forEach { (name, value) -> payload.put(name, value) }
        publishPayload(outbox, cluster, origin, type, payload)
    }

    private fun publishPayload(
        outbox: ClusterOutbox,
        cluster: String,
        origin: String,
        type: ClusterEventType,
        payload: ObjectNode,
    ) {
        val envelope = ClusterEnvelope(type = type, cluster = cluster, origin = origin, at = Instant.now(clock), payload = payload)
        outbox.offer(ClusterEnvelopeCodec.encode(envelope))
        counters.countPublished()
    }

    private companion object {
        const val CLUSTER_IDENTIFIER_NAME = "cluster-identifier"

        // Messages waiting for the transport. A full queue drops its oldest message, so memory stays bounded during an outage.
        const val OUTBOX_CAPACITY = 1024

        // Log lines waiting for the transport, kept apart from the events above so that a log burst cannot evict them.
        const val CONSOLE_OUTBOX_CAPACITY = 512
    }

}
