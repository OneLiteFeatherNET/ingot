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

package com.reposilite.auth

import com.reposilite.auth.api.Credentials
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.shared.ErrorResponse
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.application.AccessTokenComponents
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import java.time.Duration
import java.util.TreeSet

/**
 * The account guard of the Basic and Bearer path: a delayed name is answered before its secret is checked, a rejected
 * credential is counted, and a success is reported with its address. Without a guard the answers are the ones of today.
 */
internal class AuthenticationAccountGuardTest {

    private val journalist = InMemoryLogger()
    private val accessTokenFacade = AccessTokenComponents(journalist, database = null)
        .accessTokenFacade(bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH)
    private val authenticator = CountingAuthenticator(BasicAuthenticator(accessTokenFacade))
    private val facade = AuthenticationFacade(
        journalist = journalist,
        authenticators = TreeSet(Authenticator.priorityComparator).apply { add(authenticator) },
        accessTokenFacade = accessTokenFacade,
        extensions = Extensions(journalist),
    )

    private class CountingAuthenticator(private val delegate: Authenticator) : Authenticator {

        var calls = 0

        override fun authenticate(credentials: Credentials): Result<AccessTokenDto, ErrorResponse> {
            calls++
            return delegate.authenticate(credentials)
        }

        override fun enabled(): Boolean = true

        override fun priority(): Double = delegate.priority()

        override fun realm(): String = delegate.realm()

    }

    private class RecordingGuard(var wait: Duration? = null) : AccountLoginGuard {

        var checks = 0
        val failures = mutableListOf<String>()
        val successes = mutableListOf<Pair<String, String>>()

        override fun retryAfter(name: String, address: String): Duration? {
            checks++
            return wait
        }

        override fun recordFailure(name: String) {
            failures += name
        }

        override fun recordSuccess(name: String, address: String) {
            successes += name to address
        }

    }

    private fun createToken() {
        accessTokenFacade.createAccessToken(CreateAccessTokenRequest(type = TEMPORARY, name = "ci", secret = "real-secret"))
    }

    private fun credentials(secret: String, ip: String = "203.0.113.7") =
        Credentials(ip = ip, name = "ci", secret = secret)

    @Test
    fun `answers a delayed name with 429 before its secret is checked`() {
        createToken()
        facade.installAccountGuard(RecordingGuard(wait = Duration.ofSeconds(5)))
        var retryAfter: Long? = null

        val result = facade.authenticateByCredentials(credentials("real-secret")) { retryAfter = it }

        assertThat(result.isErr).isTrue()
        assertThat(result.error.status).isEqualTo(429)
        assertThat(authenticator.calls).describedAs("a delayed name must not reach the secret check").isZero()
        assertThat(retryAfter).isEqualTo(5L)
    }

    @Test
    fun `checks the secret of a name the guard does not delay`() {
        createToken()
        facade.installAccountGuard(RecordingGuard(wait = null))

        val result = facade.authenticateByCredentials(credentials("real-secret"))

        assertThat(result.isOk).isTrue()
        assertThat(authenticator.calls).isEqualTo(1)
    }

    @Test
    fun `counts a rejected credential and reports a success with its address`() {
        createToken()
        val guard = RecordingGuard()
        facade.installAccountGuard(guard)

        facade.authenticateByCredentials(credentials("guessed-secret"))
        facade.authenticateByCredentials(credentials("real-secret", ip = "198.51.100.4"))

        assertThat(guard.failures).containsExactly("ci")
        assertThat(guard.successes).containsExactly("ci" to "198.51.100.4")
    }

    @Test
    fun `does not consult the guard for the loopback mirror`() {
        createToken()
        val guard = RecordingGuard(wait = Duration.ofSeconds(5))
        facade.installAccountGuard(guard)

        val result = facade.authenticateByCredentials(credentials("real-secret"), guarded = false)

        assertThat(result.isOk).isTrue()
        assertThat(guard.checks).isZero()
        assertThat(authenticator.calls).isEqualTo(1)
    }

    @Test
    fun `without a guard every wrong secret is answered with 401 and checked`() {
        createToken()

        repeat(12) {
            val result = facade.authenticateByCredentials(credentials("guessed-secret"))
            assertThat(result.error.status).describedAs("no guard installed: the answer stays 401").isEqualTo(401)
        }

        assertThat(authenticator.calls).describedAs("every request reaches the secret check").isEqualTo(12)
    }

}
