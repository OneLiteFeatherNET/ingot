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

package net.onelitefeather.ingot.nexusimport.nexus

import net.onelitefeather.ingot.nexusimport.fake.AdvancingClock
import net.onelitefeather.ingot.nexusimport.fake.FakeNexus
import net.onelitefeather.ingot.nexusimport.fake.FakeReply
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class NexusConnectionProbeTest {

    private val canary = "canary-password-7f3a"
    private lateinit var nexus: FakeNexus

    @BeforeEach
    fun setUp() {
        nexus = FakeNexus()
    }

    @AfterEach
    fun tearDown() {
        nexus.close()
    }

    private fun probe(password: String = canary): ConnectionReport {
        val connection = NexusConnection(nexus.baseUri, "importer", Secret(password))
        val client = NexusClient(connection, JdkNexusTransport(), AdvancingClock(), sleeper = {})
        return NexusConnectionProbe(client, connection).run()
    }

    private fun answerHealthy() {
        nexus.on(NexusClient.STATUS_PATH) { FakeReply.status(200, mapOf("Server" to "Nexus/3.70.1-02 (OSS)")) }
        nexus.serveFixture(NexusClient.REPOSITORIES_PATH, "repositories.json")
        nexus.serveFixture("/service/rest/v1/repositories/maven/hosted/releases", "maven-hosted-releases.json")
    }

    @Test
    fun `should report a healthy Nexus with version, edition and both reads`() {
        // given: a healthy Nexus that answers the status, the repositories and the hosted settings
        answerHealthy()

        // when: the connection is probed
        val report = probe()

        // then: the report names the version and edition and shows the credential may read everything
        assertThat(report.reachable).describedAs("the status endpoint answered").isTrue
        assertThat(report.credentialAccepted).describedAs("the repository list answered 200").isTrue
        assertThat(report.canReadSettings).describedAs("the hosted settings answered 200").isTrue
        assertThat(report.version).isEqualTo("3.70.1-02")
        assertThat(report.edition).isEqualTo("OSS")
        assertThat(report.problem).isNull()
    }

    @Test
    fun `should report bad credentials when the repository list answers 401`() {
        // given: the status answers, but the credential is refused
        nexus.on(NexusClient.STATUS_PATH) { FakeReply.status(200) }
        nexus.on(NexusClient.REPOSITORIES_PATH) { FakeReply.status(401) }

        // when: the connection is probed
        val report = probe()

        // then: the credential is reported as refused and the problem names it
        assertThat(report.credentialAccepted).describedAs("a 401 means the credential was refused").isFalse
        assertThat(report.problem).describedAs("the problem names the credential").contains("credential")
    }

    @Test
    fun `should report that settings are unreadable when the settings answer 403`() {
        // given: the list is readable, the settings are forbidden
        nexus.on(NexusClient.STATUS_PATH) { FakeReply.status(200) }
        nexus.serveFixture(NexusClient.REPOSITORIES_PATH, "repositories.json")
        nexus.on("/service/rest/v1/repositories/maven/hosted/releases") { FakeReply.status(403) }

        // when: the connection is probed
        val report = probe()

        // then: the probe still succeeds, and the settings are marked unreadable instead of failing the probe
        assertThat(report.credentialAccepted).isTrue
        assertThat(report.canReadSettings).describedAs("a 403 on settings is a result, not an error").isFalse
        assertThat(report.problem).isNull()
    }

    @Test
    fun `should warn when the connection uses plain http`() {
        // given: a healthy Nexus on plain http, which the fake always is
        answerHealthy()

        // when: the connection is probed
        val report = probe()

        // then: the password would cross the network in the clear, so the report says so
        assertThat(report.warnings).describedAs("plain http must be reported").anySatisfy { warning ->
            assertThat(warning).contains("plain http")
        }
    }

    @Test
    fun `should never contain the password in the report`() {
        // given: a Nexus that refuses the credential, so the password was sent and refused
        nexus.on(NexusClient.STATUS_PATH) { FakeReply.status(200) }
        nexus.on(NexusClient.REPOSITORIES_PATH) { FakeReply.status(401) }

        // when: the connection is probed
        val report = probe()

        // then: neither the report nor its text form holds the canary value
        assertThat(report.toString()).describedAs("the report must not carry the password").doesNotContain(canary)
        assertThat(report.problem.orEmpty()).doesNotContain(canary)
        assertThat(report.warnings.joinToString()).doesNotContain(canary)
    }

    @Test
    fun `should report unreachable when nothing answers on the status endpoint`() {
        // given: the status endpoint is not served, so the fake answers 404
        // when: the connection is probed
        val report = probe()

        // then: the probe reports the Nexus as not reachable with a problem and does not throw
        assertThat(report.reachable).describedAs("a 404 on status is not a healthy Nexus").isFalse
        assertThat(report.problem).isNotNull
    }
}
