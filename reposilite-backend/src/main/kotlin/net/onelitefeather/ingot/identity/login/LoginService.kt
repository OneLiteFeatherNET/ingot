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
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.auth.application.BruteForceProtectionSettings
import net.onelitefeather.ingot.identity.login.LoginOutcome.Blocked
import net.onelitefeather.ingot.identity.login.LoginOutcome.Disabled
import net.onelitefeather.ingot.identity.login.LoginOutcome.Rejected
import net.onelitefeather.ingot.identity.login.LoginOutcome.Succeeded
import net.onelitefeather.ingot.identity.login.LoginOutcome.UnknownProvider
import net.onelitefeather.ingot.identity.login.SetupOutcome.Blocked as SetupBlocked
import net.onelitefeather.ingot.identity.login.SetupOutcome.Created
import net.onelitefeather.ingot.identity.login.SetupOutcome.InvalidInput
import net.onelitefeather.ingot.identity.login.SetupOutcome.NameTaken
import net.onelitefeather.ingot.identity.login.SetupOutcome.NotRequired
import net.onelitefeather.ingot.identity.login.SetupOutcome.WrongCode
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.storage.IdentityStore
import panda.std.reactive.Reference
import java.time.Clock

/**
 * Password logins for human principals. Every provider goes through the same checks here: the failure limit per user
 * name and address, the audit record of a rejected attempt and the password policy. The audit record names the attempted
 * user and address; the password never leaves the request.
 */
class LoginService internal constructor(
    private val identities: IdentityStore,
    private val credentials: LoginStore,
    private val hasher: PasswordHasher,
    protection: Reference<BruteForceProtectionSettings>,
    clock: Clock,
    private val audit: (AuditEvent) -> Unit,
    private val setupCode: SetupCode = SetupCode(),
) {

    private val limiter = LoginRateLimiter(protection, clock)

    /** Serializes setups of this instance, so that the check for "no user yet" and the creation happen together. */
    private val setupLock = Any()

    private val providers: List<LoginProvider> = listOf(
        LocalLoginProvider(identities, credentials, hasher),
    )

    fun providers(): List<LoginProvider> =
        providers

    fun login(providerId: String, username: String, password: String, ip: String): LoginOutcome {
        val provider = providers.firstOrNull { it.id == providerId } ?: return UnknownProvider
        val key = "${username.lowercase()}|$ip"

        if (limiter.isBlocked(key)) {
            recordRejected(username, ip)
            return Blocked
        }

        val outcome = provider.authenticate(username, password)
        when (outcome) {
            is Succeeded -> limiter.forget(key)
            Rejected -> {
                limiter.recordFailure(key)
                recordRejected(username, ip)
            }
            // The password was right, so this is not guessing and does not count towards the limit.
            Disabled -> recordRejected(username, ip)
            Blocked, UnknownProvider -> Unit
        }
        return outcome
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

    /** The user changes their own password. The current one must be given; a forced change, if any, ends with it. */
    fun changePassword(principalId: Int, currentPassword: String, newPassword: String): PasswordOutcome {
        val principal = identities.findPrincipalById(principalId)?.takeIf { it.kind == PrincipalKind.USER }
            ?: return PasswordOutcome.NotAUser
        val stored = credentials.findCredential(principal.id)?.passwordHash.orEmpty()
        if (!hasher.matches(currentPassword, stored)) {
            return PasswordOutcome.WrongCurrentPassword
        }
        passwordProblem(newPassword)?.let { return PasswordOutcome.InvalidInput(it) }
        setPassword(principal, newPassword, mustChange = false)
        return PasswordOutcome.Changed
    }

    /** An administrator sets a new password. The user must choose another one at the next login. */
    fun resetPassword(principalName: String, newPassword: String): PasswordOutcome {
        val principal = identities.findPrincipal(principalName) ?: return PasswordOutcome.NotFound
        if (principal.kind != PrincipalKind.USER) {
            return PasswordOutcome.NotAUser
        }
        passwordProblem(newPassword)?.let { return PasswordOutcome.InvalidInput(it) }
        setPassword(principal, newPassword, mustChange = true)
        return PasswordOutcome.Changed
    }

    /** A user-facing reason why [password] is not acceptable, or `null` when it is. */
    fun passwordProblem(password: String): String? = when {
        password.length < MIN_PASSWORD_LENGTH -> "Password must have at least $MIN_PASSWORD_LENGTH characters"
        password.length > MAX_PASSWORD_LENGTH -> "Password must not have more than $MAX_PASSWORD_LENGTH characters"
        else -> null
    }

    /** Whether at least one human principal exists. The setup code is needed exactly as long as this is `false`. */
    fun hasUsers(): Boolean =
        identities.listPrincipals(PrincipalKind.USER).isNotEmpty()

    fun principalIdOf(principalName: String): Int? =
        identities.findPrincipal(principalName)?.id

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
            return NotRequired
        }
        val key = "setup|$ip"
        if (limiter.isBlocked(key)) {
            recordRejected(SETUP_ACTOR, ip)
            return SetupBlocked
        }
        if (!setupCode.matches(givenCode)) {
            limiter.recordFailure(key)
            recordRejected(SETUP_ACTOR, ip)
            return WrongCode
        }
        val name = username.trim()
        usernameProblem(name)?.let { return InvalidInput(it) }
        passwordProblem(password)?.let { return InvalidInput(it) }

        val principal = identities.createPrincipal(
            PrincipalKind.USER,
            name,
            displayName?.takeIf { it.isNotBlank() } ?: name,
            false,
            identities.now(),
        ) ?: return NameTaken
        identities.grantInstanceRole(principalId = principal.id, teamId = null)
        setPassword(principal, password, mustChange = false)
        setupCode.clear()
        limiter.forget(key)
        return Created(principal)
    }

    private fun usernameProblem(name: String): String? = when {
        name.isEmpty() -> "User name is required"
        name.length > MAX_USERNAME_LENGTH -> "User name must not have more than $MAX_USERNAME_LENGTH characters"
        name.any { it == ':' || it.isWhitespace() || it.isISOControl() } -> "User name must not contain colons, spaces or control characters"
        else -> null
    }

    private fun recordRejected(username: String, ip: String) {
        audit(AuditEvent(action = LOGIN_FAILED, actor = AuditActor.named(username, ip), success = false))
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 12
        const val MAX_PASSWORD_LENGTH = 256
        private const val MAX_USERNAME_LENGTH = 255
        private const val SETUP_ACTOR = "setup"
    }

}
