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

package net.onelitefeather.ingot.identity.login.api

import com.reposilite.audit.api.AuditActor
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequestError
import com.reposilite.shared.errorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import net.onelitefeather.ingot.identity.api.CreatedTokenDto
import net.onelitefeather.ingot.identity.api.TokenDto
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.OwnTokenOutcome
import net.onelitefeather.ingot.identity.login.PasswordOutcome
import net.onelitefeather.ingot.identity.model.PrincipalKind
import panda.std.Result
import panda.std.asSuccess

/**
 * What a signed-in user does with their own account: the password, and the tokens for scripts and CI. Every route
 * resolves the caller to a user principal first, so a machine principal or a legacy token gets 403. The administrator
 * reset of a password is here too, because it is the same operation from the other side.
 */
internal class SelfServiceEndpoints(
    private val facade: LoginFacade,
    private val accessTokens: AccessTokenFacade,
) : ReposiliteRoutes() {

    private data class Caller(val principalId: Int, val name: String)

    private fun callerOf(identifier: AccessTokenIdentifier): Result<Caller, ErrorResponse> {
        val subject = accessTokens.subjectOf(identifier)
        val principalId = subject.principalId
        val name = subject.principalName
        return if (principalId == null || name == null || subject.kind != PrincipalKind.USER) {
            errorResponse(FORBIDDEN, "Only a user account can manage itself, not a machine principal or a legacy token")
        } else {
            Caller(principalId, name).asSuccess()
        }
    }

    @OpenApi(
        path = "/api/identity/me/password",
        methods = [HttpMethod.POST],
        summary = "Change own password",
        description = "Changes the password of the signed-in user after checking the current one. Every session of the user ends",
        tags = ["Identity"],
    )
    private val changePassword = ReposiliteRoute<Unit>("/api/identity/me/password", Route.POST) {
        authenticated {
            val caller = callerOf(identifier)
            val request = runCatching { body<PasswordChangeRequest>() }.getOrNull()
            val current = request?.currentPassword?.takeIf { it.isNotEmpty() }
            val replacement = request?.newPassword?.takeIf { it.isNotEmpty() }

            response = caller.flatMap { user ->
                if (current == null || replacement == null) {
                    badRequestError("Password change requires currentPassword and newPassword")
                } else {
                    passwordResponse(facade.changeOwnPassword(user.principalId, current, replacement))
                }
            }
        }
    }

    @OpenApi(
        path = "/api/identity/me/tokens",
        methods = [HttpMethod.GET],
        summary = "List own tokens",
        description = "The tokens of the signed-in user, without their secrets",
        tags = ["Identity"],
    )
    private val listTokens = ReposiliteRoute<List<TokenDto>>("/api/identity/me/tokens", Route.GET) {
        authenticated {
            response = callerOf(identifier).flatMap { user -> facade.listOwnTokens(user.principalId).asSuccess() }
        }
    }

    @OpenApi(
        path = "/api/identity/me/tokens",
        methods = [HttpMethod.POST],
        summary = "Create an own token",
        description = "Creates a token for the signed-in user. The secret is returned once, in this response",
        tags = ["Identity"],
    )
    private val createToken = ReposiliteRoute<CreatedTokenDto>("/api/identity/me/tokens", Route.POST) {
        authenticated {
            val caller = callerOf(identifier)
            val request = runCatching { body<SelfTokenRequest>() }.getOrNull() ?: SelfTokenRequest()
            response = caller.flatMap { user ->
                tokenResponse(facade.issueOwnToken(user.principalId, AuditActor.named(user.name, ctx.ip()), request), "Token '${request.name}' already exists")
            }
        }
    }

    @OpenApi(
        path = "/api/identity/me/tokens/{name}/rotate",
        methods = [HttpMethod.POST],
        summary = "Rotate an own token",
        description = "Replaces the secret of an own token. The new secret is returned once, the old one stops working at once",
        tags = ["Identity"],
    )
    private val rotateToken = ReposiliteRoute<CreatedTokenDto>("/api/identity/me/tokens/{name}/rotate", Route.POST) {
        authenticated {
            val caller = callerOf(identifier)
            val name = requireParameter("name")
            response = caller.flatMap { user ->
                tokenResponse(facade.rotateOwnToken(user.principalId, name, AuditActor.named(user.name, ctx.ip())), "")
            }
        }
    }

    @OpenApi(
        path = "/api/identity/me/tokens/{name}",
        methods = [HttpMethod.DELETE],
        summary = "Delete an own token",
        description = "Deletes an own token. It stops working at once",
        tags = ["Identity"],
    )
    private val deleteToken = ReposiliteRoute<Unit>("/api/identity/me/tokens/{name}", Route.DELETE) {
        authenticated {
            val caller = callerOf(identifier)
            val name = requireParameter("name")
            response = caller.flatMap { user ->
                when (facade.deleteOwnToken(user.principalId, name, AuditActor.named(user.name, ctx.ip()))) {
                    OwnTokenOutcome.Deleted -> Unit.asSuccess()
                    else -> notFoundError("Token not found")
                }
            }
        }
    }

    @OpenApi(
        path = "/api/identity/principals/{name}/password",
        methods = [HttpMethod.POST],
        summary = "Reset a user's password",
        description = "Instance admins only. Sets a new password for a user, which must be changed at the next login. Every session of the user ends",
        tags = ["Identity"],
    )
    private val resetPassword = ReposiliteRoute<Unit>("/api/identity/principals/{name}/password", Route.POST) {
        managerOnly {
            val name = requireParameter("name")
            val request = runCatching { body<PasswordResetRequest>() }.getOrNull()
            val replacement = request?.newPassword?.takeIf { it.isNotEmpty() }

            response = if (replacement == null) {
                badRequestError("Password reset requires newPassword")
            } else {
                when (val outcome = facade.resetPassword(name, replacement)) {
                    PasswordOutcome.Changed -> Unit.asSuccess()
                    PasswordOutcome.NotFound -> notFoundError("Principal not found")
                    PasswordOutcome.NotAUser -> badRequestError("Only a user has a password")
                    is PasswordOutcome.InvalidInput -> badRequestError(outcome.problem)
                    PasswordOutcome.WrongCurrentPassword -> badRequestError("Unexpected outcome of a reset: ${outcome::class.simpleName}")
                }
            }
        }
    }

    override val routes = routes(
        changePassword,
        listTokens,
        createToken,
        rotateToken,
        deleteToken,
        resetPassword,
    )

}

private fun passwordResponse(outcome: PasswordOutcome): Result<Unit, ErrorResponse> =
    when (outcome) {
        PasswordOutcome.Changed -> Unit.asSuccess()
        PasswordOutcome.WrongCurrentPassword -> errorResponse(FORBIDDEN, "The current password is wrong")
        is PasswordOutcome.InvalidInput -> badRequestError(outcome.problem)
        PasswordOutcome.NotFound -> notFoundError("Account not found")
        PasswordOutcome.NotAUser -> errorResponse(FORBIDDEN, "Only a user has a password")
    }

private fun tokenResponse(outcome: OwnTokenOutcome, nameTakenMessage: String): Result<CreatedTokenDto, ErrorResponse> =
    when (outcome) {
        is OwnTokenOutcome.Issued -> outcome.dto.asSuccess()
        is OwnTokenOutcome.Rotated -> outcome.dto.asSuccess()
        OwnTokenOutcome.NameTaken -> errorResponse(CONFLICT, nameTakenMessage.ifEmpty { "A token with this name already exists" })
        is OwnTokenOutcome.Invalid -> badRequestError(outcome.problem)
        OwnTokenOutcome.NotFound -> notFoundError("Token not found")
        OwnTokenOutcome.Deleted -> notFoundError("Token not found")
    }
