package com.reposilite.auth

import com.reposilite.auth.api.Credentials
import com.reposilite.auth.application.BruteForceProtectionSettings
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.unauthorized
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.ExportService
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.infrastructure.InMemoryAccessTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import panda.std.reactive.toMutableReference
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The per-IP login ban is measured on the injected clock, from the failed attempt that reached the limit. Time only
 * moves when a test advances the clock, so no test waits for the ban to pass.
 */
internal class BruteForceBanWindowTest {

    private val clock = StepClock(Instant.parse("2000-01-01T00:00:00Z"))
    private val journalist = InMemoryLogger()
    private val tokens = AccessTokenFacade(
        journalist = journalist,
        temporaryRepository = InMemoryAccessTokenRepository(clock),
        persistentRepository = InMemoryAccessTokenRepository(clock),
        exportService = ExportService(),
        bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH,
    )
    private val hook = HookedAuthenticator(BasicAuthenticator(tokens, clock))
    private val settings = BruteForceProtectionSettings(enabled = true, maxAttempts = 3, banDurationSeconds = 300).toMutableReference()
    private val authentication = AuthenticationFacade(
        journalist = journalist,
        authenticators = sortedSetOf(Authenticator.priorityComparator, hook),
        accessTokenFacade = tokens,
        bruteForceProtectionSettings = settings,
        clock = clock,
    )

    @Test
    fun `should keep the ban for the full period counted from the attempt that reached the limit`() {
        // given: a token and an IP that fails slowly, one attempt every 200 seconds
        createToken("deploy-bot", "secret")
        repeat(2) {
            authenticate("slow-ip", "deploy-bot", "wrong-secret")
            clock.advance(Duration.ofSeconds(200))
        }

        // when: the third failure reaches the limit, and 299 seconds pass after it
        authenticate("slow-ip", "deploy-bot", "wrong-secret")
        clock.advance(Duration.ofSeconds(299))

        // then: the IP is still banned, although its first failure is more than the ban period ago
        assertError(unauthorized("Invalid authorization credentials"), authenticate("slow-ip", "deploy-bot", "secret"))
    }

    @Test
    fun `should lift the ban once the full period has passed since the attempt that reached the limit`() {
        // given: an IP that reached the limit slowly
        createToken("deploy-bot", "secret")
        repeat(2) {
            authenticate("slow-ip", "deploy-bot", "wrong-secret")
            clock.advance(Duration.ofSeconds(200))
        }
        authenticate("slow-ip", "deploy-bot", "wrong-secret")

        // when: the ban period has passed since the attempt that reached the limit
        clock.advance(Duration.ofSeconds(300))

        // then: the IP is accepted again
        assertOk(authenticate("slow-ip", "deploy-bot", "secret"))
    }

    @Test
    fun `should measure the ban with the injected clock instead of the system time`() {
        // given: an IP banned at a fixed instant far from the system time
        createToken("deploy-bot", "secret")
        repeat(3) { authenticate("clocked-ip", "deploy-bot", "wrong-secret") }

        // when: only the injected clock moves past the ban period, without any real waiting
        clock.advance(Duration.ofSeconds(300))

        // then: the ban is lifted according to the injected clock
        assertOk(authenticate("clocked-ip", "deploy-bot", "secret"))
    }

    @Test
    fun `should not lift an active ban when a valid login succeeds from the same address`() {
        // given: an IP whose failures reach the limit while a valid login from it is being checked
        createToken("deploy-bot", "secret")
        hook.afterDelegate = {
            hook.afterDelegate = {}
            repeat(3) { authenticate("shared-nat", "deploy-bot", "wrong-secret") }
        }
        assertOk(authenticate("shared-nat", "deploy-bot", "secret"))

        // when: the ban is still running and a valid login is presented from the same address
        clock.advance(Duration.ofSeconds(299))
        val response = authenticate("shared-nat", "deploy-bot", "secret")

        // then: the valid login is rejected, because the successful login did not clear the ban
        assertError(unauthorized("Invalid authorization credentials"), response)
    }

    @Test
    fun `should keep the failure record for the whole ban when the ban is longer than an hour`() {
        // given: a two hour ban and an IP that reached the limit
        settings.update(BruteForceProtectionSettings(enabled = true, maxAttempts = 3, banDurationSeconds = 7200))
        createToken("deploy-bot", "secret")
        repeat(3) { authenticate("long-ban-ip", "deploy-bot", "wrong-secret") }

        // when: 90 minutes pass, longer than the one hour lifetime of a failure record
        clock.advance(Duration.ofMinutes(90))

        // then: the IP is still banned
        assertError(unauthorized("Invalid authorization credentials"), authenticate("long-ban-ip", "deploy-bot", "secret"))
    }

    @Test
    fun `should keep the failure record for a ban that was lengthened after the record was written`() {
        // given: an IP that reached the limit under the default five minute ban
        createToken("deploy-bot", "secret")
        repeat(3) { authenticate("lengthened-ip", "deploy-bot", "wrong-secret") }

        // when: the ban is raised to two hours at runtime and 90 minutes pass
        settings.update(BruteForceProtectionSettings(enabled = true, maxAttempts = 3, banDurationSeconds = 7200))
        clock.advance(Duration.ofMinutes(90))

        // then: the IP is still banned for the lengthened period
        assertError(unauthorized("Invalid authorization credentials"), authenticate("lengthened-ip", "deploy-bot", "secret"))
    }

    private fun createToken(name: String, secret: String): AccessTokenDto =
        tokens.createAccessToken(
            CreateAccessTokenRequest(type = TEMPORARY, name = name, secret = secret, expiresAt = null)
        ).accessToken

    private fun authenticate(ip: String, name: String, secret: String): Result<out AccessTokenDto, ErrorResponse> =
        authentication.authenticateByCredentials(Credentials(ip, name, secret))

    /** A clock that only moves when a test advances it. */
    private class StepClock(private var now: Instant) : Clock() {

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = now

    }

    /** Counts the calls that reach the real authenticator and runs an optional hook right after it has answered. */
    private class HookedAuthenticator(private val delegate: Authenticator) : Authenticator {

        var afterDelegate: () -> Unit = {}

        override fun authenticate(credentials: Credentials): Result<AccessTokenDto, ErrorResponse> =
            delegate.authenticate(credentials).also { afterDelegate() }

        override fun enabled(): Boolean = delegate.enabled()

        override fun priority(): Double = delegate.priority()

        override fun realm(): String = delegate.realm()

    }

}
