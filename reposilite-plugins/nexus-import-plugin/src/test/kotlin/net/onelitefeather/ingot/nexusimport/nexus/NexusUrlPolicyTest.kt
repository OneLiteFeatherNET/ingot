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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class NexusUrlPolicyTest {

    private val anyHost = NexusUrlPolicy(allowedHosts = null)

    @Test
    fun `should refuse a URL with user information because the secret would be stored in it`() {
        // when: the URL carries a user name and password
        val outcome = anyHost.check("https://importer:secret@nexus.example.test/")

        // then: the URL is refused with a bad request answer
        assertThat(outcome.refusal).describedAs("user information in the URL must be refused").isNotNull
        assertThat(outcome.refusal).contains("user information")
        assertThat(outcome.refusal.orEmpty()).doesNotContain("secret")
    }

    @Test
    fun `should accept an https URL without warnings`() {
        // when: a plain https URL is checked
        val outcome = anyHost.check("https://nexus.example.test/")

        // then: it is accepted and nothing is reported
        assertThat(outcome.refusal).isNull()
        assertThat(outcome.warnings).isEmpty()
    }

    @Test
    fun `should accept plain http with a warning`() {
        // when: a plain http URL is checked
        val outcome = anyHost.check("http://nexus.example.test/")

        // then: it is accepted and the warning names the cleartext password
        assertThat(outcome.refusal).isNull()
        assertThat(outcome.warnings).anySatisfy { assertThat(it).contains("plain http") }
    }

    @Test
    fun `should refuse a host that is not on the allow list`() {
        // given: an allow list with one host
        val policy = NexusUrlPolicy(allowedHosts = setOf("nexus.example.test"))

        // when: a different host is checked
        val outcome = policy.check("https://elsewhere.example.test/")

        // then: the host is refused
        assertThat(outcome.refusal).describedAs("a host outside the allow list must be refused").isNotNull
    }

    @Test
    fun `should accept an allowed host regardless of case`() {
        // given: an allow list in lower case
        val policy = NexusUrlPolicy(allowedHosts = setOf("nexus.example.test"))

        // when: the host is written in upper case
        val outcome = policy.check("https://NEXUS.example.test/")

        // then: it is accepted
        assertThat(outcome.refusal).isNull()
    }

    @Test
    fun `should refuse a scheme other than http or https`() {
        // when: a file URL is checked
        val outcome = anyHost.check("file:///etc/passwd")

        // then: it is refused
        assertThat(outcome.refusal).isNotNull
    }
}
