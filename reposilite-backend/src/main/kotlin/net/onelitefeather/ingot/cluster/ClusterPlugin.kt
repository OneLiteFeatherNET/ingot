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
import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.api.TokenStateChangedEvent
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.status.ClusterStatusSource
import com.reposilite.status.StatusFacade
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
        "status",
    ],
)
class ClusterPlugin(
    private val sources: ClusterSources = ClusterSources.system(),
    private val transports: Map<String, ClusterTransportFactory> = emptyMap(),
    /** Replaces the lookup of the cluster identifier in the configuration table, for tests. */
    private val clusterIdentifier: (() -> String)? = null,
    private val clock: Clock = Clock.systemUTC(),
) : ReposilitePlugin() {

    internal val counters = ClusterCounters(clock)
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
            attachStatus(ClusterStatusView(url, counters) { ConnectionState.DISABLED })
            return null
        }

        logger.info("Cluster | connecting to ${url.redacted()}")
        val transport = factory.open(url, settings)
        attachStatus(ClusterStatusView(url, counters) { transport.connectionState() })
        val receiver = ClusterReceiver(identifier, settings.instanceId, counters)
        transport.subscribe { raw -> receiver.accept(raw)?.let { dispatch(it) } }

        val outbox = ClusterOutbox(OUTBOX_CAPACITY)
        outbox.drainTo(
            sink = { transport.publish(it) },
            onError = { logger.warn("Cluster | a message could not be sent: ${it.message}") },
        )

        event { event: TokenStateChangedEvent ->
            publish(outbox, identifier, settings.instanceId, ClusterEventType.TOKEN_CHANGED, mapOf("name" to event.tokenName))
        }
        event { _: ReposiliteDisposeEvent ->
            outbox.close()
            transport.close()
        }
        return null
    }

    private fun attachStatus(source: ClusterStatusSource) {
        extensions().getFacades().filterIsInstance<StatusFacade>().forEach { it.attachClusterStatus(source) }
    }

    private fun dispatch(envelope: ClusterEnvelope) {
        handlers[envelope.type]?.forEach { it(envelope) }
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
        val envelope = ClusterEnvelope(type = type, cluster = cluster, origin = origin, at = Instant.now(clock), payload = payload)
        outbox.offer(ClusterEnvelopeCodec.encode(envelope))
        counters.countPublished()
    }

    private companion object {
        const val CLUSTER_IDENTIFIER_NAME = "cluster-identifier"

        // Messages waiting for the transport. A full queue drops its oldest message, so memory stays bounded during an outage.
        const val OUTBOX_CAPACITY = 1024
    }

}
