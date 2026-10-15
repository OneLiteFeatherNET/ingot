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

import com.reposilite.plugin.api.Facade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenType.PRINCIPAL
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.session.SessionService
import net.onelitefeather.ingot.identity.session.SessionTokens

/**
 * What the rest of Ingot asks of human login and browser sessions. Without a database there are no users and no sessions:
 * every login answers [LoginOutcome.UnknownProvider], no provider is announced and no cookie is accepted.
 */
class LoginFacade internal constructor(
    private val service: LoginService?,
    private val sessions: SessionService?,
) : Facade {

    fun providers(): List<LoginProvider> =
        service?.providers().orEmpty()

    fun setupRequired(): Boolean =
        service?.let { !it.hasUsers() } ?: false

    fun login(providerId: String, username: String, password: String, ip: String): LoginOutcome =
        service?.login(providerId, username, password, ip) ?: LoginOutcome.UnknownProvider

    /** Creates a human user with a local password. Returns `null` when the name is taken. */
    fun createUser(name: String, displayName: String, password: String, disabled: Boolean = false): PrincipalRecord? =
        service?.createUser(name, displayName, password, disabled)

    fun passwordProblem(password: String): String? =
        service?.passwordProblem(password)

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

}
