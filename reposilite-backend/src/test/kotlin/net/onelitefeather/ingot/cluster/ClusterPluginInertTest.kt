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

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.TokenStateChangedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Without a bus URL the cluster plugin is inert: no transport, no thread and no published event. */
internal class ClusterPluginInertTest {

    private val logger = InMemoryLogger()

    /** Counts the connections it is asked to open. Every scheme of the plugin is routed to it. */
    private class CountingFactory : ClusterTransportFactory {
        var opened = 0

        override fun open(url: ClusterBusUrl, settings: ClusterSettings, cluster: String): ClusterTransport {
            opened++
            return object : ClusterTransport {
                override fun publish(message: String) {}

                override fun subscribe(listener: (String) -> Unit) {}

                override fun close() {}
            }
        }
    }

    private fun inertPlugin(factory: CountingFactory): Extensions {
        val extensions = Extensions(logger)
        val transports = ClusterBusUrl.ACCEPTED_SCHEMES.associateWith { factory }
        val plugin = ClusterPlugin(ClusterSources(property = { null }, variable = { null }, hostName = { "ingot-0" }), transports)
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return extensions
    }

    @Test
    fun `without a bus url no transport factory is invoked`() {
        // given: a plugin whose sources define no bus url
        val factory = CountingFactory()

        // when: the plugin initializes
        inertPlugin(factory)

        // then: no connection is opened
        assertThat(factory.opened).isZero()
    }

    @Test
    fun `without a bus url no cluster thread starts`() {
        // given: the cluster threads that exist before the plugin initializes
        val before = clusterThreadNames()

        // when: the plugin initializes without a bus url
        inertPlugin(CountingFactory())

        // then: no cluster thread was started
        assertThat(clusterThreadNames() - before).isEmpty()
    }

    @Test
    fun `without a bus url no event is published to a transport`() {
        // given: an inert plugin with a counting transport
        val factory = CountingFactory()
        val extensions = inertPlugin(factory)

        // when: a token state change happens on the instance
        extensions.emitEvent(TokenStateChangedEvent("ci-bot"))

        // then: no transport exists that could publish it
        assertThat(factory.opened).isZero()
    }

    private fun clusterThreadNames(): Set<String> =
        Thread.getAllStackTraces().keys.map { it.name }.filter { it.startsWith(CLUSTER_THREAD_PREFIX) }.toSet()

    private companion object {
        const val CLUSTER_THREAD_PREFIX = "ingot-cluster"
    }
}
