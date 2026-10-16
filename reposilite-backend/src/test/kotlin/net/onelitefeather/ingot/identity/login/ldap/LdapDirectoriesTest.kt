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

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

/** Which directory serves the dashboard login: login.ldap when it is on, else the legacy authentication.ldap, else none. */
internal class LdapDirectoriesTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    private lateinit var directory: InMemoryDirectory

    @BeforeEach
    fun startDirectory() {
        directory = InMemoryDirectory()
    }

    @AfterEach
    fun stopDirectory() {
        directory.close()
    }

    private fun withEnvironment(legacyEnabled: Boolean, block: (LegacyLdapEnvironment) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            block(LegacyLdapEnvironment(directory, connection.database, clock, legacyEnabled))
        }
    }

    private fun usable() = LdapLoginSettings(enabled = true, url = "ldap://127.0.0.1:1", userBaseDn = "ou=people,dc=example,dc=org")

    @Test
    fun `login ldap with all required fields serves the login`() {
        withEnvironment(legacyEnabled = true) { environment ->
            assertThat(environment.directories(usable()).provider()).isInstanceOf(LdapLoginProvider::class.java)
        }
    }

    @Test
    fun `the legacy settings serve the login while login ldap is off`() {
        withEnvironment(legacyEnabled = true) { environment ->
            assertThat(environment.directories(usable().copy(enabled = false)).provider()).isInstanceOf(LegacyLdapLoginProvider::class.java)
        }
    }

    @Test
    fun `no directory serves the login when both are off`() {
        withEnvironment(legacyEnabled = false) { environment ->
            val directories = environment.directories(LdapLoginSettings())

            assertThat(directories.provider()).isNull()
            assertThat(directories.announcement()).describedAs("nothing is announced either").isNull()
        }
    }

    @Test
    fun `login ldap that is on but incomplete does not fall back to the legacy settings`() {
        withEnvironment(legacyEnabled = true) { environment ->
            val directories = environment.directories(usable().copy(url = ""))

            assertThat(directories.provider()).describedAs("misconfigured login.ldap must not hand the login to authentication.ldap").isNull()
            assertThat(directories.announcement()).isNull()
        }
    }

}
