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

package net.onelitefeather.ingot.identity.login.ldap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LdapConnectionCheckTest {

    private lateinit var directory: InMemoryDirectory

    @BeforeEach
    fun start() {
        directory = InMemoryDirectory()
    }

    @AfterEach
    fun stop() {
        directory.close()
    }

    private fun settings(bindPassword: String = InMemoryDirectory.SERVICE_PASSWORD) = LdapLoginSettings(
        enabled = true,
        url = directory.url,
        bindDn = InMemoryDirectory.SERVICE_DN,
        bindPassword = bindPassword,
        userBaseDn = "ou=people,${InMemoryDirectory.BASE_DN}",
        groupBaseDn = "ou=groups,${InMemoryDirectory.BASE_DN}",
    )

    @Test
    fun `the service bind alone is a successful check`() {
        val report = LdapConnectionCheck(settings()).check(username = null, password = null)

        assertThat(report.ok).isTrue
        assertThat(report.userDn).isNull()
    }

    @Test
    fun `a refused service bind is reported without the bind password`() {
        val report = LdapConnectionCheck(settings(bindPassword = "not-the-service-secret")).check(null, null)

        assertThat(report.ok).isFalse
        assertThat(report.error).contains("service bind")
        assertThat(report.toString()).doesNotContain("not-the-service-secret")
    }

    @Test
    fun `a user with the right password reports the entry and its groups`() {
        val report = LdapConnectionCheck(settings()).check(username = "alice", password = "alice-secret")

        assertThat(report.ok).isTrue
        assertThat(report.userDn).isEqualTo("uid=alice,ou=people,${InMemoryDirectory.BASE_DN}")
        assertThat(report.groups).containsExactly("devs")
    }

    @Test
    fun `a wrong password is reported and never echoed`() {
        val report = LdapConnectionCheck(settings()).check(username = "alice", password = "guess-password")

        assertThat(report.ok).isFalse
        assertThat(report.error).contains("refused")
        assertThat(report.toString()).doesNotContain("guess-password")
    }

    @Test
    fun `a user name without an entry is reported`() {
        val report = LdapConnectionCheck(settings()).check(username = "nobody", password = null)

        assertThat(report.ok).isFalse
        assertThat(report.error).contains("No entry")
    }

    @Test
    fun `a missing directory setting is reported by name`() {
        val report = LdapConnectionCheck(settings().copy(url = "")).check(null, null)

        assertThat(report.ok).isFalse
        assertThat(report.error).contains("url")
    }
}
