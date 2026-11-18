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

import com.reposilite.audit.api.AuditAction.LOGIN_FAILED
import com.reposilite.audit.api.AuditAction.LOGIN_SUCCEEDED
import com.reposilite.audit.api.AuditAction.MFA_FIRST_FACTOR_ACCEPTED
import com.reposilite.audit.api.AuditAction.PASSWORD_BREACH_CHECK_UNAVAILABLE
import com.reposilite.audit.api.AuditAction.PASSWORD_CHANGED
import com.reposilite.audit.api.AuditAction.PASSWORD_RESET
import com.reposilite.audit.api.AuditAction.SETUP_COMPLETED
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.auth.SignInObserver
import com.reposilite.auth.SignInOutcome
import com.reposilite.auth.SignInPath
import net.onelitefeather.ingot.identity.login.LoginOutcome.Blocked
import net.onelitefeather.ingot.identity.login.LoginOutcome.Disabled
import net.onelitefeather.ingot.identity.login.LoginOutcome.Rejected
import net.onelitefeather.ingot.identity.login.LoginOutcome.Succeeded
import net.onelitefeather.ingot.identity.login.LoginOutcome.UnknownProvider
import net.onelitefeather.ingot.identity.login.SetupOutcome.AlreadyCompleted
import net.onelitefeather.ingot.identity.login.SetupOutcome.Blocked as SetupBlocked
import net.onelitefeather.ingot.identity.login.SetupOutcome.Created
import net.onelitefeather.ingot.identity.login.SetupOutcome.InvalidInput
import net.onelitefeather.ingot.identity.login.SetupOutcome.NameTaken
import net.onelitefeather.ingot.identity.login.SetupOutcome.NotRequired
import net.onelitefeather.ingot.identity.login.SetupOutcome.WrongCode
import net.onelitefeather.ingot.identity.login.challenge.ChallengeVerdict
import net.onelitefeather.ingot.identity.login.challenge.LoginChallengeAnswer
import net.onelitefeather.ingot.identity.login.challenge.LoginChallenges
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.storage.IdentityStore
import panda.std.reactive.Reference
import java.time.Clock
import java.time.Duration

/**
 * Password logins for human principals. Every provider goes through the same checks here: the failure limit per user
 * name and address, the audit record of a rejected attempt and the password policy. The audit record names the attempted
 * user and address; the password never leaves the request.
 */
class LoginService internal constructor(
    private val identities: IdentityStore,
    private val credentials: LoginStore,
    private val hasher: PasswordHasher,
    private val settings: Reference<LoginSettings>,
    clock: Clock,
    private val audit: (AuditEvent) -> Unit,
    private val setupCode: SetupCode = SetupCode(),
    /** Password providers beyond the local one, read on every call so that a change in the settings applies at once. */
    private val externalPasswordProviders: () -> List<LoginProvider> = { emptyList() },
    /** Providers that redirect the browser, and the LDAP provider when it is enabled. */
    private val externalAnnouncements: () -> List<ProviderAnnouncement> = { emptyList() },
    /** Whether a principal whose password was accepted still owes a second factor. Then no login is recorded yet. */
    private val secondFactorRequired: (PrincipalRecord) -> Boolean = { false },
    /** The per-account delay of guesses from new addresses. `null` leaves the login as it was before the delay existed. */
    private val accountThrottle: AccountThrottle? = null,
    /** The breached-password check of new passwords. `null` leaves the check out, as the default mode does. */
    private val breachCheck: PasswordBreachCheck? = null,
    /** Counts and announces the outcomes of the dashboard logins. Nothing is observed while the account throttle switch is off. */
    private val signInObserver: SignInObserver = SignInObserver.OFF,
    /** The login challenge a plugin may register for names the account throttle delays. `null` asks for none. */
    private val challenges: LoginChallenges? = null,
) {

    private val limiter = LoginAttemptLimiter(credentials, settings, clock)

    /** Serializes setups of this instance, so that the check for "no user yet" and the creation happen together. */
    private val setupLock = Any()

    private val localProvider = LocalLoginProvider(identities, credentials, hasher)

    /** The providers that check a password. Only these answer [login]. */
    fun passwordProviders(): List<LoginProvider> =
        listOf(localProvider) + externalPasswordProviders()

    /**
     * What the login page shows: the local provider while a local user exists or the first administrator was set up, then
     * the external providers. A provider that is disabled is never listed.
     */
    fun announcements(): List<ProviderAnnouncement> {
        val local = if (hasUsers() || credentials.isFirstAdministratorClaimed()) {
            listOf(ProviderAnnouncement(id = LocalLoginProvider.ID, type = LoginProviderType.LOCAL, label = localProvider.label, icon = "LOCAL"))
        } else {
            emptyList()
        }
        return local + externalAnnouncements()
    }

    fun login(providerId: String, username: String, password: String, ip: String, userAgent: String? = null): LoginOutcome =
        login(providerId, username, password, ip, userAgent, challenge = null)

    /**
     * A login with the `challenge` member of the request. It is read only while the account throttle delays the name and a
     * login challenge provider is registered, and ignored otherwise. A verified challenge lets one password check through.
     */
    fun login(providerId: String, username: String, password: String, ip: String, userAgent: String?, challenge: LoginChallengeAnswer?): LoginOutcome {
        val provider = passwordProviders().firstOrNull { it.id == providerId } ?: return UnknownProvider
        val userKey = LoginAttemptLimiter.userKey(username, ip)
        val keys = listOf(userKey, LoginAttemptLimiter.addressKey(ip))

        limiter.retryAfter(keys)?.let { wait -> return delayed(username, ip, wait) }
        accountThrottle?.retryAfter(username, ip)?.let { wait ->
            when (val verdict = challenges?.judge(username, ip, challenge) ?: ChallengeVerdict.NotOffered) {
                ChallengeVerdict.Passed -> Unit
                ChallengeVerdict.NotOffered -> return delayed(username, ip, wait)
                is ChallengeVerdict.Required -> {
                    val blocked = delayed(username, ip, wait)
                    return LoginOutcome.ChallengeRequired(blocked.retryAfterSeconds, verdict.provider, verdict.parameters)
                }
            }
        }

        val outcome = provider.authenticate(username, password)
        when (outcome) {
            is Succeeded -> {
                limiter.forget(userKey)
                // Asked before the address is recorded as known for the name
                val newAddress = accountThrottle?.isNewAddress(username, ip) == true
                accountThrottle?.recordSuccess(username, ip)
                signInObserver.record(SignInPath.DASHBOARD, SignInOutcome.SUCCEEDED)
                if (newAddress) {
                    signInObserver.newSignIn(username, ip, SignInPath.DASHBOARD, userAgent)
                }
                // A login is recorded when its session starts. A user who owes a second factor has only passed the first one.
                val action = if (secondFactorRequired(outcome.principal)) MFA_FIRST_FACTOR_ACCEPTED else LOGIN_SUCCEEDED
                audit(AuditEvent(action = action, actor = AuditActor.named(outcome.principal.name, ip)))
            }
            Rejected -> {
                limiter.recordFailure(keys)
                accountThrottle?.recordFailure(username)
                signInObserver.record(SignInPath.DASHBOARD, SignInOutcome.FAILED)
                recordRejected(username, ip)
            }
            // The password was right, so this is not guessing and does not count towards the limit.
            Disabled -> {
                signInObserver.record(SignInPath.DASHBOARD, SignInOutcome.FAILED)
                recordRejected(username, ip)
            }
            // The password was judged to be wrong for this account: counted like a rejection by the audit trail.
            is LoginOutcome.Refused -> {
                signInObserver.record(SignInPath.DASHBOARD, SignInOutcome.FAILED)
                recordRejected(username, ip)
            }
            // The directory did not answer, so nothing was judged and nothing counts towards the limit.
            LoginOutcome.Unavailable, is Blocked, is LoginOutcome.ChallengeRequired, UnknownProvider, is LoginOutcome.SecondFactorRequired -> Unit
        }
        return outcome
    }

    /** Records that the session of a user has started, after the second factor was completed. */
    fun recordLoginSucceeded(principalName: String, ip: String) {
        audit(AuditEvent(action = LOGIN_SUCCEEDED, actor = AuditActor.named(principalName, ip)))
    }

    /** Sets the local password of a principal. [mustChange] makes the next login ask for a new one. */
    fun setPassword(principal: PrincipalRecord, password: String, mustChange: Boolean) {
        credentials.saveCredential(principal.id, hasher.hash(password), mustChange)
    }

    /** Creates a user principal with a password that does not have to be changed. `null` when the name is taken. */
    fun createUser(name: String, displayName: String, password: String, disabled: Boolean): PrincipalRecord? {
        val principal = identities.createPrincipal(PrincipalKind.USER, name, displayName, disabled, identities.now()) ?: return null
        setPassword(principal, password, mustChange = false)
        return principal
    }

    /**
     * The user changes their own password. The current one must be given; a forced change, if any, ends with it. [actor] is
     * the user, with the address of the request; the password itself is never audited.
     */
    fun changePassword(principalId: Int, currentPassword: String, newPassword: String, actor: AuditActor = AuditActor.SYSTEM_ACTOR): PasswordOutcome {
        val principal = identities.findPrincipalById(principalId)?.takeIf { it.kind == PrincipalKind.USER }
            ?: return PasswordOutcome.NotAUser
        val stored = credentials.findCredential(principal.id)?.passwordHash.orEmpty()
        if (!hasher.matches(currentPassword, stored)) {
            return PasswordOutcome.WrongCurrentPassword
        }
        passwordProblem(newPassword)?.let { return PasswordOutcome.InvalidInput(it) }
        breachRefusal(newPassword, actor)?.let { return it.toPasswordOutcome() }
        setPassword(principal, newPassword, mustChange = false)
        audit(AuditEvent(action = PASSWORD_CHANGED, actor = actor, details = mapOf("principal" to principal.name)))
        return PasswordOutcome.Changed
    }

    /** An administrator sets a new password. The user must choose another one at the next login. [actor] is the administrator. */
    fun resetPassword(principalName: String, newPassword: String, actor: AuditActor = AuditActor.SYSTEM_ACTOR): PasswordOutcome {
        val principal = identities.findPrincipal(principalName) ?: return PasswordOutcome.NotFound
        if (principal.kind != PrincipalKind.USER) {
            return PasswordOutcome.NotAUser
        }
        passwordProblem(newPassword)?.let { return PasswordOutcome.InvalidInput(it) }
        breachRefusal(newPassword, actor)?.let { return it.toPasswordOutcome() }
        setPassword(principal, newPassword, mustChange = true)
        audit(AuditEvent(action = PASSWORD_RESET, actor = actor, details = mapOf("principal" to principal.name)))
        return PasswordOutcome.Changed
    }

    /** A user-facing reason why [password] is not acceptable, or `null` when it is. */
    fun passwordProblem(password: String): String? {
        val minimum = settings.get().passwordPolicy.minLength
        return when {
            password.length < minimum -> "Password must have at least $minimum characters"
            password.length > MAX_PASSWORD_LENGTH -> "Password must not have more than $MAX_PASSWORD_LENGTH characters"
            else -> null
        }
    }

    /**
     * What the breached-password check says about a password that is about to be set: `null` when it may be set. An
     * outage that the settings allow is audited and lets the password through.
     */
    private fun breachRefusal(password: String, actor: AuditActor): BreachRefusal? {
        val check = breachCheck ?: return null
        return when (val verdict = check.verdictOf(password)) {
            BreachVerdict.Clear -> null
            BreachVerdict.Breached -> BreachRefusal.Breached
            is BreachVerdict.Unavailable -> if (verdict.failClosed) {
                BreachRefusal.Unavailable(BREACH_CHECK_RETRY_SECONDS)
            } else {
                audit(AuditEvent(action = PASSWORD_BREACH_CHECK_UNAVAILABLE, actor = actor))
                null
            }
        }
    }

    private sealed interface BreachRefusal {
        data object Breached : BreachRefusal
        data class Unavailable(val retryAfterSeconds: Long) : BreachRefusal
    }

    private fun BreachRefusal.toPasswordOutcome(): PasswordOutcome = when (this) {
        BreachRefusal.Breached -> PasswordOutcome.InvalidInput(BREACHED_PASSWORD_PROBLEM)
        is BreachRefusal.Unavailable -> PasswordOutcome.BreachCheckUnavailable(retryAfterSeconds)
    }

    private fun BreachRefusal.toSetupOutcome(): SetupOutcome = when (this) {
        BreachRefusal.Breached -> InvalidInput(BREACHED_PASSWORD_PROBLEM)
        is BreachRefusal.Unavailable -> SetupOutcome.BreachCheckUnavailable(retryAfterSeconds)
    }

    /** Whether at least one human principal exists. The setup code is needed exactly as long as this is `false`. */
    fun hasUsers(): Boolean =
        identities.listPrincipals(PrincipalKind.USER).isNotEmpty()

    /** Whether the user must choose a new password at the next sign-in, which an administrator reset asks for. */
    fun mustChangePassword(principalId: Int): Boolean =
        credentials.findCredential(principalId)?.mustChangePassword ?: false

    fun principalIdOf(principalName: String): Int? =
        identities.findPrincipal(principalName)?.id

    fun principalNamed(principalName: String): PrincipalRecord? =
        identities.findPrincipal(principalName)

    fun isSetupRequired(): Boolean =
        !hasUsers()

    /** The setup code to show in the log, or `null` when no setup is required. The same code is returned until it is used. */
    fun issueSetupCode(): String? =
        if (isSetupRequired()) setupCode.issue() else null

    /**
     * Creates the first administrator. The code is checked like a password: wrong codes count towards the failure limit of
     * the address, are audited, and never reveal the code. A successful setup creates the user with the instance admin
     * role and consumes the code.
     */
    fun completeSetup(givenCode: String, username: String, password: String, displayName: String?, ip: String): SetupOutcome {
        synchronized(setupLock) {
            return createFirstAdministrator(givenCode, username, password, displayName, ip)
        }
    }

    private fun createFirstAdministrator(givenCode: String, username: String, password: String, displayName: String?, ip: String): SetupOutcome {
        if (!isSetupRequired()) {
            return if (setupCode.matches(givenCode) && credentials.isFirstAdministratorClaimed()) AlreadyCompleted else NotRequired
        }
        val addressKey = LoginAttemptLimiter.addressKey(ip)
        limiter.retryAfter(listOf(addressKey))?.let { wait ->
            recordRejected(SETUP_ACTOR, ip)
            return SetupBlocked(ceilSeconds(wait))
        }
        if (!setupCode.matches(givenCode)) {
            limiter.recordFailure(listOf(addressKey))
            recordRejected(SETUP_ACTOR, ip)
            return WrongCode
        }
        val name = username.trim()
        usernameProblem(name)?.let { return InvalidInput(it) }
        passwordProblem(password)?.let { return InvalidInput(it) }
        breachRefusal(password, AuditActor.named(name, ip))?.let { return it.toSetupOutcome() }

        // The claim makes the setup exclusive across instances: of all instances on the database, only one gets it.
        if (!credentials.claimFirstAdministrator()) {
            return AlreadyCompleted
        }
        val principal = try {
            identities.createPrincipal(
                PrincipalKind.USER,
                name,
                displayName?.takeIf { it.isNotBlank() } ?: name,
                false,
                identities.now(),
            )
        } catch (failure: Throwable) {
            credentials.releaseFirstAdministrator()
            throw failure
        }
        if (principal == null) {
            credentials.releaseFirstAdministrator()
            return NameTaken
        }
        identities.grantInstanceRole(principalId = principal.id, teamId = null)
        setPassword(principal, password, mustChange = false)
        setupCode.clear()
        audit(AuditEvent(action = SETUP_COMPLETED, actor = AuditActor.named(principal.name, ip), details = mapOf("principal" to principal.name)))
        return Created(principal)
    }

    private fun usernameProblem(name: String): String? =
        LoginNames.usernameProblem(name)

    /** Whole seconds to wait, rounded up, and at least one: a client told "0" would retry at once. */
    private fun ceilSeconds(wait: Duration): Long =
        ((wait.toMillis() + 999) / 1000).coerceAtLeast(1)

    private fun recordRejected(username: String, ip: String) {
        audit(AuditEvent(action = LOGIN_FAILED, actor = AuditActor.named(username, ip), success = false))
    }

    /**
     * A login answered with a delay. While the account throttle is on it is audited once as `LOGIN_THROTTLED` and announced;
     * while it is off the rejection is audited as before.
     */
    private fun delayed(username: String, ip: String, wait: Duration): Blocked {
        val retryAfterSeconds = ceilSeconds(wait)
        if (signInObserver.enabled) {
            signInObserver.throttled(username, ip, SignInPath.DASHBOARD, retryAfterSeconds)
        } else {
            recordRejected(username, ip)
        }
        return Blocked(retryAfterSeconds = retryAfterSeconds)
    }

    companion object {
        const val MAX_PASSWORD_LENGTH = 256
        private const val SETUP_ACTOR = "setup"
        private const val BREACHED_PASSWORD_PROBLEM = "This password appears in a breached password list, choose another one"

        /** The retry hint of a change refused because the breached-password service did not answer. */
        private const val BREACH_CHECK_RETRY_SECONDS = 60L
    }

}
