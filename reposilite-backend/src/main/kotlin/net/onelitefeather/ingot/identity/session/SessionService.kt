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

package net.onelitefeather.ingot.identity.session

import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.storage.IdentityStore
import panda.std.reactive.Reference
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** Everything a client receives when a session starts or is renewed. */
data class SessionTokens(
    val principal: PrincipalRecord,
    val sessionJwt: String,
    val sessionExpiresAt: Instant,
    val sessionMaxAgeSeconds: Long,
    val refreshToken: String,
    val refreshExpiresAt: Instant,
    val refreshMaxAgeSeconds: Long,
    val csrfToken: String,
)

/**
 * Browser sessions: a short-lived signed session token plus a long-lived refresh token. The refresh token is single use:
 * exchanging it issues a new one in the same family. Presenting a token that was already exchanged is taken as theft, and
 * revokes the family. Logout revokes the family too. The session token itself is not looked up in the database; it expires
 * on its own, so a revocation takes effect at the latest when it does.
 */
class SessionService internal constructor(
    private val store: LoginStore,
    private val identities: IdentityStore,
    private val signer: SessionSigner,
    private val settings: Reference<LoginSettings>,
    private val clock: Clock,
) {

    fun start(principal: PrincipalRecord, ip: String?, userAgent: String?): SessionTokens =
        issue(principal, familyId = RandomTokens.generate(), ip = ip, userAgent = userAgent)

    /**
     * Exchanges a refresh token for a new session. Returns `null` for an unknown, expired, revoked or spent token, and for
     * a principal that is no longer an active user. A spent token revokes its whole family.
     */
    fun refresh(refreshToken: String, ip: String?, userAgent: String?): SessionTokens? {
        val now = Instant.now(clock)
        val presented = store.findRefreshToken(RandomTokens.hash(refreshToken)) ?: return null
        if (presented.revokedAt != null || !now.isBefore(presented.expiresAt)) {
            return null
        }
        if (presented.usedAt != null || !store.claimRefreshToken(presented.id, now)) {
            store.revokeFamily(presented.familyId, now)
            return null
        }
        val principal = activePrincipal(presented.principalId)
        if (principal == null) {
            store.revokeFamily(presented.familyId, now)
            return null
        }
        return issue(principal, familyId = presented.familyId, ip = ip, userAgent = userAgent)
    }

    /** Revokes the family of the refresh token. Unknown and missing tokens are ignored. */
    fun logout(refreshToken: String?) {
        if (refreshToken == null) {
            return
        }
        store.findRefreshToken(RandomTokens.hash(refreshToken))?.let {
            store.revokeFamily(it.familyId, Instant.now(clock))
        }
    }

    /** Ends every session of a principal, on every instance. Used when its password changes or is reset. */
    fun revokeAllSessions(principalId: Int) {
        store.revokeAllOfPrincipal(principalId, Instant.now(clock))
    }

    /** The verified claims of a session token whose principal is still an active user, or `null`. */
    fun authenticate(sessionJwt: String): VerifiedSession? {
        val verified = signer.verify(sessionJwt) ?: return null
        return verified.takeIf { activePrincipal(it.principalId) != null }
    }

    private fun issue(principal: PrincipalRecord, familyId: String, ip: String?, userAgent: String?): SessionTokens {
        val now = Instant.now(clock)
        val lifetimes = settings.get()
        val sessionLifetime = Duration.ofMinutes(lifetimes.sessionLifetimeMinutes)
        val refreshLifetime = Duration.ofHours(lifetimes.refreshLifetimeHours)

        val refreshToken = RandomTokens.generate()
        val refreshExpiresAt = now.plus(refreshLifetime)
        store.insertRefreshToken(
            familyId = familyId,
            principalId = principal.id,
            tokenHash = RandomTokens.hash(refreshToken),
            createdAt = now,
            expiresAt = refreshExpiresAt,
            userAgent = userAgent,
            ip = ip,
        )

        val sessionExpiresAt = now.plus(sessionLifetime)
        return SessionTokens(
            principal = principal,
            sessionJwt = signer.issue(principal.id, principal.name, principal.kind.name, familyId, sessionExpiresAt),
            sessionExpiresAt = sessionExpiresAt,
            sessionMaxAgeSeconds = sessionLifetime.seconds,
            refreshToken = refreshToken,
            refreshExpiresAt = refreshExpiresAt,
            refreshMaxAgeSeconds = refreshLifetime.seconds,
            csrfToken = RandomTokens.generate(),
        )
    }

    private fun activePrincipal(principalId: Int): PrincipalRecord? =
        identities.findPrincipalById(principalId)?.takeIf { it.kind == PrincipalKind.USER && !it.disabled }

}

/** Random tokens for refresh tokens, families and csrf tokens. Only the hash of a refresh token is ever stored. */
internal object RandomTokens {

    private val random = SecureRandom()

    fun generate(): String =
        ByteArray(TOKEN_BYTES).also { random.nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }

    private const val TOKEN_BYTES = 32

}
