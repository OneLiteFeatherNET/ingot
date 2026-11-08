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

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.reposilite.SharedContainers
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

/** How long a test waits for a connection to be established. Bounded, never a sleep. */
internal val CONNECT_BOUND: Duration = Duration.ofSeconds(60)

/** How long a test waits for one event to arrive. Bounded, never a sleep. */
internal val RECEIVE_BOUND: Duration = Duration.ofSeconds(15)

private const val CONFIGURATION_PATH = "/etc/nats/server.conf"

/**
 * A NATS broker that is not shared with other tests. A [fixedPort] lets a test stop the broker and start it again at the
 * address the transports already know. A [configuration] replaces the default server configuration, for operator mode.
 */
internal fun natsBroker(fixedPort: Int? = null, configuration: ByteArray? = null): GenericContainer<*> {
    val container = GenericContainer(DockerImageName.parse(SharedContainers.NATS_IMAGE))
        .withExposedPorts(SharedContainers.NATS_PORT)
        .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))
    if (fixedPort != null) {
        container.withCreateContainerCmdModifier { command ->
            command.withHostConfig(
                command.hostConfig.withPortBindings(
                    PortBinding(Ports.Binding.bindPort(fixedPort), ExposedPort(SharedContainers.NATS_PORT))
                )
            )
        }
    }
    if (configuration != null) {
        container
            .withCopyToContainer(Transferable.of(configuration), CONFIGURATION_PATH)
            .withCommand("-c", CONFIGURATION_PATH)
    }
    return container
}

/** A port that was free a moment ago. Only used by tests that start their broker on it later. */
internal fun freePort(): Int =
    ServerSocket(0).use { it.localPort }

/** The bus URL of a broker on [port] on this machine. */
internal fun localNatsUrl(port: Int): String =
    "nats://localhost:$port"

/**
 * Opens a NATS transport through the factory the plugin uses. Settings come from the same source reader as in production,
 * so the URL, the credentials file and the redaction take the production path.
 */
internal fun openNatsTransport(url: String, cluster: String, credentials: Path? = null): NatsClusterTransport {
    val sources = ClusterSources(
        property = { key ->
            when (key) {
                "ingot.cluster.url" -> url
                "ingot.cluster.credentials" -> credentials?.toString()
                else -> null
            }
        },
        variable = { null },
        hostName = { "ingot-test" },
    )
    val settings = ClusterSettings.read(sources)
    return NatsClusterTransportFactory().open(requireNotNull(settings.url) { "the test url was not read" }, settings, cluster)
}

/** Waits until [expected] is delivered, ignoring other messages that arrive first. Bounded by [RECEIVE_BOUND]. */
internal fun BlockingQueue<String>.awaitMessage(expected: String): Boolean {
    val deadline = System.nanoTime() + RECEIVE_BOUND.toNanos()
    while (true) {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) return false
        val message = poll(remaining, TimeUnit.NANOSECONDS) ?: return false
        if (message == expected) return true
    }
}
