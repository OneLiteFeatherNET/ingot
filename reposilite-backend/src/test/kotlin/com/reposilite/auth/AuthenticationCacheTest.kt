package com.reposilite.auth

import com.reposilite.audit.MutableClock
import com.reposilite.auth.api.Credentials
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.unauthorized
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenPermission.MANAGER
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
import java.time.Duration
import java.time.Instant

/**
 * A successful authentication is cached per credentials. Once the token behind it changes on this instance,
 * the cached credentials must stop working at once instead of after the cache expires. Every fixture is built
 * here, and time only moves when a test advances the clock.
 */
internal class AuthenticationCacheTest {

    private val clock = MutableClock()
    private val journalist = InMemoryLogger()
    private val tokens = AccessTokenFacade(
        journalist = journalist,
        temporaryRepository = InMemoryAccessTokenRepository(clock),
        persistentRepository = InMemoryAccessTokenRepository(clock),
        exportService = ExportService(),
    )
    private val spy = AuthenticatorSpy(BasicAuthenticator(tokens, clock))
    private val authentication = AuthenticationFacade(
        journalist = journalist,
        authenticators = sortedSetOf(Authenticator.priorityComparator, spy),
        accessTokenFacade = tokens,
        clock = clock,
    )

    @Test
    fun `should serve an unchanged credential from the cache`() {
        // given: a token that has been authenticated once
        createToken("deploy-bot", "secret")
        authenticate("deploy-bot", "secret")

        // when: the same credentials are presented again
        val response = authenticate("deploy-bot", "secret")

        // then: the request is authorized and the authenticator was consulted only once
        assertOk(response)
        assertThat(spy.calls).`as`("authenticator calls").isEqualTo(1)
    }

    @Test
    fun `should reject a cached credential once its token is revoked`() {
        // given: a token whose credentials are cached
        val token = createToken("deploy-bot", "secret")
        assertOk(authenticate("deploy-bot", "secret"))

        // when: the token is revoked and the same credentials are presented
        tokens.deleteToken(token.identifier)
        val response = authenticate("deploy-bot", "secret")

        // then: the credentials are rejected immediately
        assertError(unauthorized("Invalid authorization credentials"), response)
    }

    @Test
    fun `should reject the previous secret once the secret is regenerated`() {
        // given: a token whose old secret is cached
        val token = createToken("deploy-bot", "old-secret")
        assertOk(authenticate("deploy-bot", "old-secret"))

        // when: the secret is regenerated to a new value
        tokens.regenerateAccessToken(token, secret = "new-secret")

        // then: the old secret is rejected and the new one is accepted
        assertError(unauthorized("Invalid authorization credentials"), authenticate("deploy-bot", "old-secret"))
        assertOk(authenticate("deploy-bot", "new-secret"))
    }

    @Test
    fun `should reject the old name once the token is renamed`() {
        // given: a token whose credentials under its old name are cached
        val token = createToken("old-name", "secret")
        assertOk(authenticate("old-name", "secret"))

        // when: the token is renamed
        tokens.updateToken(token.copy(name = "new-name"))

        // then: the old name is rejected and the new name is accepted with the same secret
        assertError(unauthorized("Invalid authorization credentials"), authenticate("old-name", "secret"))
        assertOk(authenticate("new-name", "secret"))
    }

    @Test
    fun `should reject a cached credential once its token has expired`() {
        // given: a token that expires in one hour and whose credentials are cached
        createToken("short-lived", "secret", expiresAt = clock.instant().plus(Duration.ofHours(1)))
        assertOk(authenticate("short-lived", "secret"))

        // when: the clock passes the expiry of the token
        clock.advance(Duration.ofHours(2))
        val response = authenticate("short-lived", "secret")

        // then: the expired token is rejected even though its credentials are cached
        assertError(unauthorized("Invalid authorization credentials"), response)
    }

    @Test
    fun `should authenticate again once a changed permission is granted`() {
        // given: a token whose credentials are cached
        val token = createToken("deploy-bot", "secret")
        assertOk(authenticate("deploy-bot", "secret"))

        // when: a permission is granted and the same credentials are presented again
        tokens.addPermission(token.identifier, MANAGER)
        assertOk(authenticate("deploy-bot", "secret"))

        // then: the cached entry was dropped and the authenticator was consulted a second time
        assertThat(spy.calls).`as`("authenticator calls").isEqualTo(2)
    }

    @Test
    fun `should not cache a credential whose token was revoked while it was being checked`() {
        // given: a token that is revoked right after the authenticator has read it, during the first request
        val token = createToken("deploy-bot", "secret")
        spy.afterDelegate = {
            spy.afterDelegate = {}
            tokens.deleteToken(token.identifier)
        }

        // when: the first request finishes and the same credentials are presented again
        authenticate("deploy-bot", "secret")
        val response = authenticate("deploy-bot", "secret")

        // then: the result read before the revocation was not kept in the cache
        assertError(unauthorized("Invalid authorization credentials"), response)
    }

    private fun createToken(
        name: String,
        secret: String,
        expiresAt: Instant? = null,
    ): AccessTokenDto =
        tokens.createAccessToken(
            CreateAccessTokenRequest(type = TEMPORARY, name = name, secret = secret, expiresAt = expiresAt)
        ).accessToken

    private fun authenticate(name: String, secret: String): Result<out AccessTokenDto, ErrorResponse> =
        authentication.authenticateByCredentials(Credentials("host", name, secret))

}

/**
 * Counts the calls that reach the real authenticator and runs an optional hook right after it has read the token.
 */
private class AuthenticatorSpy(private val delegate: Authenticator) : Authenticator {

    var calls = 0
    var afterDelegate: () -> Unit = {}

    override fun authenticate(credentials: Credentials): Result<AccessTokenDto, ErrorResponse> {
        calls++
        return delegate.authenticate(credentials).also { afterDelegate() }
    }

    override fun enabled(): Boolean = delegate.enabled()

    override fun priority(): Double = delegate.priority()

    override fun realm(): String = delegate.realm()

}
