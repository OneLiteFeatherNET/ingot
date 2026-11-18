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

@file:OptIn(ExperimentalIngotPluginApi::class)

package net.onelitefeather.ingot.identity.login

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditEvent
import com.reposilite.auth.SignInObserver
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.LoginOutcome.Blocked
import net.onelitefeather.ingot.identity.login.LoginOutcome.ChallengeRequired
import net.onelitefeather.ingot.identity.login.LoginOutcome.Rejected
import net.onelitefeather.ingot.identity.login.LoginOutcome.Succeeded
import net.onelitefeather.ingot.identity.login.challenge.LoginChallengeAnswer
import net.onelitefeather.ingot.identity.login.challenge.LoginChallengeContext
import net.onelitefeather.ingot.identity.login.challenge.LoginChallengeProvider
import net.onelitefeather.ingot.identity.login.challenge.LoginChallenges
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Instant

/**
 * The login challenge of a delayed dashboard login. The per-address and per-user limits are set high unless a test is about
 * them, so that only the account key decides whether a name is delayed.
 */
internal class LoginServiceLoginChallengeTest {

    private val clock = MutableClock(Instant.parse("2026-11-18T10:00:00Z"))
    private val password = "correct horse battery"
    private val home = "10.0.0.1"
    private val elsewhere = "10.0.0.9"
    private val secretAnswer = "nonce-4711-secret"
    private val publicSeed = "seed-0815-public"

    @TempDir
    lateinit var workingDirectory: Path

    /** A provider whose answer is right when it carries [secretAnswer] as `nonce`. It counts how often it is asked. */
    private class CountingProvider(
        override val id: String = "example-pow",
        private val seed: String,
        private val expected: String,
        private val failure: RuntimeException? = null,
    ) : LoginChallengeProvider {
        var asked = 0
        override fun parameters(context: LoginChallengeContext): Map<String, Any?> {
            asked++
            failure?.let { throw it }
            return mapOf("seed" to seed, "difficulty" to 20)
        }
        override fun verify(context: LoginChallengeContext, answer: Map<String, Any?>): Boolean {
            asked++
            failure?.let { throw it }
            return answer["nonce"] == expected
        }
    }

    private class Fixture(
        val service: LoginService,
        val store: LoginStore,
        val audited: MutableList<AuditEvent>,
        val warnings: MutableList<String>,
    )

    private fun withFixture(
        provider: LoginChallengeProvider? = null,
        settings: LoginSettings = LoginSettings(maxFailedAttempts = 100, accountThrottle = AccountThrottleSettings(threshold = 3)),
        block: (Fixture) -> Unit,
    ) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val store = LoginStore(connection.database, clock)
            val identities = IdentityStore(connection.database, clock)
            val audited = mutableListOf<AuditEvent>()
            val warnings = mutableListOf<String>()
            val challenges = LoginChallenges(warn = { warnings += it })
            provider?.let { challenges.register(it) }
            val service = LoginService(
                identities = identities,
                credentials = store,
                hasher = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1)),
                settings = Reference.reference(settings),
                clock = clock,
                audit = { audited += it },
                accountThrottle = AccountThrottle(store, Reference.reference(settings), clock, enabled = true),
                signInObserver = SignInObserver(enabled = true, audit = { audited += it }, publish = {}, warn = { warnings += it }),
                challenges = challenges,
            )
            val principal = identities.createPrincipal(PrincipalKind.USER, "alice", "alice", false, clock.instant())!!
            service.setPassword(principal, password, mustChange = false)
            block(Fixture(service, store, audited, warnings))
        }
    }

    private fun Fixture.login(secret: String, ip: String = elsewhere, challenge: LoginChallengeAnswer? = null) =
        service.login(LocalLoginProvider.ID, "alice", secret, ip, userAgent = null, challenge = challenge)

    /** Three wrong passwords from the home address, so that the name is delayed from every other address. */
    private fun Fixture.delayAlice() {
        repeat(3) { login("wrong guess", ip = home) }
        audited.clear()
    }

    private fun answer(nonce: String = secretAnswer, provider: String = "example-pow") =
        LoginChallengeAnswer.Given(provider = provider, answer = mapOf("nonce" to nonce))

    @Test
    fun `without a provider a delayed login gets the plain delay`() {
        withFixture(provider = null) { fixture ->
            fixture.delayAlice()

            assertThat(fixture.login(password))
                .describedAs("no provider is registered, so the answer is the delay of the throttle alone")
                .isEqualTo(Blocked(retryAfterSeconds = 1))
        }
    }

    @Test
    fun `without a provider a challenge in the request changes nothing`() {
        withFixture(provider = null) { fixture ->
            fixture.delayAlice()

            val without = fixture.login(password)
            val with = fixture.login(password, challenge = answer())

            assertThat(with)
                .describedAs("a challenge sent while no provider is registered is ignored")
                .isEqualTo(without)
                .isEqualTo(Blocked(retryAfterSeconds = 1))
        }
    }

    @Test
    fun `with a provider a delayed login without a challenge is asked for one`() {
        val provider = CountingProvider(seed = publicSeed, expected = secretAnswer)
        withFixture(provider = provider) { fixture ->
            fixture.delayAlice()

            assertThat(fixture.login(password))
                .describedAs("the delayed name gets the challenge of the registered provider")
                .isEqualTo(ChallengeRequired(retryAfterSeconds = 1, provider = "example-pow", parameters = mapOf("seed" to publicSeed, "difficulty" to 20)))
        }
    }

    @Test
    fun `with a provider a login that is not delayed is not asked and its challenge is ignored`() {
        val provider = CountingProvider(seed = publicSeed, expected = secretAnswer)
        withFixture(provider = provider) { fixture ->
            assertThat(fixture.login(password, challenge = LoginChallengeAnswer.Malformed))
                .describedAs("a name that is not delayed is answered as without a challenge")
                .isInstanceOf(Succeeded::class.java)
            assertThat(provider.asked).describedAs("the provider is not asked for a name that is not delayed").isZero()
        }
    }

    @Test
    fun `a verified challenge lets one password check through`() {
        withFixture(provider = CountingProvider(seed = publicSeed, expected = secretAnswer)) { fixture ->
            fixture.delayAlice()

            assertThat(fixture.login(password, challenge = answer()))
                .describedAs("the right password after a verified challenge signs in")
                .isInstanceOf(Succeeded::class.java)
        }
    }

    @Test
    fun `a wrong password after a verified challenge counts as a failure and the delay continues`() {
        withFixture(provider = CountingProvider(seed = publicSeed, expected = secretAnswer)) { fixture ->
            fixture.delayAlice()

            assertThat(fixture.login("wrong guess", challenge = answer()))
                .describedAs("the password is checked once and is wrong")
                .isEqualTo(Rejected)
            assertThat(fixture.audited.map { it.action }).containsExactly(AuditAction.LOGIN_FAILED)
            assertThat(fixture.login(password))
                .describedAs("the next request is delayed again, longer, because the failure counted")
                .isEqualTo(ChallengeRequired(retryAfterSeconds = 2, provider = "example-pow", parameters = mapOf("seed" to publicSeed, "difficulty" to 20)))
        }
    }

    @Test
    fun `a wrong answer gets a fresh challenge and no failure is counted`() {
        withFixture(provider = CountingProvider(seed = publicSeed, expected = secretAnswer)) { fixture ->
            fixture.delayAlice()
            val before = fixture.store.findAttempt(LoginAttemptLimiter.accountKey("alice"))

            assertThat(fixture.login(password, challenge = answer(nonce = "wrong")))
                .describedAs("an answer the provider does not verify is answered with a fresh challenge")
                .isInstanceOf(ChallengeRequired::class.java)
            assertThat(fixture.store.findAttempt(LoginAttemptLimiter.accountKey("alice")))
                .describedAs("a rejected answer does not count as a failure of the name")
                .isEqualTo(before)
        }
    }

    @Test
    fun `a challenge for another provider gets a fresh challenge without a password check`() {
        withFixture(provider = CountingProvider(seed = publicSeed, expected = secretAnswer)) { fixture ->
            fixture.delayAlice()

            assertThat(fixture.login(password, challenge = answer(provider = "someone-else")))
                .describedAs("a foreign provider id is answered with a fresh challenge, the right password is not checked")
                .isInstanceOf(ChallengeRequired::class.java)
            assertThat(fixture.audited.map { it.action }).containsExactly(AuditAction.LOGIN_THROTTLED)
        }
    }

    @Test
    fun `a malformed challenge gets a fresh challenge without a password check`() {
        withFixture(provider = CountingProvider(seed = publicSeed, expected = secretAnswer)) { fixture ->
            fixture.delayAlice()

            assertThat(fixture.login(password, challenge = LoginChallengeAnswer.Malformed))
                .describedAs("a malformed challenge is answered with a fresh challenge, not with 400")
                .isInstanceOf(ChallengeRequired::class.java)
        }
    }

    @Test
    fun `a failing provider falls back to the plain delay and the failure is logged`() {
        val provider = CountingProvider(seed = publicSeed, expected = secretAnswer, failure = IllegalStateException("service down"))
        withFixture(provider = provider) { fixture ->
            fixture.delayAlice()

            assertThat(fixture.login(password, challenge = answer()))
                .describedAs("a provider that throws never lets the password through, the delay stays")
                .isEqualTo(Blocked(retryAfterSeconds = 1))
            assertThat(fixture.warnings).describedAs("the failure of the provider is logged").anyMatch { it.contains("example-pow") }
        }
    }

    @Test
    fun `the attempt limiter is not bypassed by a challenge the provider would verify`() {
        val provider = CountingProvider(seed = publicSeed, expected = secretAnswer)
        val strict = LoginSettings(maxFailedAttempts = 3, accountThrottle = AccountThrottleSettings(threshold = 3))
        withFixture(provider = provider, settings = strict) { fixture ->
            repeat(3) { fixture.login("wrong guess") }

            val outcome = fixture.login(password, challenge = answer())

            assertThat(outcome).describedAs("the attempt limiter keeps its plain 429").isInstanceOf(Blocked::class.java)
            assertThat(provider.asked).describedAs("the provider is not asked while the limiter delays").isZero()
        }
    }

    @Test
    fun `a challenge request is audited once as LOGIN_THROTTLED`() {
        withFixture(provider = CountingProvider(seed = publicSeed, expected = secretAnswer)) { fixture ->
            fixture.delayAlice()

            fixture.login(password, challenge = answer(nonce = "wrong"))

            assertThat(fixture.audited.map { it.action }).containsExactly(AuditAction.LOGIN_THROTTLED)
        }
    }

    @Test
    fun `neither the answer, the parameters nor the password are audited or logged`() {
        withFixture(provider = CountingProvider(seed = publicSeed, expected = secretAnswer)) { fixture ->
            fixture.delayAlice()
            fixture.login(password, challenge = answer(nonce = "wrong-$secretAnswer"))
            fixture.login(password)

            assertThat(fixture.audited.toString()).doesNotContain(secretAnswer, publicSeed, password)
            assertThat(fixture.warnings.joinToString()).doesNotContain(secretAnswer, publicSeed, password)
        }
    }

    @Test
    fun `a failing provider is logged without the text of its failure`() {
        val failing = CountingProvider(seed = publicSeed, expected = secretAnswer, failure = IllegalStateException("bad $secretAnswer $publicSeed"))
        withFixture(provider = failing) { fixture ->
            fixture.delayAlice()
            fixture.login(password, challenge = answer())

            assertThat(fixture.warnings).describedAs("the failure is logged").isNotEmpty()
            assertThat(fixture.warnings.joinToString())
                .describedAs("the log names the provider but not the text of its failure, which may carry the answer")
                .doesNotContain(secretAnswer, publicSeed, password)
        }
    }

}
