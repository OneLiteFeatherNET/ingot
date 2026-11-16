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

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.plugin.api.Event
import com.reposilite.plugin.api.SessionFamilyRevokedEvent
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.login.storage.RefreshTokenRecord
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.storage.IdentityStore
import panda.std.reactive.Reference
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

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
    /** The last authentication of the family, the `ath` claim of [sessionJwt]. */
    val authenticatedAt: Instant? = null,
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
    private val audit: (AuditEvent) -> Unit,
    private val publish: (Event) -> Unit = {},
) {

    /** Families revoked elsewhere, each mapped to the instant until which its session tokens are refused here. */
    private val deniedFamilies = ConcurrentHashMap<String, Instant>()

    /**
     * Starts a family for a login. The authentication time is the clock now, and [mfa] tells whether a second factor completed the
     * login, see [LoginFacade.startSession].
     */
    fun start(principal: PrincipalRecord, ip: String?, userAgent: String?, mfa: Boolean = false): SessionTokens {
        val loginEnd = Instant.now(clock).plus(absoluteLifetime())
        return issue(
            principal,
            familyId = RandomTokens.generate(),
            ip = ip,
            userAgent = userAgent,
            familyEnd = loginEnd,
            authenticatedAt = authenticatedNow(),
            mfa = mfa,
        )
    }

    /**
     * Renews a session with a second factor that was just completed for it. The new session belongs to the same family, with the
     * authentication time now and the second factor set. The family keeps its absolute end. `null` when the family was revoked or
     * has ended, and when its principal is no longer active.
     */
    fun stepUp(session: VerifiedSession, ip: String?, userAgent: String?): SessionTokens? {
        val now = Instant.now(clock)
        if (store.familyRevoked(session.familyId)) {
            return null
        }
        val principal = activePrincipal(session.principalId) ?: return null
        val familyEnd = store.familyStartedAt(session.familyId)?.plus(absoluteLifetime()) ?: return null
        if (!now.isBefore(familyEnd)) {
            return null
        }
        return issue(
            principal,
            familyId = session.familyId,
            ip = ip,
            userAgent = userAgent,
            familyEnd = familyEnd,
            authenticatedAt = authenticatedNow(),
            mfa = true,
        )
    }

    /**
     * Exchanges a refresh token for a new session. Returns `null` for an unknown, expired, revoked or spent token, for a
     * family that has reached its absolute lifetime, and for a principal that is no longer active. A spent token
     * revokes its whole family, and so does a token that the idle timeout ended.
     */
    fun refresh(refreshToken: String, ip: String?, userAgent: String?): SessionTokens? {
        val now = Instant.now(clock)
        val presented = store.findRefreshToken(RandomTokens.hash(refreshToken)) ?: return null
        if (idleTimeoutPassed(presented, now)) {
            revokeFamily(presented.familyId, now)
            return null
        }
        if (presented.revokedAt != null || !now.isBefore(presented.expiresAt)) {
            return null
        }
        val familyEnd = familyEndOf(presented)
        if (!now.isBefore(familyEnd)) {
            revokeFamily(presented.familyId, now)
            return null
        }
        if (presented.usedAt != null || !store.claimRefreshToken(presented.id, now)) {
            revokeFamily(presented.familyId, now)
            audit(AuditEvent(action = AuditAction.SESSION_REFRESH_REUSE_DETECTED, actor = actorOf(presented.principalId, ip), success = false))
            return null
        }
        val principal = activePrincipal(presented.principalId)
        if (principal == null) {
            revokeFamily(presented.familyId, now)
            return null
        }
        // The renewal carries the authentication time and the second factor of the family, and does not extend the first.
        return issue(
            principal,
            familyId = presented.familyId,
            ip = ip,
            userAgent = userAgent,
            familyEnd = familyEnd,
            authenticatedAt = presented.authenticatedAt,
            mfa = presented.mfa,
        )
    }

    /** Revokes the family of the refresh token. Unknown and missing tokens are ignored and not audited. */
    fun logout(refreshToken: String?, ip: String? = null) {
        if (refreshToken == null) {
            return
        }
        val record = store.findRefreshToken(RandomTokens.hash(refreshToken)) ?: return
        revokeFamily(record.familyId, Instant.now(clock))
        audit(AuditEvent(action = AuditAction.LOGOUT, actor = actorOf(record.principalId, ip)))
    }

    /** Ends every session of a principal, on every instance. Used when its password changes or is reset. */
    fun revokeAllSessions(principalId: Int) {
        val families = store.activeFamilyIdsOfPrincipal(principalId)
        store.revokeAllOfPrincipal(principalId, Instant.now(clock))
        families.forEach { family ->
            denyFamily(family)
            publish(SessionFamilyRevokedEvent(family))
        }
    }

    /** Revokes a family, refuses its session tokens here at once, and announces it once the revocation is stored. */
    private fun revokeFamily(familyId: String, at: Instant) {
        store.revokeFamily(familyId, at)
        denyFamily(familyId)
        publish(SessionFamilyRevokedEvent(familyId))
    }

    /** The verified claims of a session token whose principal is still active, or `null`. */
    fun authenticate(sessionJwt: String): VerifiedSession? {
        val verified = signer.verify(sessionJwt) ?: return null
        if (isDenied(verified.familyId)) {
            return null
        }
        return verified.takeIf { activePrincipal(it.principalId) != null }
    }

    /**
     * Refuses the session tokens of a family that was revoked, here or on another instance. Only this instance's memory
     * changes: nothing is written to the database and nothing is published. An entry lasts one session lifetime from now.
     * A token of the family that exists now expires within that lifetime, and no token of a revoked family can be issued again.
     */
    internal fun denyFamily(familyId: String) {
        val now = Instant.now(clock)
        deniedFamilies.entries.removeIf { !now.isBefore(it.value) }
        deniedFamilies[familyId] = now.plus(Duration.ofMinutes(settings.get().sessionLifetimeMinutes))
    }

    private fun isDenied(familyId: String): Boolean {
        val until = deniedFamilies[familyId] ?: return false
        if (Instant.now(clock).isBefore(until)) {
            return true
        }
        deniedFamilies.remove(familyId, until)
        return false
    }

    private fun absoluteLifetime(): Duration =
        Duration.ofDays(settings.get().refreshAbsoluteDays)

    private fun familyEndOf(token: RefreshTokenRecord): Instant =
        store.familyStartedAt(token.familyId)?.plus(absoluteLifetime()) ?: token.createdAt.plus(absoluteLifetime())

    /**
     * True when the idle timeout ended the session: the current, unspent token of a live family has expired while the
     * absolute end of the login is still ahead. Always false with the timeout off, so the default writes nothing new.
     */
    private fun idleTimeoutPassed(token: RefreshTokenRecord, now: Instant): Boolean =
        settings.get().idleTimeoutMinutes > 0 &&
            token.revokedAt == null &&
            token.usedAt == null &&
            !now.isBefore(token.expiresAt) &&
            now.isBefore(familyEndOf(token))

    /**
     * Issues a session in [familyId]. The refresh token lives for the refresh lifetime, but never beyond [familyEnd], the
     * absolute end of the login. With an idle timeout set, it also ends the timeout after this issue.
     */
    private fun issue(
        principal: PrincipalRecord,
        familyId: String,
        ip: String?,
        userAgent: String?,
        familyEnd: Instant,
        authenticatedAt: Instant?,
        mfa: Boolean,
    ): SessionTokens {
        val now = Instant.now(clock)
        val lifetimes = settings.get()
        val sessionLifetime = Duration.ofMinutes(lifetimes.sessionLifetimeMinutes)
        val refreshLifetime = Duration.ofHours(lifetimes.refreshLifetimeHours)

        val refreshToken = RandomTokens.generate()
        val refreshEnd = minOf(now.plus(refreshLifetime), familyEnd)
        val refreshExpiresAt = if (lifetimes.idleTimeoutMinutes > 0) {
            minOf(refreshEnd, now.plus(Duration.ofMinutes(lifetimes.idleTimeoutMinutes)))
        } else {
            refreshEnd
        }
        store.insertRefreshToken(
            familyId = familyId,
            principalId = principal.id,
            tokenHash = RandomTokens.hash(refreshToken),
            createdAt = now,
            expiresAt = refreshExpiresAt,
            userAgent = userAgent,
            ip = ip,
            authenticatedAt = authenticatedAt,
            mfa = mfa,
        )

        val sessionExpiresAt = now.plus(sessionLifetime)
        return SessionTokens(
            principal = principal,
            sessionJwt = signer.issue(principal.id, principal.name, principal.kind.name, familyId, sessionExpiresAt, authenticatedAt, mfa),
            sessionExpiresAt = sessionExpiresAt,
            sessionMaxAgeSeconds = sessionLifetime.seconds,
            refreshToken = refreshToken,
            refreshExpiresAt = refreshExpiresAt,
            refreshMaxAgeSeconds = Duration.between(now, refreshExpiresAt).seconds,
            csrfToken = RandomTokens.generate(),
            authenticatedAt = authenticatedAt,
        )
    }

    /** The authentication time of a session that starts now, in whole seconds, as the `ath` claim carries it. */
    private fun authenticatedNow(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)

    /** The user the event is about, by name. The address is the one of the request. */
    private fun actorOf(principalId: Int, ip: String?): AuditActor =
        AuditActor.named(identities.findPrincipalById(principalId)?.name ?: AuditActor.ANONYMOUS, ip)

    /**
     * The principal of a session, if it may still hold one: a user or a machine principal that is not disabled. A machine
     * principal is allowed because the only producer of a machine login is the legacy LDAP provider, which signs in as the
     * principal that owns the token of the directory user. That is the identity of the Maven path, so its rights stay
     * those of 1.1.0.
     */
    private fun activePrincipal(principalId: Int): PrincipalRecord? =
        identities.findPrincipalById(principalId)?.takeIf { (it.kind == PrincipalKind.USER || it.kind == PrincipalKind.MACHINE) && !it.disabled }

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
