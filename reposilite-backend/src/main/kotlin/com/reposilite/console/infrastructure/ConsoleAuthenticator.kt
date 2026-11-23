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

package com.reposilite.console.infrastructure

import com.reposilite.auth.AuthenticationFacade
import com.reposilite.auth.SignInPath
import com.reposilite.auth.api.Credentials
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.unauthorizedError
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.api.AccessTokenDto
import net.onelitefeather.ingot.identity.login.LoginFacade
import panda.std.Result
import panda.std.asSuccess

/**
 * Decides who may open the remote console. Two ways in end at the same check, the instance admin role:
 * the credentials of a token, as the console has always taken them, and the session token of a browser login, the same
 * one the HTTP API accepts from its session cookie.
 */
internal open class ConsoleAuthenticator(
    private val accessTokenFacade: AccessTokenFacade,
    private val authenticationFacade: AuthenticationFacade,
    private val loginFacade: LoginFacade,
) {

    open fun authenticate(credentials: Credentials): Result<AccessTokenDto, ErrorResponse> =
        authenticationFacade.authenticateByCredentials(credentials, path = SignInPath.CONSOLE)
            .flatMap { requireConsoleArea(it, credentials.ip) }

    /** A session token from a browser login. Fails like a wrong secret when the session is not valid or has no user. */
    open fun authenticateSession(sessionJwt: String, ip: String): Result<AccessTokenDto, ErrorResponse> {
        val identifier = loginFacade.authenticateSession(sessionJwt)
            ?: return unauthorizedError("Invalid or expired session")
        val identity = accessTokenFacade.getAccessTokenById(identifier)
            ?: return unauthorizedError("Invalid or expired session")
        return requireConsoleArea(identity, ip)
    }

    /**
     * Whether [identity], authenticated earlier, may still use the console: the token still exists and still has the console
     * area, and [sessionJwt], when it came from a browser login, is still a valid session.
     */
    open fun isStillAuthorized(identity: AccessTokenDto, sessionJwt: String?): Boolean {
        if (sessionJwt != null && loginFacade.authenticateSession(sessionJwt) == null) {
            return false
        }
        val current = accessTokenFacade.getAccessTokenById(identity.identifier) ?: return false
        return accessTokenFacade.hasAdminArea(current.identifier, CONSOLE_AREA)
    }

    /** The console is open to the principals that may administer the console area, the node `ingot.admin.console`. */
    private fun requireConsoleArea(identity: AccessTokenDto, ip: String): Result<AccessTokenDto, ErrorResponse> =
        if (accessTokenFacade.hasAdminArea(identity.identifier, CONSOLE_AREA)) {
            identity.asSuccess()
        } else {
            unauthorizedError("Unauthorized CLI access request from $ip (the principal has no console area)")
        }

    private companion object {
        private const val CONSOLE_AREA = "console"
    }

}
