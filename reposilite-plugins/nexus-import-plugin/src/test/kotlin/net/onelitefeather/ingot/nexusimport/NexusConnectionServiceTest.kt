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

package net.onelitefeather.ingot.nexusimport

import net.onelitefeather.ingot.nexusimport.fake.AdvancingClock
import net.onelitefeather.ingot.nexusimport.fake.FakeNexus
import net.onelitefeather.ingot.nexusimport.fake.FakeReply
import net.onelitefeather.ingot.nexusimport.nexus.NexusClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

internal class NexusConnectionServiceTest {

    private val canary = "canary-password-91bc"
    private lateinit var nexus: FakeNexus

    @BeforeEach
    fun setUp() {
        nexus = FakeNexus()
    }

    @AfterEach
    fun tearDown() {
        nexus.close()
    }

    private fun service(allowedHosts: Set<String>? = null) = NexusConnectionService(
        settings = NexusImportSettings.read(emptyMap()).copy(allowedHosts = allowedHosts),
        transport = net.onelitefeather.ingot.nexusimport.nexus.JdkNexusTransport(),
        clock = AdvancingClock(),
        sleeper = { _: Duration -> },
    )

    @Test
    fun `should refuse a URL with user information before any request is sent`() {
        // given: a URL that embeds the credential
        val url = "http://importer:$canary@${nexus.baseUri.authority}/"

        // when: the connection test runs
        val result = service().test(ConnectionRequest(url = url, user = "importer", password = canary))

        // then: the request is refused with a bad request, nothing reached the fake, and the password is not echoed
        assertThat(result.isErr).describedAs("user information in the URL must be refused").isTrue
        assertThat(result.error.status).isEqualTo(400)
        assertThat(result.error.message.orEmpty()).doesNotContain(canary)
        assertThat(nexus.requests()).describedAs("no request may be sent for a refused URL").isEmpty()
    }

    @Test
    fun `should refuse a host that is not on the allow list before any request is sent`() {
        // given: an allow list that does not include the fake
        // when: the connection test runs against the fake
        val result = service(allowedHosts = setOf("nexus.example.test"))
            .test(ConnectionRequest(url = nexus.baseUri.toString(), user = "importer", password = canary))

        // then: the host is refused and the fake sees nothing
        assertThat(result.isErr).isTrue
        assertThat(nexus.requests()).isEmpty()
    }

    @Test
    fun `should return the probe report for a healthy Nexus`() {
        // given: a healthy Nexus
        nexus.on(NexusClient.STATUS_PATH) { FakeReply.status(200, mapOf("Server" to "Nexus/3.70.1-02 (OSS)")) }
        nexus.serveFixture(NexusClient.REPOSITORIES_PATH, "repositories.json")
        nexus.serveFixture("/service/rest/v1/repositories/maven/hosted/releases", "maven-hosted-releases.json")

        // when: the connection test runs
        val result = service().test(ConnectionRequest(url = nexus.baseUri.toString(), user = "importer", password = canary))

        // then: the report comes back as a success with the version and the readable settings
        assertThat(result.isOk).isTrue
        assertThat(result.get().version).isEqualTo("3.70.1-02")
        assertThat(result.get().canReadSettings).isTrue
        assertThat(result.get().toString()).doesNotContain(canary)
    }
}
