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

import com.reposilite.configuration.shared.SecretReferenceResolver
import net.onelitefeather.ingot.nexusimport.fake.AdvancingClock
import net.onelitefeather.ingot.nexusimport.fake.FakeNexus
import net.onelitefeather.ingot.nexusimport.fake.FakeReply
import net.onelitefeather.ingot.nexusimport.nexus.JdkNexusTransport
import net.onelitefeather.ingot.nexusimport.nexus.NexusClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Base64

/** A password written as a reference is resolved by the existing resolver, and only when the operator allowed it. */
internal class ConnectionSecretReferenceTest {

    private val resolved = "resolved-password-5d2e"
    private lateinit var nexus: FakeNexus

    @BeforeEach
    fun setUp() {
        nexus = FakeNexus()
        nexus.on(NexusClient.STATUS_PATH) { FakeReply.status(200) }
        nexus.serveFixture(NexusClient.REPOSITORIES_PATH, "repositories.json")
    }

    @AfterEach
    fun tearDown() {
        nexus.close()
    }

    private fun service(resolver: SecretReferenceResolver?) = NexusConnectionService(
        settings = NexusImportSettings.read(emptyMap()),
        transport = JdkNexusTransport(),
        clock = AdvancingClock(),
        sleeper = { _: Duration -> },
        secretReferences = resolver,
    )

    private fun request(password: String) =
        ConnectionRequest(url = nexus.baseUri.toString(), user = "importer", password = password)

    @Test
    fun `should refuse a reference when the switch is off and send no request`() {
        // when: a reference is given with no resolver configured
        val result = service(resolver = null).test(request("\${env:NEXUS_PASSWORD}"))

        // then: the request is refused and Nexus sees nothing
        assertThat(result.isErr).isTrue
        assertThat(result.error.status).isEqualTo(400)
        assertThat(nexus.requests()).isEmpty()
    }

    @Test
    fun `should send the resolved value of an allowed environment reference`() {
        // given: a resolver that allows exactly one variable
        val resolver = SecretReferenceResolver(
            environment = mapOf("NEXUS_PASSWORD" to resolved),
            variables = setOf("NEXUS_PASSWORD"),
            directories = emptyList(),
        )

        // when: the connection test runs with the reference
        val result = service(resolver).test(request("\${env:NEXUS_PASSWORD}"))

        // then: the credential sent to Nexus is the resolved value
        assertThat(result.isOk).isTrue
        val header = nexus.requests().single { it.path == NexusClient.REPOSITORIES_PATH }.headers.getValue("authorization")
        assertThat(String(Base64.getDecoder().decode(header.removePrefix("Basic ")))).isEqualTo("importer:$resolved")
    }

    @Test
    fun `should refuse a variable that is not allowed and not echo its value`() {
        // given: a resolver that does not list the variable
        val resolver = SecretReferenceResolver(
            environment = mapOf("OTHER" to resolved),
            variables = emptySet(),
            directories = emptyList(),
        )

        // when: the reference names the unlisted variable
        val result = service(resolver).test(request("\${env:OTHER}"))

        // then: the request is refused, nothing is sent and the value is not in the answer
        assertThat(result.isErr).isTrue
        assertThat(result.error.message).doesNotContain(resolved)
        assertThat(nexus.requests()).isEmpty()
    }
}
