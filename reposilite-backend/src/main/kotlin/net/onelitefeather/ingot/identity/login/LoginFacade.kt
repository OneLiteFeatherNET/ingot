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
) : Facade {

    fun providers(): List<LoginProvider> =
        service?.providers().orEmpty()

    fun setupRequired(): Boolean =
        service?.isSetupRequired() ?: false

    /** The setup code to show in the log, `null` when no setup is required. */
    fun issueSetupCode(): String? =
        service?.issueSetupCode()

    fun completeSetup(givenCode: String, username: String, password: String, displayName: String?, ip: String): SetupOutcome =
        service?.completeSetup(givenCode, username, password, displayName, ip) ?: SetupOutcome.NotRequired

    fun login(providerId: String, username: String, password: String, ip: String): LoginOutcome =
        service?.login(providerId, username, password, ip) ?: LoginOutcome.UnknownProvider

    /** Creates a human user with a local password. Returns `null` when the name is taken. */
    fun createUser(name: String, displayName: String, password: String, disabled: Boolean = false): PrincipalRecord? =
        service?.createUser(name, displayName, password, disabled)

    fun passwordProblem(password: String): String? =
        service?.passwordProblem(password)

    /**
     * The user changes their own password. Every session of the user ends, on every instance, so that the password change
     * is also a logout everywhere.
     */
    fun changeOwnPassword(principalId: Int, currentPassword: String, newPassword: String): PasswordOutcome {
        val outcome = service?.changePassword(principalId, currentPassword, newPassword) ?: PasswordOutcome.NotFound
        if (outcome == PasswordOutcome.Changed) {
            sessions?.revokeAllSessions(principalId)
        }
        return outcome
    }

    /** An administrator resets the password of a user. The user must change it at the next login, and every session ends. */
    fun resetPassword(principalName: String, newPassword: String): PasswordOutcome {
        val principalId = service?.principalIdOf(principalName)
        val outcome = service?.resetPassword(principalName, newPassword) ?: PasswordOutcome.NotFound
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

    fun endSession(refreshToken: String?) {
        sessions?.logout(refreshToken)
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
