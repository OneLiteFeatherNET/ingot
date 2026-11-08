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

package net.onelitefeather.ingot.web

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.LoginAttemptLimiter
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Properties

internal class TrustedProxiesTest {

    private val proxies = TrustedProxies.parse("10.0.0.0/8, 192.168.1.5, fd00::/8, 2001:db8::1")

    @Test
    fun `without trusted proxies the forwarded header is taken as it comes`() {
        assertThat(TrustedProxies.NONE.resolve("203.0.113.9", "1.1.1.1, 2.2.2.2")).isEqualTo("1.1.1.1, 2.2.2.2")
    }

    @Test
    fun `without trusted proxies the peer is used when there is no header`() {
        assertThat(TrustedProxies.NONE.resolve("203.0.113.9", null)).isEqualTo("203.0.113.9")
    }

    @Test
    fun `an empty setting trusts nobody`() {
        assertThat(TrustedProxies.parse("  ").enabled).isFalse()
    }

    @Test
    fun `ignores the forwarded header when the peer is not a trusted proxy`() {
        assertThat(proxies.resolve("203.0.113.9", "198.51.100.1")).isEqualTo("203.0.113.9")
    }

    @Test
    fun `uses the rightmost address that is not a trusted proxy`() {
        assertThat(proxies.resolve("10.1.2.3", "6.6.6.6, 198.51.100.1, 10.9.9.9")).isEqualTo("198.51.100.1")
    }

    @Test
    fun `ignores what a client prepends to the chain`() {
        val first = proxies.resolve("10.1.2.3", "1.1.1.1, 198.51.100.1")
        val second = proxies.resolve("10.1.2.3", "2.2.2.2, 198.51.100.1")

        assertThat(first).isEqualTo("198.51.100.1")
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `falls back to the peer when the trusted peer sends no header`() {
        assertThat(proxies.resolve("10.1.2.3", null)).isEqualTo("10.1.2.3")
    }

    @Test
    fun `falls back to the leftmost address when the whole chain is trusted`() {
        assertThat(proxies.resolve("10.1.2.3", "10.4.4.4, 192.168.1.5")).isEqualTo("10.4.4.4")
    }

    @Test
    fun `matches a single IPv4 address exactly`() {
        assertThat(proxies.resolve("192.168.1.5", "198.51.100.1")).isEqualTo("198.51.100.1")
        assertThat(proxies.resolve("192.168.1.6", "198.51.100.1")).isEqualTo("192.168.1.6")
    }

    @Test
    fun `matches IPv6 addresses and ranges`() {
        assertThat(proxies.resolve("2001:db8::1", "198.51.100.1")).isEqualTo("198.51.100.1")
        assertThat(proxies.resolve("fd12:3456::7", "2001:db8:ffff::9")).isEqualTo("2001:db8:ffff::9")
        assertThat(proxies.resolve("2001:db8::2", "198.51.100.1")).isEqualTo("2001:db8::2")
    }

    @Test
    fun `matches a CIDR range that does not end on a byte boundary`() {
        val range = TrustedProxies.parse("172.16.0.0/12")

        assertThat(range.resolve("172.31.255.255", "198.51.100.1")).isEqualTo("198.51.100.1")
        assertThat(range.resolve("172.32.0.1", "198.51.100.1")).isEqualTo("172.32.0.1")
    }

    @Test
    fun `never matches an address of the other family`() {
        assertThat(TrustedProxies.parse("0.0.0.0/0").resolve("2001:db8::1", "198.51.100.1")).isEqualTo("2001:db8::1")
    }

    @Test
    fun `treats an unparsable address in the chain as the client`() {
        assertThat(proxies.resolve("10.1.2.3", "10.0.0.1, unknown")).isEqualTo("unknown")
    }

    @Test
    fun `fails the start on a malformed address and names it`() {
        assertThatThrownBy { TrustedProxies.parse("10.0.0.1, proxy.example.com") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("proxy.example.com")
            .hasMessageContaining("INGOT_TRUSTED_PROXIES")
    }

    @Test
    fun `fails the start on a prefix that is out of range`() {
        assertThatThrownBy { TrustedProxies.parse("10.0.0.0/33") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("10.0.0.0/33")
        assertThatThrownBy { TrustedProxies.parse("fd00::/129") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrustedProxies.parse("10.0.0.0/x") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `reads the environment variable`() {
        val fromEnvironment = TrustedProxies.fromEnvironment(mapOf("INGOT_TRUSTED_PROXIES" to "10.0.0.0/8"), Properties())

        assertThat(fromEnvironment.enabled).isTrue()
    }

    @Test
    fun `lets the system property win over the environment variable`() {
        val properties = Properties().apply { setProperty("ingot.trustedProxies", "192.168.0.1") }
        val fromProperty = TrustedProxies.fromEnvironment(mapOf("INGOT_TRUSTED_PROXIES" to "10.0.0.0/8"), properties)

        assertThat(fromProperty.resolve("10.0.0.1", "198.51.100.1")).isEqualTo("10.0.0.1")
    }

    @Test
    fun `warns at startup when the forwarded header is used without trusted proxies`() {
        val warning = TrustedProxies.startupWarning(TrustedProxies.NONE, "X-Forwarded-For")

        assertThat(warning).contains("X-Forwarded-For").contains("INGOT_TRUSTED_PROXIES")
    }

    @Test
    fun `does not warn when proxies are trusted`() {
        assertThat(TrustedProxies.startupWarning(proxies, "X-Forwarded-For")).isNull()
    }

    @Test
    fun `does not warn when no forwarded header is configured`() {
        assertThat(TrustedProxies.startupWarning(TrustedProxies.NONE, "")).isNull()
    }

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `a client that varies its forwarded prefix cannot escape the lockout`() {
        val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val limiter = LoginAttemptLimiter(
                LoginStore(connection.database, clock),
                Reference.reference(LoginSettings(maxFailedAttempts = 3, lockoutMinutes = 15)),
                clock,
            )
            val attempts = listOf("1.1.1.1", "2.2.2.2", "3.3.3.3").map { prefix ->
                proxies.resolve("10.1.2.3", "$prefix, 198.51.100.1")
            }
            attempts.forEach { limiter.recordFailure(listOf(LoginAttemptLimiter.addressKey(it))) }

            assertThat(limiter.retryAfter(listOf(LoginAttemptLimiter.addressKey(attempts.last()))))
                .describedAs("three failures from one real client must lock it however the prefix varies")
                .isNotNull()
        }
    }

}
