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

package net.onelitefeather.ingot.identity.login

import com.reposilite.audit.api.AuditActor
import com.reposilite.plugin.api.Facade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenType.PRINCIPAL
import net.onelitefeather.ingot.identity.api.TokenDto
import net.onelitefeather.ingot.identity.login.api.SelfTokenRequest
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.login.mfa.ChallengeCompletion
import net.onelitefeather.ingot.identity.login.mfa.EnrolmentConfirmation
import net.onelitefeather.ingot.identity.login.mfa.FactorRecord
import net.onelitefeather.ingot.identity.login.mfa.FactorRemoval
import net.onelitefeather.ingot.identity.login.mfa.RecoveryCodesOutcome
import net.onelitefeather.ingot.identity.login.mfa.SecondFactorService
import net.onelitefeather.ingot.identity.login.mfa.TotpEnrolmentStart
import net.onelitefeather.ingot.identity.session.SessionService
import net.onelitefeather.ingot.identity.session.SessionTokens

/**
 * What the rest of Ingot asks of human login, browser sessions and the credentials a user manages. Without a database
 * there are no users, no sessions and no tokens of users: every login answers [LoginOutcome.UnknownProvider], no provider
 * is announced and no cookie is accepted.
 */
class LoginFacade internal constructor(
    private val service: LoginService?,
    private val sessions: SessionService?,
    private val ownTokens: OwnTokens?,
    private val secondFactor: SecondFactorService? = null,
) : Facade {

    /** The providers the login page shows, see [LoginService.announcements]. */
    fun providers(): List<ProviderAnnouncement> =
        service?.announcements().orEmpty()

    fun setupRequired(): Boolean =
        service?.isSetupRequired() ?: false

    /** The setup code to show in the log, `null` when no setup is required. */
    fun issueSetupCode(): String? =
        service?.issueSetupCode()

    fun completeSetup(givenCode: String, username: String, password: String, displayName: String?, ip: String): SetupOutcome =
        service?.completeSetup(givenCode, username, password, displayName, ip) ?: SetupOutcome.NotRequired

    /**
     * A password login. When the password is right and the user has an authenticator, no session starts: the outcome is
     * [LoginOutcome.SecondFactorRequired], and [completeSecondFactor] finishes the sign-in.
     */
    fun login(providerId: String, username: String, password: String, ip: String): LoginOutcome {
        val outcome = service?.login(providerId, username, password, ip) ?: return LoginOutcome.UnknownProvider
        if (outcome !is LoginOutcome.Succeeded) {
            return outcome
        }
        val factors = secondFactor ?: return outcome
        if (!factors.requiresSecondFactor(outcome.principal.id)) {
            return outcome
        }
        return LoginOutcome.SecondFactorRequired(challenge = factors.startChallenge(outcome.principal.id))
    }

    /** Records the login of a user whose session started after the second factor, see [LoginService.recordLoginSucceeded]. */
    fun recordSessionIssued(principalName: String, ip: String) {
        service?.recordLoginSucceeded(principalName, ip)
    }

    /** Completes a sign-in that waits for its second factor. The caller starts the session only on [ChallengeCompletion.Verified]. */
    fun completeSecondFactor(challenge: String, code: String, ip: String): ChallengeCompletion =
        secondFactor?.completeChallenge(challenge, code, ip) ?: ChallengeCompletion.Gone

    /** Whether a principal has a second factor, which an external sign-in must not skip. */
    fun hasSecondFactor(principalId: Int): Boolean =
        secondFactor?.requiresSecondFactor(principalId) ?: false

    fun secondFactorsOf(principalId: Int): List<FactorRecord> =
        secondFactor?.factorsOf(principalId).orEmpty()

    /** The factors of a user, for an administrator. `null` when no user has that name. */
    fun secondFactorsOfPrincipal(principalName: String): List<FactorRecord>? {
        val principalId = service?.principalIdOf(principalName) ?: return null
        return secondFactorsOf(principalId)
    }

    /** Whether the user must choose a new password at the next sign-in. */
    fun mustChangePassword(principalId: Int): Boolean =
        service?.mustChangePassword(principalId) ?: false

    fun startAuthenticatorEnrolment(principalId: Int, account: String, code: String?, ip: String): TotpEnrolmentStart =
        secondFactor?.enrolmentStart(principalId, account, code, ip)
            ?: TotpEnrolmentStart.Unavailable("Second factors need the database")

    fun confirmAuthenticatorEnrolment(principalId: Int, account: String, code: String, ip: String): EnrolmentConfirmation =
        secondFactor?.confirmEnrolment(principalId, account, code, ip) ?: EnrolmentConfirmation.NoPendingEnrolment

    fun removeSecondFactor(principalId: Int, account: String, factorId: Int, code: String, ip: String, actor: AuditActor): FactorRemoval =
        secondFactor?.removeFactor(principalId, account, factorId, code, ip, actor) ?: FactorRemoval.NotFound

    fun regenerateRecoveryCodes(principalId: Int, account: String, code: String, ip: String, actor: AuditActor): RecoveryCodesOutcome =
        secondFactor?.regenerateRecoveryCodes(principalId, account, code, ip, actor) ?: RecoveryCodesOutcome.NoFactor

    /**
     * An administrator removes the factors of a user, all of them or the one [factorId] names. The sessions of the user end at
     * once, so that the next sign-in has to pass the factors that are left.
     */
    fun resetSecondFactors(principalName: String, factorId: Int?, actor: AuditActor): Int? {
        val principalId = service?.principalIdOf(principalName) ?: return null
        val removed = secondFactor?.resetFactors(principalId, principalName, factorId, actor) ?: 0
        sessions?.revokeAllSessions(principalId)
        return removed
    }

    /** Creates a human user with a local password. Returns `null` when the name is taken. */
    fun createUser(name: String, displayName: String, password: String, disabled: Boolean = false): PrincipalRecord? =
        service?.createUser(name, displayName, password, disabled)

    fun passwordProblem(password: String): String? =
        service?.passwordProblem(password)

    /**
     * The user changes their own password. Every session of the user ends, on every instance, so that the password change
     * is also a logout everywhere.
     */
    fun changeOwnPassword(principalId: Int, currentPassword: String, newPassword: String, actor: AuditActor): PasswordOutcome {
        val outcome = service?.changePassword(principalId, currentPassword, newPassword, actor) ?: PasswordOutcome.NotFound
        if (outcome == PasswordOutcome.Changed) {
            sessions?.revokeAllSessions(principalId)
        }
        return outcome
    }

    /** An administrator resets the password of a user. The user must change it at the next login, and every session ends. */
    fun resetPassword(principalName: String, newPassword: String, actor: AuditActor): PasswordOutcome {
        val principalId = service?.principalIdOf(principalName)
        val outcome = service?.resetPassword(principalName, newPassword, actor) ?: PasswordOutcome.NotFound
        if (outcome == PasswordOutcome.Changed && principalId != null) {
            sessions?.revokeAllSessions(principalId)
        }
        return outcome
    }

    /** A new browser session for a principal that has just logged in. `null` without a database. */
    fun startSession(principal: PrincipalRecord, ip: String?, userAgent: String?): SessionTokens? =
        sessions?.start(principal, ip, userAgent)

    /** Renews a session with its refresh token, see [SessionService.refresh]. `null` when the token is not usable. */
    fun refreshSession(refreshToken: String, ip: String?, userAgent: String?): SessionTokens? =
        sessions?.refresh(refreshToken, ip, userAgent)

    fun endSession(refreshToken: String?, ip: String?) {
        sessions?.logout(refreshToken, ip)
    }

    /** Refuses the session tokens of a family that another instance revoked, see [SessionService.denyFamily]. */
    internal fun denySessionFamily(familyId: String) {
        sessions?.denyFamily(familyId)
    }

    /**
     * The identity of the caller that presented a session token: the principal, addressed the way the authorization
     * facade resolves it. `null` when the token is not a valid, unexpired session of an active user.
     */
    fun authenticateSession(sessionJwt: String): AccessTokenIdentifier? =
        sessions?.authenticate(sessionJwt)?.let { AccessTokenIdentifier(type = PRINCIPAL, value = it.principalId) }

    fun listOwnTokens(principalId: Int): List<TokenDto> =
        ownTokens?.list(principalId).orEmpty()

    fun issueOwnToken(principalId: Int, actor: AuditActor, request: SelfTokenRequest): OwnTokenOutcome =
        ownTokens?.issue(principalId, actor, request) ?: OwnTokenOutcome.NotFound

    fun rotateOwnToken(principalId: Int, tokenName: String, actor: AuditActor): OwnTokenOutcome =
        ownTokens?.rotate(principalId, tokenName, actor) ?: OwnTokenOutcome.NotFound

    fun deleteOwnToken(principalId: Int, tokenName: String, actor: AuditActor): OwnTokenOutcome =
        ownTokens?.delete(principalId, tokenName, actor) ?: OwnTokenOutcome.NotFound

    /**
     * The name of the token that a Bearer secret names. `null` when it names none; the caller then authenticates with an
     * empty name, which fails and is counted like any other wrong secret.
     */
    fun bearerTokenName(secret: String): String? =
        ownTokens?.nameOfBearer(secret)

}
