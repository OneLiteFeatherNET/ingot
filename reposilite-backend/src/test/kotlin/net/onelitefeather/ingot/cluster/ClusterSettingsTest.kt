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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class ClusterSettingsTest {

    /** Sources made from two maps, so that no test reads the process environment or its properties. */
    private fun sources(
        properties: Map<String, String> = emptyMap(),
        variables: Map<String, String> = emptyMap(),
        hostName: String = "ingot-0",
    ) = ClusterSources(property = properties::get, variable = variables::get, hostName = { hostName })

    @Test
    fun `the system property takes precedence over both variables`() {
        val settings = ClusterSettings.read(
            sources(
                properties = mapOf("ingot.cluster.url" to "nats://property.example:4222"),
                variables = mapOf(
                    "INGOT_CLUSTER_URL" to "nats://ingot.example:4222",
                    "REPOSILITE_CLUSTER_URL" to "nats://legacy.example:4222",
                ),
            )
        )

        assertThat(settings.url?.host).isEqualTo("property.example")
    }

    @Test
    fun `INGOT_CLUSTER_URL takes precedence over the legacy variable`() {
        val settings = ClusterSettings.read(
            sources(
                variables = mapOf(
                    "INGOT_CLUSTER_URL" to "nats://ingot.example:4222",
                    "REPOSILITE_CLUSTER_URL" to "nats://legacy.example:4222",
                ),
            )
        )

        assertThat(settings.url?.host).isEqualTo("ingot.example")
    }

    @Test
    fun `the legacy REPOSILITE_CLUSTER_URL alone enables the bus`() {
        val settings = ClusterSettings.read(sources(variables = mapOf("REPOSILITE_CLUSTER_URL" to "nats://legacy.example:4222")))

        assertThat(settings.url?.host).isEqualTo("legacy.example")
    }

    @Test
    fun `no url in any source leaves the bus unconfigured`() {
        assertThat(ClusterSettings.read(sources()).url).isNull()
    }

    @Test
    fun `an unknown scheme in the url stops the settings with a configuration error`() {
        assertThatThrownBy {
            ClusterSettings.read(sources(variables = mapOf("INGOT_CLUSTER_URL" to "http://bus.example:8080")))
        }.isInstanceOf(ClusterConfigurationException::class.java)
    }

    @Test
    fun `INGOT_CLUSTER_PASSWORD fills in a password that the url does not carry`() {
        val settings = ClusterSettings.read(
            sources(
                variables = mapOf(
                    "INGOT_CLUSTER_URL" to "nats://nats.example:4222",
                    "INGOT_CLUSTER_PASSWORD" to "s3cret",
                ),
            )
        )

        assertThat(settings.url?.password).isEqualTo("s3cret")
    }

    @Test
    fun `REPOSILITE_CLUSTER_PASSWORD is the fallback for the password`() {
        val settings = ClusterSettings.read(
            sources(
                variables = mapOf(
                    "REPOSILITE_CLUSTER_URL" to "nats://nats.example:4222",
                    "REPOSILITE_CLUSTER_PASSWORD" to "legacy-secret",
                ),
            )
        )

        assertThat(settings.url?.password).isEqualTo("legacy-secret")
    }

    @Test
    fun `a password in the url wins over the password variable`() {
        val settings = ClusterSettings.read(
            sources(
                variables = mapOf(
                    "INGOT_CLUSTER_URL" to "nats://ops:url-secret@nats.example:4222",
                    "INGOT_CLUSTER_PASSWORD" to "variable-secret",
                ),
            )
        )

        assertThat(settings.url?.password).isEqualTo("url-secret")
    }

    @Test
    fun `the credentials file property takes precedence over INGOT_CLUSTER_CREDENTIALS`() {
        val settings = ClusterSettings.read(
            sources(
                properties = mapOf("ingot.cluster.credentials" to "/run/property.creds"),
                variables = mapOf(
                    "INGOT_CLUSTER_URL" to "nats://nats.example:4222",
                    "INGOT_CLUSTER_CREDENTIALS" to "/run/variable.creds",
                ),
            )
        )

        assertThat(settings.credentialsFile).isEqualTo(Path.of("/run/property.creds"))
    }

    @Test
    fun `a credentials file is ignored with a warning for a Redis scheme`() {
        val settings = ClusterSettings.read(
            sources(
                variables = mapOf(
                    "INGOT_CLUSTER_URL" to "valkey://cache.example:6379",
                    "INGOT_CLUSTER_CREDENTIALS" to "/run/nats.creds",
                ),
            )
        )

        assertThat(settings.credentialsFile).isNull()
        assertThat(settings.warnings).anySatisfy { assertThat(it).contains("valkey") }
    }

    @Test
    fun `credentials over an unencrypted scheme warn with the scheme and never with the credentials`() {
        val settings = ClusterSettings.read(
            sources(variables = mapOf("INGOT_CLUSTER_URL" to "nats://ops:s3cret@nats.example:4222")),
        )

        assertThat(settings.warnings).anySatisfy { assertThat(it).contains("nats") }
        assertThat(settings.warnings.joinToString("\n")).doesNotContain("s3cret").doesNotContain("ops")
    }

    @Test
    fun `the instance identifier defaults to the host name`() {
        assertThat(ClusterSettings.read(sources(hostName = "ingot-7")).instanceId).isEqualTo("ingot-7")
    }

    @Test
    fun `INGOT_CLUSTER_INSTANCE_ID overrides the host name`() {
        val settings = ClusterSettings.read(sources(hostName = "ingot-7", variables = mapOf("INGOT_CLUSTER_INSTANCE_ID" to "edge-1")))

        assertThat(settings.instanceId).isEqualTo("edge-1")
    }

    @Test
    fun `console fan-out is on and failure aggregation is off by default`() {
        val settings = ClusterSettings.read(sources())

        assertThat(settings.console).isTrue()
        assertThat(settings.failures).isFalse()
    }

    @Test
    fun `INGOT_CLUSTER_CONSOLE switches console fan-out off`() {
        val settings = ClusterSettings.read(sources(variables = mapOf("INGOT_CLUSTER_CONSOLE" to "false")))

        assertThat(settings.console).isFalse()
    }

    @Test
    fun `console fan-out defaults to on for the broker schemes`() {
        listOf("nats", "tls", "redis", "rediss", "valkey", "valkeys").forEach { scheme ->
            assertThat(ClusterSettings.consoleFanOutDefault(scheme))
                .describedAs("console fan-out default for $scheme")
                .isTrue()
        }
    }

    @Test
    fun `console fan-out defaults to off on the database transport`() {
        assertThat(ClusterSettings.consoleFanOutDefault("database")).isFalse()
    }

    @Test
    fun `console fan-out defaults to on without a bus scheme`() {
        assertThat(ClusterSettings.consoleFanOutDefault(null)).isTrue()
    }

    @Test
    fun `an invalid boolean names the variable it came from`() {
        assertThatThrownBy {
            ClusterSettings.read(sources(variables = mapOf("INGOT_CLUSTER_FAILURES" to "maybe")))
        }.isInstanceOf(ClusterConfigurationException::class.java)
            .hasMessageContaining("INGOT_CLUSTER_FAILURES")
    }
}
