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

import com.reposilite.auth.api.Credentials
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.token.AccessTokenSecurityProvider
import net.onelitefeather.ingot.identity.login.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

/**
 * A directory password is refused for a token whose user has a second factor. The caller gets the same generic answer as
 * any other failed login, and the server log names the account and the reason, without the password.
 */
internal class DirectoryRefusalTest {

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

    private fun withEnvironment(block: (LegacyLdapEnvironment) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            block(LegacyLdapEnvironment(directory, connection.database, clock))
        }
    }

    /** A human with a second factor who owns a token named like the directory user. */
    private fun LegacyLdapEnvironment.humanWithFactorOwningToken(name: String) {
        val human = createHuman(name)
        identities.createToken(
            principalId = human.id,
            name = name,
            secretHash = AccessTokenSecurityProvider.encodeSecret("unused-secret", AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH),
            scopes = null,
            expiresAt = null,
            createdAt = LocalDate.of(2026, 10, 15),
            description = "",
        )
        accessTokens.secondFactorGuard = { principalId -> principalId == human.id }
    }

    @Test
    fun `a refused directory login answers with the generic message`() {
        withEnvironment { environment ->
            environment.humanWithFactorOwningToken("alice")

            val result = environment.authenticator.authenticate(Credentials(ip = "10.0.0.1", name = "alice", secret = "alice-secret"))

            assertThat(result.isErr).describedAs("the refused directory login").isTrue()
            assertThat(result.error.message).describedAs("the caller learns nothing about the factor").isEqualTo("Invalid authorization credentials")
        }
    }

    @Test
    fun `a refused directory login is logged with the account, its address and the reason`() {
        withEnvironment { environment ->
            environment.humanWithFactorOwningToken("alice")

            environment.authenticator.authenticate(Credentials(ip = "10.0.0.1", name = "alice", secret = "alice-secret"))

            val log = environment.logs.messages.joinToString("\n") { it.value }
            assertThat(log).describedAs("server log names the account and its address").contains("alice@10.0.0.1")
            assertThat(log).describedAs("server log names the reason").contains("a second factor is set up for this account")
        }
    }

    @Test
    fun `a refused directory login never logs the password`() {
        withEnvironment { environment ->
            environment.humanWithFactorOwningToken("alice")

            environment.authenticator.authenticate(Credentials(ip = "10.0.0.1", name = "alice", secret = "alice-secret"))

            val log = environment.logs.messages.joinToString("\n") { it.value }
            assertThat(log).describedAs("the directory password stays out of the log").doesNotContain("alice-secret")
        }
    }

}
