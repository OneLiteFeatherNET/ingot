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

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditEvent
import com.reposilite.auth.api.Credentials
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.EventListener
import com.reposilite.plugin.api.LoginThrottledEvent
import com.reposilite.plugin.api.NewSignInEvent
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.application.AccessTokenComponents
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.TreeSet

/**
 * The sign-in observation of the Basic, Bearer and console paths: a delayed request is audited once as `LOGIN_THROTTLED`,
 * counted and announced, while the switch is off the answers and the audit entries stay the ones of today.
 */
internal class AuthenticationSignInObservationTest {

    private val journalist = InMemoryLogger()
    private val audited = mutableListOf<AuditEvent>()
    private val throttledEvents = mutableListOf<LoginThrottledEvent>()
    private val newSignIns = mutableListOf<NewSignInEvent>()
    private val extensions = Extensions(journalist).also {
        it.registerEvent(AuditEvent::class.java, EventListener { event -> audited += event })
        it.registerEvent(LoginThrottledEvent::class.java, EventListener { event -> throttledEvents += event })
        it.registerEvent(NewSignInEvent::class.java, EventListener { event -> newSignIns += event })
    }
    private val accessTokenFacade = AccessTokenComponents(journalist, database = null)
        .accessTokenFacade(bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH)
    private val facade = AuthenticationFacade(
        journalist = journalist,
        authenticators = TreeSet(Authenticator.priorityComparator).apply {
            add(BasicAuthenticator(accessTokenFacade))
        },
        accessTokenFacade = accessTokenFacade,
        extensions = extensions,
    )

    private class DelayGuard(var wait: Duration? = null, var newAddress: Boolean = false) : AccountLoginGuard {

        override fun retryAfter(name: String, address: String): Duration? = wait

        override fun recordFailure(name: String) = Unit

        override fun recordSuccess(name: String, address: String) = Unit

        override fun isNewAddress(name: String, address: String): Boolean = newAddress

    }

    private fun observer(enabled: Boolean = true) =
        SignInObserver(enabled = enabled, audit = { extensions.emitEvent(it) }, publish = { extensions.emitEvent(it) }, warn = {})

    private fun createToken() {
        accessTokenFacade.createAccessToken(CreateAccessTokenRequest(type = TEMPORARY, name = "ci", secret = "real-secret"))
    }

    @Test
    fun `a delayed Basic request writes one LOGIN_THROTTLED for the basic path and no LOGIN_FAILED`() {
        createToken()
        facade.installSignInObserver(observer())
        facade.installAccountGuard(DelayGuard(wait = Duration.ofSeconds(5)))
        audited.clear()

        val result = facade.authenticateByCredentials(Credentials(ip = "203.0.113.7", name = "ci", secret = "guessed-secret"), path = SignInPath.BASIC)

        assertThat(result.error.status).isEqualTo(429)
        val event = audited.single()
        assertThat(event.action).isEqualTo(AuditAction.LOGIN_THROTTLED)
        assertThat(event.details).containsExactly(entry("path", "basic"))
        assertThat(audited.map { it.action }).doesNotContain(AuditAction.LOGIN_FAILED)
        assertThat(audited.toString()).doesNotContain("guessed-secret", "real-secret")
    }

    @Test
    fun `a delayed Bearer request is announced with the bearer path and the remaining delay`() {
        createToken()
        facade.installSignInObserver(observer())
        facade.installAccountGuard(DelayGuard(wait = Duration.ofMillis(2400)))

        facade.authenticateByCredentials(Credentials(ip = "203.0.113.7", name = "ci", secret = "real-secret"), path = SignInPath.BEARER)

        assertThat(throttledEvents).containsExactly(LoginThrottledEvent(name = "ci", address = "203.0.113.7", path = "bearer", retryAfterSeconds = 3))
    }

    @Test
    fun `a delayed request is answered 429 although the throttled listener fails`() {
        createToken()
        val failing = Extensions(journalist).also {
            it.registerEvent(LoginThrottledEvent::class.java, EventListener { error("listener failed") })
        }
        val guarded = AuthenticationFacade(
            journalist = journalist,
            authenticators = TreeSet(Authenticator.priorityComparator).apply { add(BasicAuthenticator(accessTokenFacade)) },
            accessTokenFacade = accessTokenFacade,
            extensions = failing,
        )
        guarded.installSignInObserver(SignInObserver(enabled = true, audit = {}, publish = { failing.emitEvent(it) }, warn = {}))
        guarded.installAccountGuard(DelayGuard(wait = Duration.ofSeconds(5)))

        val result = guarded.authenticateByCredentials(Credentials(ip = "203.0.113.7", name = "ci", secret = "real-secret"), path = SignInPath.BASIC)

        assertThat(result.error.status).isEqualTo(429)
    }

    @Test
    fun `with the observer off a delayed request keeps its LOGIN_FAILED entry and publishes nothing`() {
        createToken()
        facade.installSignInObserver(observer(enabled = false))
        facade.installAccountGuard(DelayGuard(wait = Duration.ofSeconds(5)))
        audited.clear()

        facade.authenticateByCredentials(Credentials(ip = "203.0.113.7", name = "ci", secret = "guessed-secret"), path = SignInPath.BASIC)

        assertThat(audited.map { it.action }).containsExactly(AuditAction.LOGIN_FAILED)
        assertThat(throttledEvents).isEmpty()
    }

    @Test
    fun `a success from a new address publishes NewSignInEvent and a success from a known one does not`() {
        createToken()
        facade.installSignInObserver(observer())
        val guard = DelayGuard(newAddress = true)
        facade.installAccountGuard(guard)

        facade.authenticateByCredentials(Credentials(ip = "198.51.100.4", name = "ci", secret = "real-secret"), path = SignInPath.BASIC)
        guard.newAddress = false
        facade.authenticateByCredentials(Credentials(ip = "198.51.100.4", name = "ci", secret = "real-secret"), path = SignInPath.BASIC)

        assertThat(newSignIns).hasSize(1)
        assertThat(newSignIns.single().path).isEqualTo("basic")
        assertThat(newSignIns.single().address).isEqualTo("198.51.100.4")
    }

    @Test
    fun `the loopback mirror is neither counted nor announced`() {
        createToken()
        val observer = observer()
        facade.installSignInObserver(observer)
        facade.installAccountGuard(DelayGuard(wait = Duration.ofSeconds(5), newAddress = true))

        facade.authenticateByCredentials(Credentials(ip = "loopback", name = "ci", secret = "real-secret"), guarded = false, path = SignInPath.BASIC)

        assertThat(observer.counters.count(SignInPath.BASIC, SignInOutcome.SUCCEEDED)).isZero()
        assertThat(newSignIns).isEmpty()
    }

    @Test
    fun `counts the outcome of every guarded Basic request on its path`() {
        createToken()
        val observer = observer()
        facade.installSignInObserver(observer)
        val guard = DelayGuard()
        facade.installAccountGuard(guard)

        facade.authenticateByCredentials(Credentials(ip = "203.0.113.7", name = "ci", secret = "real-secret"), path = SignInPath.BASIC)
        facade.authenticateByCredentials(Credentials(ip = "203.0.113.7", name = "ci", secret = "guessed-secret"), path = SignInPath.BASIC)
        guard.wait = Duration.ofSeconds(5)
        facade.authenticateByCredentials(Credentials(ip = "203.0.113.7", name = "ci", secret = "real-secret"), path = SignInPath.BASIC)

        assertThat(observer.counters.count(SignInPath.BASIC, SignInOutcome.SUCCEEDED)).isEqualTo(1)
        assertThat(observer.counters.count(SignInPath.BASIC, SignInOutcome.FAILED)).isEqualTo(1)
        assertThat(observer.counters.count(SignInPath.BASIC, SignInOutcome.THROTTLED)).isEqualTo(1)
    }

}
