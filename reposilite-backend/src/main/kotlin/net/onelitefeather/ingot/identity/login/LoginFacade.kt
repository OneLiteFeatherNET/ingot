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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import net.onelitefeather.ingot.identity.api.TokenDto
import net.onelitefeather.ingot.identity.login.api.SelfTokenRequest
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.login.mfa.ChallengeCompletion
import net.onelitefeather.ingot.identity.login.mfa.EnrolmentConfirmation
import net.onelitefeather.ingot.identity.login.mfa.ExternalSecondFactor
import net.onelitefeather.ingot.identity.login.mfa.FactorRecord
import net.onelitefeather.ingot.identity.login.mfa.FactorRemoval
import net.onelitefeather.ingot.identity.login.mfa.FactorRename
import net.onelitefeather.ingot.identity.login.mfa.FirstFactorPasskey
import net.onelitefeather.ingot.identity.login.mfa.SecondFactorPolicy
import net.onelitefeather.ingot.identity.login.mfa.PasskeyChallenge
import net.onelitefeather.ingot.identity.login.mfa.PasskeyProof
import net.onelitefeather.ingot.identity.login.mfa.PasskeyRegistration
import net.onelitefeather.ingot.identity.login.mfa.PasskeyStart
import net.onelitefeather.ingot.identity.login.mfa.RecoveryCodesOutcome
import net.onelitefeather.ingot.identity.login.mfa.SecondFactorMethod
import net.onelitefeather.ingot.identity.login.mfa.SecondFactorService
import net.onelitefeather.ingot.identity.login.mfa.StepUpOutcome
import net.onelitefeather.ingot.identity.login.mfa.TotpEnrolmentStart
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore.Companion.KIND_PASSKEY
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore.Companion.KIND_TOTP
import net.onelitefeather.ingot.identity.session.SessionScope
import net.onelitefeather.ingot.identity.session.SessionService
import net.onelitefeather.ingot.identity.session.SessionTokens
import net.onelitefeather.ingot.identity.session.VerifiedSession
import net.onelitefeather.ingot.identity.session.stepUpAllows

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
    /** Called when a second factor is confirmed or reset, so that the cached credentials of the instance are checked again. */
    private val onSecondFactorChanged: () -> Unit = {},
    /** The clock of the step-up window, read at every sensitive action. */
    private val clock: Clock = Clock.systemUTC(),
    /** The second factor policy, which covers users at every sign-in. `null` leaves every user as before. */
    private val policy: SecondFactorPolicy? = null,
    /** The login settings, read on every use: the step-up window and the passkey first factor switch. */
    private val settings: () -> LoginSettings = { LoginSettings() },
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
    fun login(providerId: String, username: String, password: String, ip: String, userAgent: String? = null): LoginOutcome {
        val outcome = service?.login(providerId, username, password, ip, userAgent) ?: return LoginOutcome.UnknownProvider
        if (outcome !is LoginOutcome.Succeeded) {
            return outcome
        }
        // A covered user is recorded at every sign-in, so the grace period starts at the first one after the policy covers them.
        policy?.covers(outcome.principal)
        val factors = secondFactor ?: return outcome
        if (!factors.requiresSecondFactor(outcome.principal.id)) {
            return outcome
        }
        val challenge = factors.startChallenge(outcome.principal.id)
        return LoginOutcome.SecondFactorRequired(challenge = challenge, methods = factors.methodsOf(outcome.principal.id))
    }

    /** Records the login of a user whose session started after the second factor, see [LoginService.recordLoginSucceeded]. */
    fun recordSessionIssued(principalName: String, ip: String) {
        service?.recordLoginSucceeded(principalName, ip)
    }

    /** Completes a sign-in that waits for its second factor. The caller starts the session only on [ChallengeCompletion.Verified]. */
    fun completeSecondFactor(challenge: String, code: String, ip: String): ChallengeCompletion =
        secondFactor?.completeChallenge(challenge, code, ip) ?: ChallengeCompletion.Gone

    /** Starts the passkey step of the sign-in that [challenge] names, see [SecondFactorService.startPasskeyAssertion]. */
    fun startPasskeySignIn(challenge: String): PasskeyStart =
        secondFactor?.startPasskeyAssertion(challenge) ?: PasskeyStart.NoPasskey

    /** Completes the sign-in that [challenge] names with a passkey assertion, see [SecondFactorService.completePasskeyChallenge]. */
    fun completePasskeySecondFactor(challenge: String, proof: PasskeyProof, rpId: String, origin: String, ip: String): ChallengeCompletion =
        secondFactor?.completePasskeyChallenge(challenge, proof, rpId, origin, ip) ?: ChallengeCompletion.Gone

    /** Starts a passkey registration for a user, see [SecondFactorService.startPasskeyRegistration]. */
    fun startPasskeyRegistration(principalId: Int): PasskeyChallenge? =
        secondFactor?.startPasskeyRegistration(principalId)

    /** Completes a passkey registration for a user, see [SecondFactorService.finishPasskeyRegistration]. */
    fun finishPasskeyRegistration(
        principalId: Int,
        challengeId: String,
        clientDataJson: ByteArray,
        attestationObject: ByteArray,
        rpId: String,
        origin: String,
    ): PasskeyRegistration =
        secondFactor?.finishPasskeyRegistration(principalId, challengeId, clientDataJson, attestationObject, rpId, origin)
            ?: PasskeyRegistration.Refused

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

    fun confirmAuthenticatorEnrolment(principalId: Int, account: String, code: String, ip: String): EnrolmentConfirmation {
        val outcome = secondFactor?.confirmEnrolment(principalId, account, code, ip) ?: EnrolmentConfirmation.NoPendingEnrolment
        if (outcome is EnrolmentConfirmation.Confirmed) {
            onSecondFactorChanged()
        }
        return outcome
    }

    fun removeSecondFactor(principalId: Int, account: String, factorId: Int, code: String, ip: String, actor: AuditActor): FactorRemoval =
        secondFactor?.removeFactor(principalId, account, factorId, code, ip, actor) ?: FactorRemoval.NotFound

    fun renameSecondFactor(principalId: Int, account: String, factorId: Int, name: String, actor: AuditActor): FactorRename =
        secondFactor?.renameFactor(principalId, account, factorId, name, actor) ?: FactorRename.NotFound

    fun regenerateRecoveryCodes(principalId: Int, account: String, code: String, ip: String, actor: AuditActor): RecoveryCodesOutcome =
        secondFactor?.regenerateRecoveryCodes(principalId, account, code, ip, actor) ?: RecoveryCodesOutcome.NoFactor

    /**
     * An administrator removes the factors of a user, all of them or the one [factorId] names. The sessions of the user end at
     * once, so that the next sign-in has to pass the factors that are left.
     */
    fun resetSecondFactors(principalName: String, factorId: Int?, actor: AuditActor): Int? {
        val principalId = service?.principalIdOf(principalName) ?: return null
        val removed = secondFactor?.resetFactors(principalId, principalName, factorId, actor) ?: 0
        onSecondFactorChanged()
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

    /**
     * A new browser session for a principal that has just logged in. [mfa] is set when a second factor completed the login.
     * `null` without a database.
     */
    fun startSession(principal: PrincipalRecord, ip: String?, userAgent: String?, mfa: Boolean = false): SessionTokens? =
        sessions?.start(principal, ip, userAgent, mfa)

    /** Checks a code of a user for a step-up, see [SecondFactorService.stepUp]. */
    fun completeStepUp(principalId: Int, account: String, code: String, ip: String): StepUpOutcome =
        secondFactor?.stepUp(principalId, account, code, ip) ?: StepUpOutcome.NoFactor

    /**
     * The session of a verified step-up: the same family, with the authentication time now and a second factor. `null` when the
     * family is no longer usable, see [SessionService.stepUp].
     */
    fun stepUpSession(session: VerifiedSession, ip: String?, userAgent: String?): SessionTokens? =
        sessions?.stepUp(session, ip, userAgent)

    /**
     * Whether a sensitive action may proceed for [session], see [stepUpAllows]. A caller without a session may always proceed. The
     * [enrolment] flag is set for the first factor enrolment, which needs a recent password entry even without a factor.
     */
    fun stepUpAllowed(session: VerifiedSession?, enrolment: Boolean): Boolean =
        stepUpAllows(
            session,
            hasFactor = session != null && hasSecondFactor(session.principalId),
            enrolment = enrolment,
            now = Instant.now(clock),
            window = Duration.ofMinutes(settings().mfa.stepUpMinutes),
        )

    /**
     * The login gate of a sign-in through an external provider, see design D12. [providerMfa] tells that the provider's ID token carries a
     * trusted `acr` or `amr` value, see `OidcFinish.LoggedIn`. [github] tells that the provider is GitHub, which has no ID token.
     *
     * An uncovered user passes whatever factors the user has: the provider authenticated the user, and an Ingot prompt on top of
     * that is not asked for. A covered GitHub user is challenged when a factor exists, and passes otherwise: a challenge without a
     * factor could never be completed. A covered user passes when the provider verified a factor, is challenged when a factor
     * exists, and passes otherwise, where the grace rule applies to the session.
     */
    fun externalSecondFactor(principal: PrincipalRecord, providerMfa: Boolean, github: Boolean): ExternalSecondFactor {
        val covered = policy?.covers(principal) ?: false
        if (!covered) {
            return ExternalSecondFactor.Pass
        }
        val hasFactor = hasSecondFactor(principal.id)
        return when {
            github -> if (hasFactor) challengeOf(principal) else ExternalSecondFactor.Pass
            providerMfa -> ExternalSecondFactor.Pass
            hasFactor -> challengeOf(principal)
            else -> ExternalSecondFactor.Pass
        }
    }

    private fun challengeOf(principal: PrincipalRecord): ExternalSecondFactor =
        secondFactor?.let { ExternalSecondFactor.Challenge(it.startChallenge(principal.id)) } ?: ExternalSecondFactor.Refused

    /** Whether `login.mfa.passkeyFirstFactor` allows a passkey to sign a user in without a password. */
    fun passkeyFirstFactorEnabled(): Boolean =
        settings().mfa.passkeyFirstFactor

    /**
     * Starts a passkey sign-in without a password for the user [username]. Nothing is stored while the switch is off, see
     * [FirstFactorPasskey.Disabled].
     */
    fun startPasskeyFirstFactor(username: String): FirstFactorPasskey {
        if (!passkeyFirstFactorEnabled()) {
            return FirstFactorPasskey.Disabled
        }
        return when (val start = secondFactor?.startPasskeyFirstFactor(username) ?: PasskeyStart.NoPasskey) {
            is PasskeyStart.Started -> FirstFactorPasskey.Started(start.challenge)
            else -> FirstFactorPasskey.Unavailable
        }
    }

    /**
     * Completes a passkey sign-in without a password. The caller starts the session on [ChallengeCompletion.Verified] with the second
     * factor recorded. Refused while the switch is off.
     */
    fun completePasskeyFirstFactor(ceremonyId: String, proof: PasskeyProof, rpId: String, origin: String, ip: String): ChallengeCompletion =
        if (!passkeyFirstFactorEnabled()) {
            ChallengeCompletion.Gone
        } else {
            secondFactor?.completePasskeyFirstFactor(ceremonyId, proof, rpId, origin, ip) ?: ChallengeCompletion.Gone
        }

    /** The ways a user can complete a second factor, for the answer of a refused sensitive action. */
    fun secondFactorMethodsOf(principalId: Int): List<SecondFactorMethod> =
        secondFactor?.methodsOf(principalId).orEmpty()

    /**
     * The security status of a user, see design D14. [session] is the verified session that made the read, `null` for a Basic or Bearer
     * caller. The read writes nothing: a covered user whose grace the policy has not recorded yet has no grace end.
     */
    fun securityStatusOf(principalId: Int, session: VerifiedSession?): SecurityStatus {
        val factors = secondFactorsOf(principalId)
        val mfa = settings().mfa
        val covered = policy?.isCoveredById(principalId) ?: false
        return SecurityStatus(
            authenticators = factors.count { it.kind == KIND_TOTP },
            passkeys = factors.count { it.kind == KIND_PASSKEY },
            recoveryCodesLeft = secondFactor?.recoveryCodesLeft(principalId) ?: 0,
            covered = covered,
            requiredForAdmins = mfa.requiredForAdmins,
            requiredForTeams = mfa.requiredForTeams,
            graceUntil = if (covered && factors.isEmpty()) policy?.graceEndOf(principalId) else null,
            scope = session?.scope ?: SessionScope.FULL,
            stepUpUntil = session?.authenticatedAt?.plus(Duration.ofMinutes(mfa.stepUpMinutes)),
        )
    }

    /** Renews a session with its refresh token, see [SessionService.refresh]. `null` when the token is not usable. */
    fun refreshSession(refreshToken: String, ip: String?, userAgent: String?): SessionTokens? =
        sessions?.refresh(refreshToken, ip, userAgent)

    fun endSession(refreshToken: String?, ip: String?) {
        sessions?.logout(refreshToken, ip)
    }

    /**
     * The verified claims of a browser session token of an active user, see [SessionService.authenticate]. `null` when the token is
     * not a valid, unexpired session.
     */
    fun verifySession(sessionJwt: String): VerifiedSession? =
        sessions?.authenticate(sessionJwt)

    /** Refuses the session tokens of a family that another instance revoked, see [SessionService.denyFamily]. */
    internal fun denySessionFamily(familyId: String) {
        sessions?.denyFamily(familyId)
    }

    /**
     * The identity of the caller that presented a session token: the principal, addressed the way the authorization
     * facade resolves it. `null` when the token is not a valid, unexpired session of an active user.
     */
    fun authenticateSession(sessionJwt: String): AccessTokenIdentifier? =
        verifySession(sessionJwt)?.let { AccessTokenIdentifier(type = PRINCIPAL, value = it.principalId) }

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
