package com.reposilite.auth

import com.reposilite.auth.api.Credentials
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenType
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.AccessTokenType.PERSISTENT
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.token.ExportService
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.infrastructure.InMemoryAccessTokenRepository
import net.onelitefeather.ingot.identity.evaluation.AccessSubjectSource
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The basic authenticator must spend exactly one secret verification on every login, whether the token name is
 * unknown, disabled, expired or known with a wrong or right secret, so that the response time does not reveal the
 * state of a name. Every fixture is built per test, and time is fixed.
 */
internal class BasicAuthenticatorTest {

    private val clock = MovableClock(Instant.parse("2026-06-01T00:00:00Z"))
    private val verifications = mutableListOf<String>()
    private val tokens = AccessTokenFacade(
        journalist = InMemoryLogger(),
        temporaryRepository = InMemoryAccessTokenRepository(clock),
        persistentRepository = InMemoryAccessTokenRepository(clock),
        exportService = ExportService(),
        subjectSource = DisabledSubjects,
        bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH,
        secretVerifier = { encodedSecret, rawSecret ->
            verifications += encodedSecret
            AccessTokenSecurityProvider.matches(encodedSecret, rawSecret)
        },
    )
    private val authenticator = BasicAuthenticator(tokens, clock)

    @Test
    fun `should verify exactly one secret for an unknown token name`() {
        // when: a name that no token has is presented
        authenticate("no-such-bot", "secret")

        // then: one verification ran
        assertThat(verifications).`as`("secret verifications").hasSize(1)
    }

    @Test
    fun `should reject an unknown token name with the invalid credentials error`() {
        // when: a name that no token has is presented
        val response = authenticate("no-such-bot", "secret")

        // then: the error is the generic one
        assertError(response)
        assertThat(response.getError().message).isEqualTo(INVALID_CREDENTIALS)
    }

    @Test
    fun `should verify exactly one secret for a disabled token`() {
        // given: a persistent token whose owner is disabled
        createToken(PERSISTENT, "disabled-bot", "secret")

        // when: the correct secret is presented
        authenticate("disabled-bot", "secret")

        // then: one verification ran
        assertThat(verifications).`as`("secret verifications").hasSize(1)
    }

    @Test
    fun `should reject a disabled token with the invalid credentials error`() {
        // given: a persistent token whose owner is disabled
        createToken(PERSISTENT, "disabled-bot", "secret")

        // when: the correct secret is presented
        val response = authenticate("disabled-bot", "secret")

        // then: the error is the generic one
        assertError(response)
        assertThat(response.getError().message).isEqualTo(INVALID_CREDENTIALS)
    }

    @Test
    fun `should verify exactly one secret for an expired token`() {
        // given: a token that expires in an hour, and time has moved past that
        createToken(TEMPORARY, "expired-bot", "secret", expiresAt = clock.now.plus(Duration.ofHours(1)))
        clock.now = clock.now.plus(Duration.ofHours(2))

        // when: the correct secret is presented
        authenticate("expired-bot", "secret")

        // then: one verification ran
        assertThat(verifications).`as`("secret verifications").hasSize(1)
    }

    @Test
    fun `should reject an expired token with the invalid credentials error`() {
        // given: a token that expires in an hour, and time has moved past that
        createToken(TEMPORARY, "expired-bot", "secret", expiresAt = clock.now.plus(Duration.ofHours(1)))
        clock.now = clock.now.plus(Duration.ofHours(2))

        // when: the correct secret is presented
        val response = authenticate("expired-bot", "secret")

        // then: the error is the generic one
        assertError(response)
        assertThat(response.getError().message).isEqualTo(INVALID_CREDENTIALS)
    }

    @Test
    fun `should verify exactly one secret for a wrong secret of an active token`() {
        // given: an active token
        createToken(TEMPORARY, "active-bot", "secret")

        // when: a wrong secret is presented
        authenticate("active-bot", "wrong")

        // then: one verification ran against the stored hash of that token
        assertThat(verifications).`as`("secret verifications").hasSize(1)
    }

    @Test
    fun `should authenticate an active token with its secret after exactly one verification`() {
        // given: an active token
        val token = createToken(TEMPORARY, "active-bot", "secret")

        // when: the correct secret is presented
        val response = authenticate("active-bot", "secret")

        // then: the token is returned and one verification ran
        assertOk(response)
        assertThat(response.get().identifier).`as`("authenticated token").isEqualTo(token.identifier)
        assertThat(verifications).`as`("secret verifications").hasSize(1)
    }

    private fun createToken(
        type: AccessTokenType,
        name: String,
        secret: String,
        expiresAt: Instant? = null,
    ): AccessTokenDto {
        tokens.createAccessToken(CreateAccessTokenRequest(type = type, name = name, secret = secret, expiresAt = expiresAt))
        return tokens.getAccessToken(name)!!
    }

    private fun authenticate(name: String, secret: String): Result<out AccessTokenDto, ErrorResponse> =
        authenticator.authenticate(Credentials("host", name, secret))

    /** Marks every persistent token as disabled, so that the disabled path can be reached without the identity tables. */
    private object DisabledSubjects : AccessSubjectSource {
        override fun subjectOf(identifier: AccessTokenIdentifier): AccessSubject =
            AccessSubject(
                principalId = null,
                principalName = null,
                kind = PrincipalKind.MACHINE,
                disabled = true,
                levels = emptyList(),
                tokenRules = emptyList(),
            )

        override fun subjectOfPrincipal(principalId: Int): AccessSubject? = null

        override fun principalOf(principalId: Int): PrincipalRecord? = null
    }

    private companion object {
        val INVALID_CREDENTIALS = "Invalid authorization credentials"
    }

}

/** A clock that only moves when a test sets [now], so that expiry needs no sleeping. */
private class MovableClock(var now: Instant) : Clock() {

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now

}
