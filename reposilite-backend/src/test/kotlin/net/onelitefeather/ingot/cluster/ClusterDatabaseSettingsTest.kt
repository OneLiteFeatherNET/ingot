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

/** The database transport settings: the poll interval and its clamp, the console default, and the bare URL. */
internal class ClusterDatabaseSettingsTest {

    private fun settings(vararg pairs: Pair<String, String>): ClusterSettings {
        val variables = mapOf("INGOT_CLUSTER_INSTANCE_ID" to "ingot-a", *pairs)
        return ClusterSettings.read(ClusterSources(property = { null }, variable = variables::get, hostName = { "host" }))
    }

    private val database = "INGOT_CLUSTER_URL" to "database:"

    @Test
    fun `the poll interval defaults to one second on the database transport`() {
        assertThat(settings(database).pollIntervalMillis).isEqualTo(1000L)
    }

    @Test
    fun `a configured poll interval is used as it is`() {
        val configured = settings(database, "INGOT_CLUSTER_POLL_INTERVAL_MS" to "500")

        assertThat(configured.pollIntervalMillis).isEqualTo(500L)
        assertThat(configured.warnings).isEmpty()
    }

    @Test
    fun `a poll interval below 200 ms is raised to 200 ms with one warning that names the interval`() {
        val clamped = settings(database, "INGOT_CLUSTER_POLL_INTERVAL_MS" to "50")

        assertThat(clamped.pollIntervalMillis).isEqualTo(200L)
        assertThat(clamped.warnings).hasSize(1)
        assertThat(clamped.warnings.single()).contains("200 ms")
    }

    @Test
    fun `a poll interval that is not a whole number of milliseconds stops the startup`() {
        assertThatThrownBy { settings(database, "INGOT_CLUSTER_POLL_INTERVAL_MS" to "fast") }
            .isInstanceOf(ClusterConfigurationException::class.java)
            .hasMessageContaining("INGOT_CLUSTER_POLL_INTERVAL_MS")
    }

    @Test
    fun `the poll interval is not read when another transport is configured`() {
        val nats = settings("INGOT_CLUSTER_URL" to "nats://bus.example:4222", "INGOT_CLUSTER_POLL_INTERVAL_MS" to "50")

        assertThat(nats.warnings).isEmpty()
    }

    @Test
    fun `the console fan-out is off on the database transport by default`() {
        assertThat(settings(database).console).isFalse()
    }

    @Test
    fun `the console fan-out is on on the database transport when it is enabled explicitly`() {
        assertThat(settings(database, "INGOT_CLUSTER_CONSOLE" to "true").console).isTrue()
    }

    @Test
    fun `the console fan-out stays on by default on the other transports`() {
        assertThat(settings("INGOT_CLUSTER_URL" to "nats://bus.example:4222").console).isTrue()
    }

    @Test
    fun `the bare database url is accepted in both spellings`() {
        assertThat(ClusterBusUrl.parse("database:").redacted()).isEqualTo("database://")
        assertThat(ClusterBusUrl.parse("database://").redacted()).isEqualTo("database://")
    }

    @Test
    fun `a database url with a host keeps the host out of its redacted form and string form`() {
        val url = ClusterBusUrl.parse("database://broker.example:5432")

        assertThat(url.redacted()).doesNotContain("broker.example")
        assertThat(url.toString()).doesNotContain("broker.example")
    }

    @Test
    fun `a database url with credentials keeps the credentials out of its string form`() {
        val url = ClusterBusUrl.parse("database://ops:s3cret@broker.example:5432")

        assertThat(url.toString()).doesNotContain("s3cret").doesNotContain("broker.example")
        assertThat(url.redacted()).doesNotContain("s3cret").doesNotContain("broker.example")
    }
}
