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

package net.onelitefeather.ingot.identity.login.mfa

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import net.onelitefeather.ingot.identity.login.LoginAttemptLimiter
import net.onelitefeather.ingot.identity.login.storage.FactorRow
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore.Companion.KIND_TOTP
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.storage.IdentityStore

/** The name an authenticator enrolment carries until the user renames it. */
internal const val AUTHENTICATOR_NAME = "Authenticator app"

/** Shown to the user as the account of the authenticator. */
internal const val AUTHENTICATOR_ISSUER = "Ingot"

/** A sign-in challenge lives this long, and is spent by one successful completion. */
internal val CHALLENGE_LIFETIME: Duration = Duration.ofMinutes(5)

/** The result of starting an authenticator enrolment. */
sealed interface TotpEnrolmentStart {

    /** The seed is returned once, in [otpauthUri]. It is never returned again: only its sealed form is stored. */
    class Started(val seed: ByteArray, val otpauthUri: String) : TotpEnrolmentStart

    /** Authenticator codes need the settings key, because the seed must be stored reversibly. [reason] says what to set. */
    data class Unavailable(val reason: String) : TotpEnrolmentStart

}

sealed interface EnrolmentConfirmation {

    /** The factor exists now. [recoveryCodes] is set only when this is the first factor, which is when the codes are issued. */
    data class Confirmed(val recoveryCodes: List<String>?) : EnrolmentConfirmation

    /** No enrolment waits for a code, so there is nothing to confirm. */
    data object NoPendingEnrolment : EnrolmentConfirmation

    data object WrongCode : EnrolmentConfirmation

    data class Blocked(val retryAfterSeconds: Long) : EnrolmentConfirmation

}

/** The result of checking a code that the user typed, for a sign-in or to authorize a change. */
sealed interface CodeCheck {

    data class Accepted(val recovery: Boolean) : CodeCheck

    data object Rejected : CodeCheck

    data class Blocked(val retryAfterSeconds: Long) : CodeCheck

}

sealed interface ChallengeCompletion {

    data class Verified(val principal: PrincipalRecord, val recoveryCodeUsed: Boolean) : ChallengeCompletion

    data object WrongCode : ChallengeCompletion

    /** The challenge is unknown, expired, spent, or its user is disabled. The reason is not told to the client. */
    data object Gone : ChallengeCompletion

    data class Blocked(val retryAfterSeconds: Long) : ChallengeCompletion

}

sealed interface FactorRemoval {

    data object Removed : FactorRemoval

    data object NotFound : FactorRemoval

    data object WrongCode : FactorRemoval

    data class Blocked(val retryAfterSeconds: Long) : FactorRemoval

}

sealed interface RecoveryCodesOutcome {

    data class Regenerated(val codes: List<String>) : RecoveryCodesOutcome

    /** Recovery codes belong to a second factor, so a user without one has none to regenerate. */
    data object NoFactor : RecoveryCodesOutcome

    data object WrongCode : RecoveryCodesOutcome

    data class Blocked(val retryAfterSeconds: Long) : RecoveryCodesOutcome

}

/** A factor as the user and the administrators see it. The seed and the codes are never part of it. */
data class FactorRecord(
    val id: Int,
    val kind: String,
    val name: String,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
)

/**
 * The second factor of browser sign-in: authenticator codes (TOTP) and recovery codes for local and LDAP accounts.
 *
 * A password login that succeeds for a user with a confirmed authenticator does not start a session. It starts a challenge
 * (see [startChallenge]), and the code completes it (see [completeChallenge]). Changes to the factors need a current code
 * of the user as well, so that a stolen session alone cannot remove the second factor.
 *
 * Failed codes count through [LoginAttemptLimiter] under keys of their own, so a password login that succeeds does not reset
 * them. Every state change is a database row, and every time-dependent decision reads the injected [clock].
 */
internal class SecondFactorService(
    private val store: SecondFactorStore,
    private val identities: IdentityStore,
    private val limiter: LoginAttemptLimiter,
    private val seeds: SeedCipher,
    private val clock: Clock,
    private val audit: (AuditEvent) -> Unit,
    private val random: SecureRandom = SecureRandom(),
    /**
     * Whether the principal has a local password. Only such accounts may enrol an authenticator in this version: a directory
     * account signs in through the directory's own Basic authentication too, which a factor does not cover yet.
     */
    private val hasLocalPassword: (Int) -> Boolean = { true },
) {

    /** Starts an enrolment. A previous enrolment that never got its code is replaced. */
    fun enrolmentStart(principalId: Int, account: String): TotpEnrolmentStart {
        if (!seeds.available) {
            return TotpEnrolmentStart.Unavailable(
                "Authenticator codes need the settings key: set INGOT_SETTINGS_KEY or INGOT_SETTINGS_KEY_FILE first. Passkeys and recovery codes need no key."
            )
        }
        if (!hasLocalPassword(principalId)) {
            return TotpEnrolmentStart.Unavailable(
                "Authenticator codes are offered for accounts with a local password in this version. Directory accounts follow in a later change."
            )
        }
        val seed = ByteArray(TotpAlgorithm.SEED_BYTES).also { random.nextBytes(it) }
        val factorKey = UUID.randomUUID().toString()
        store.deletePendingTotp(principalId)
        store.insertPendingTotp(principalId, factorKey, AUTHENTICATOR_NAME, seeds.seal(factorKey, seed), clock.instant())
        return TotpEnrolmentStart.Started(seed, TotpAlgorithm.otpauthUri(AUTHENTICATOR_ISSUER, account, seed))
    }

    /** Confirms the pending enrolment with a code of its authenticator. */
    fun confirmEnrolment(principalId: Int, account: String, code: String, ip: String): EnrolmentConfirmation {
        val pending = store.findPendingTotp(principalId) ?: return EnrolmentConfirmation.NoPendingEnrolment
        val keys = keysOf(account, ip)
        limiter.retryAfter(keys)?.let { wait ->
            audit(failure(account, ip, "enrolment"))
            return EnrolmentConfirmation.Blocked(ceilSeconds(wait))
        }
        val seed = runCatching { seeds.open(pending.factorKey, checkNotNull(pending.secret)) }.getOrNull()
            ?: return EnrolmentConfirmation.NoPendingEnrolment
        val now = clock.instant()
        val step = TotpAlgorithm.matchingStep(seed, code.trim(), now.epochSecond)
        if (step == null) {
            limiter.recordFailure(keys)
            audit(failure(account, ip, "enrolment"))
            return EnrolmentConfirmation.WrongCode
        }
        store.confirm(pending.id, now, step)
        val codes = if (store.hasRecoveryCodes(principalId)) null else newRecoveryCodes(principalId)
        audit(AuditEvent(action = AuditAction.MFA_FACTOR_ENROLLED, actor = AuditActor.named(account, ip), details = mapOf("principal" to account, "kind" to KIND_TOTP)))
        return EnrolmentConfirmation.Confirmed(codes)
    }

    /** Whether a password login of [principalId] needs a second factor before a session starts. */
    fun requiresSecondFactor(principalId: Int): Boolean =
        store.findFactors(principalId).isNotEmpty()

    /** The factors of a principal: names and kinds, never secrets. */
    fun factorsOf(principalId: Int): List<FactorRecord> =
        store.findFactors(principalId).map { it.toRecord() }

    /**
     * Starts a sign-in challenge after a password was accepted, and returns its id. The browser keeps the id in a cookie; only
     * its hash is stored.
     */
    fun startChallenge(principalId: Int): String {
        val now = clock.instant()
        val id = ByteArray(CHALLENGE_ID_BYTES).also { random.nextBytes(it) }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(id)
        store.insertChallenge(hashOf(encoded), principalId, now, now.plus(CHALLENGE_LIFETIME))
        return encoded
    }

    /** Completes a sign-in challenge with an authenticator code or a recovery code. */
    fun completeChallenge(challengeId: String, code: String, ip: String): ChallengeCompletion {
        val now = clock.instant()
        val idHash = hashOf(challengeId)
        val challenge = store.findChallenge(idHash)
            ?.takeIf { it.usedAt == null && it.expiresAt.isAfter(now) }
            ?: return ChallengeCompletion.Gone
        val principal = identities.findPrincipalById(challenge.principalId)?.takeIf { !it.disabled }
            ?: return ChallengeCompletion.Gone

        return when (val check = checkCode(principal.id, principal.name, code, ip, context = "sign-in")) {
            CodeCheck.Rejected -> ChallengeCompletion.WrongCode
            is CodeCheck.Blocked -> ChallengeCompletion.Blocked(check.retryAfterSeconds)
            is CodeCheck.Accepted ->
                if (store.claimChallenge(idHash, now)) {
                    if (check.recovery) {
                        audit(AuditEvent(action = AuditAction.MFA_RECOVERY_CODE_USED, actor = AuditActor.named(principal.name, ip), details = mapOf("principal" to principal.name)))
                    }
                    ChallengeCompletion.Verified(principal, recoveryCodeUsed = check.recovery)
                } else {
                    ChallengeCompletion.Gone
                }
        }
    }

    /**
     * Removes one factor. The current code of the user must be given, so that a session alone cannot weaken the second factor.
     * Removing the last factor removes the recovery codes too.
     */
    fun removeFactor(principalId: Int, account: String, factorId: Int, code: String, ip: String, actor: AuditActor): FactorRemoval {
        if (store.findFactor(principalId, factorId) == null) {
            return FactorRemoval.NotFound
        }
        return when (val check = checkCode(principalId, account, code, ip, context = "change")) {
            CodeCheck.Rejected -> FactorRemoval.WrongCode
            is CodeCheck.Blocked -> FactorRemoval.Blocked(check.retryAfterSeconds)
            is CodeCheck.Accepted -> {
                store.deleteFactor(principalId, factorId)
                if (store.findFactors(principalId).isEmpty()) {
                    store.deleteRecoveryCodes(principalId)
                }
                audit(AuditEvent(action = AuditAction.MFA_FACTOR_REMOVED, actor = actor, details = mapOf("principal" to account, "factor" to factorId)))
                FactorRemoval.Removed
            }
        }
    }

    /** Replaces the recovery codes with a new set. The current code of the user must be given. */
    fun regenerateRecoveryCodes(principalId: Int, account: String, code: String, ip: String, actor: AuditActor): RecoveryCodesOutcome {
        if (store.findFactors(principalId).isEmpty()) {
            return RecoveryCodesOutcome.NoFactor
        }
        return when (val check = checkCode(principalId, account, code, ip, context = "change")) {
            CodeCheck.Rejected -> RecoveryCodesOutcome.WrongCode
            is CodeCheck.Blocked -> RecoveryCodesOutcome.Blocked(check.retryAfterSeconds)
            is CodeCheck.Accepted -> {
                val codes = newRecoveryCodes(principalId)
                audit(AuditEvent(action = AuditAction.MFA_RECOVERY_CODES_REGENERATED, actor = actor, details = mapOf("principal" to account)))
                RecoveryCodesOutcome.Regenerated(codes)
            }
        }
    }

    /**
     * An administrator removes the factors of a user: one of them when [factorId] is given, all of them otherwise. Returns
     * how many confirmed factors were removed. The caller ends the sessions of the user.
     */
    fun resetFactors(principalId: Int, account: String, factorId: Int?, actor: AuditActor): Int {
        val removed = if (factorId == null) {
            store.deleteAllFactors(principalId)
        } else {
            if (store.deleteFactor(principalId, factorId)) 1 else 0
        }
        if (store.findFactors(principalId).isEmpty()) {
            store.deletePendingTotp(principalId)
            store.deleteRecoveryCodes(principalId)
        }
        if (removed > 0) {
            audit(AuditEvent(action = AuditAction.MFA_FACTORS_RESET, actor = actor, details = mapOf("principal" to account, "removed" to removed)))
        }
        return removed
    }

    /**
     * Checks a code for [principalId]: six digits are an authenticator code, anything else a recovery code. A rejected code
     * counts as a failure and is audited without the code. A blocked attempt is refused before any code is looked at.
     */
    private fun checkCode(principalId: Int, account: String, code: String, ip: String, context: String): CodeCheck {
        val keys = keysOf(account, ip)
        limiter.retryAfter(keys)?.let { wait ->
            audit(failure(account, ip, context))
            return CodeCheck.Blocked(ceilSeconds(wait))
        }
        val accepted = if (isSixDigits(code.trim())) {
            acceptAuthenticatorCode(principalId, code.trim())
        } else {
            acceptRecoveryCode(principalId, code)
        }
        if (accepted == null) {
            limiter.recordFailure(keys)
            audit(failure(account, ip, context))
            return CodeCheck.Rejected
        }
        return accepted
    }

    private fun acceptAuthenticatorCode(principalId: Int, code: String): CodeCheck? {
        val now = clock.instant()
        for (factor in store.findFactors(principalId).filter { it.kind == KIND_TOTP }) {
            val seed = runCatching { seeds.open(factor.factorKey, checkNotNull(factor.secret)) }.getOrNull() ?: continue
            val step = TotpAlgorithm.matchingStep(seed, code, now.epochSecond) ?: continue
            // The code matched this factor. It is accepted only if no code of the same or a later step was accepted before.
            return if (store.claimTotpStep(factor.id, step, now)) CodeCheck.Accepted(recovery = false) else null
        }
        return null
    }

    private fun acceptRecoveryCode(principalId: Int, code: String): CodeCheck? {
        val spent = store.claimRecoveryCode(principalId, RecoveryCodes.hash(code), clock.instant())
        return if (spent) CodeCheck.Accepted(recovery = true) else null
    }

    private fun newRecoveryCodes(principalId: Int): List<String> {
        val codes = RecoveryCodes.generate(random)
        store.replaceRecoveryCodes(principalId, codes.map { RecoveryCodes.hash(it) }, clock.instant())
        return codes
    }

    private fun failure(account: String, ip: String, context: String): AuditEvent =
        AuditEvent(
            action = AuditAction.MFA_CHALLENGE_FAILED,
            actor = AuditActor.named(account, ip),
            details = mapOf("context" to context),
            success = false,
        )

    private fun keysOf(account: String, ip: String): List<String> =
        listOf(LoginAttemptLimiter.mfaUserKey(account, ip), LoginAttemptLimiter.mfaAddressKey(ip))

    private fun isSixDigits(value: String): Boolean =
        value.length == TotpAlgorithm.DIGITS && value.all { it in '0'..'9' }

    private fun hashOf(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }

    private fun FactorRow.toRecord() = FactorRecord(id = id, kind = kind, name = name, createdAt = createdAt, lastUsedAt = lastUsedAt)

    /** Whole seconds to wait, rounded up, and at least one. */
    private fun ceilSeconds(wait: Duration): Long =
        ((wait.toMillis() + 999) / 1000).coerceAtLeast(1)

    private companion object {
        const val CHALLENGE_ID_BYTES = 32
    }

}
