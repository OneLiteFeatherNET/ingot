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

import com.reposilite.audit.api.auditActor
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequestError
import com.reposilite.shared.errorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenType
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import com.reposilite.web.api.SESSION_COOKIE_SCHEME
import com.reposilite.web.api.TOKEN_BASIC_SCHEME
import com.reposilite.web.api.SMALL_JSON_BODY_LIMIT
import io.javalin.community.routing.Route
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.mfa.EnrolmentConfirmation
import net.onelitefeather.ingot.identity.login.mfa.FactorRecord
import net.onelitefeather.ingot.identity.login.mfa.FactorRemoval
import net.onelitefeather.ingot.identity.login.mfa.RecoveryCodesOutcome
import net.onelitefeather.ingot.identity.login.mfa.TotpEnrolmentStart
import net.onelitefeather.ingot.identity.model.PrincipalKind
import panda.std.Result
import panda.std.asSuccess

/**
 * The second factors of a user: authenticator codes and recovery codes. A user manages their own factors, and an
 * instance administrator may list and reset those of any user. Secrets never leave these routes: the authenticator URI is
 * returned once, and the recovery codes are returned once, when they are issued.
 *
 * Changes to the factors ask for a current code of the user, so a session alone cannot weaken the second factor.
 */
internal class SecondFactorEndpoints(
    private val facade: LoginFacade,
    private val accessTokens: AccessTokenFacade,
) : ReposiliteRoutes() {

    private data class Caller(val principalId: Int, val name: String)

    /**
     * The signed-in user of a browser session. An access token is refused, even one of the user: a token is a long-lived
     * secret that Maven, CI or a script keeps, and a second factor must not be reachable with it.
     */
    private fun callerOf(identifier: AccessTokenIdentifier): Result<Caller, ErrorResponse> {
        if (identifier.type != AccessTokenType.PRINCIPAL) {
            return errorResponse(FORBIDDEN, "Second factors are managed from a browser session, not with an access token")
        }
        val subject = accessTokens.subjectOf(identifier)
        val principalId = subject.principalId
        val name = subject.principalName
        return if (principalId == null || name == null || subject.kind != PrincipalKind.USER) {
            errorResponse(FORBIDDEN, "Only a user account can manage its second factors")
        } else {
            Caller(principalId, name).asSuccess()
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        path = "/api/identity/me/factors",
        methods = [HttpMethod.GET],
        summary = "List own second factors",
        description = "The authenticator codes of the signed-in user, without secrets. Pending enrolments are not listed",
        tags = ["Identity"],
        responses = [
            OpenApiResponse(status = "200", description = "The factors of the signed-in user"),
        ],
    )
    private val listOwn = ReposiliteRoute<List<SecondFactorDto>>("/api/identity/me/factors", Route.GET) {
        authenticated {
            response = callerOf(identifier).flatMap { user ->
                facade.secondFactorsOf(user.principalId).map { it.toDto() }.asSuccess()
            }
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        path = "/api/identity/me/factors/totp",
        methods = [HttpMethod.POST],
        summary = "Start an authenticator enrolment",
        description = "Starts an enrolment for the signed-in user and returns the otpauth URI once. The enrolment counts as a factor after /totp/confirm accepts a code. Needs the settings key: without it the answer is 409 with the reason",
        tags = ["Identity"],
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = AuthenticatorEnrolmentDto::class) ]),
            OpenApiResponse(status = "409", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ],
    )
    private val startTotp = ReposiliteRoute<AuthenticatorEnrolmentDto>("/api/identity/me/factors/totp", Route.POST, bodyLimit = SMALL_JSON_BODY_LIMIT) {
        authenticated {
            val caller = callerOf(identifier)
            // A user who has a factor sends a current code of it, see SecondFactorService.enrolmentStart. The body may be empty
            // for a user without a factor.
            val code = runCatching { body<CodeRequest>() }.getOrNull()?.code?.takeIf { it.isNotBlank() }
            response = caller.flatMap { user ->
                when (val started = facade.startAuthenticatorEnrolment(user.principalId, user.name, code, ctx.ip())) {
                    is TotpEnrolmentStart.Started -> AuthenticatorEnrolmentDto(otpauthUri = started.otpauthUri).asSuccess()
                    is TotpEnrolmentStart.Unavailable -> errorResponse(CONFLICT, started.reason)
                    TotpEnrolmentStart.ProofRequired -> errorResponse(FORBIDDEN, "Adding a factor needs a current code of an existing factor")
                    is TotpEnrolmentStart.Blocked -> tooManyRequests(ctx, started.retryAfterSeconds, "Too many failed codes, try again later")
                }
            }
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        path = "/api/identity/me/factors/totp/confirm",
        methods = [HttpMethod.POST],
        summary = "Confirm an authenticator enrolment",
        description = "Confirms the pending enrolment with a code of the authenticator app. The first factor issues ten recovery codes, returned once",
        tags = ["Identity"],
        requestBody = OpenApiRequestBody(content = [ OpenApiContent(from = CodeRequest::class) ]),
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = ConfirmedEnrolmentDto::class) ]),
            OpenApiResponse(status = "400", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "403", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "409", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "429", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ],
    )
    private val confirmTotp = ReposiliteRoute<ConfirmedEnrolmentDto>("/api/identity/me/factors/totp/confirm", Route.POST, bodyLimit = SMALL_JSON_BODY_LIMIT) {
        authenticated {
            val caller = callerOf(identifier)
            val code = runCatching { body<CodeRequest>() }.getOrNull()?.code?.takeIf { it.isNotBlank() }
            response = caller.flatMap { user ->
                if (code == null) {
                    badRequestError("Confirmation requires code")
                } else {
                    when (val outcome = facade.confirmAuthenticatorEnrolment(user.principalId, user.name, code, ctx.ip())) {
                        is EnrolmentConfirmation.Confirmed -> ConfirmedEnrolmentDto(recoveryCodes = outcome.recoveryCodes).asSuccess()
                        EnrolmentConfirmation.NoPendingEnrolment -> errorResponse(CONFLICT, "No authenticator is waiting for a code, start an enrolment first")
                        EnrolmentConfirmation.WrongCode -> errorResponse(FORBIDDEN, "The code is not valid")
                        is EnrolmentConfirmation.Blocked -> tooManyRequests(ctx, outcome.retryAfterSeconds, "Too many failed codes, try again later")
                    }
                }
            }
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        path = "/api/identity/me/factors/{id}",
        methods = [HttpMethod.DELETE],
        summary = "Remove an own second factor",
        description = "Removes one authenticator of the signed-in user. Needs a current code of the user in the body. Removing the last factor removes the recovery codes too",
        tags = ["Identity"],
        pathParams = [OpenApiParam(name = "id", description = "Id of the factor", required = true)],
        requestBody = OpenApiRequestBody(content = [ OpenApiContent(from = CodeRequest::class) ]),
        responses = [
            OpenApiResponse(status = "200", description = "Removed"),
            OpenApiResponse(status = "400", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "403", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "404", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "429", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ],
    )
    private val removeOwn = ReposiliteRoute<Unit>("/api/identity/me/factors/{id}", Route.DELETE, bodyLimit = SMALL_JSON_BODY_LIMIT) {
        authenticated {
            val caller = callerOf(identifier)
            val factorId = requireParameter("id").toIntOrNull()
            val code = runCatching { body<CodeRequest>() }.getOrNull()?.code?.takeIf { it.isNotBlank() }
            response = caller.flatMap { user ->
                when {
                    factorId == null -> notFoundError("Factor not found")
                    code == null -> badRequestError("Removal requires code")
                    else -> when (val outcome = facade.removeSecondFactor(user.principalId, user.name, factorId, code, ctx.ip(), auditActor())) {
                        FactorRemoval.Removed -> Unit.asSuccess()
                        FactorRemoval.NotFound -> notFoundError("Factor not found")
                        FactorRemoval.WrongCode -> errorResponse(FORBIDDEN, "The code is not valid")
                        is FactorRemoval.Blocked -> tooManyRequests(ctx, outcome.retryAfterSeconds, "Too many failed codes, try again later")
                    }
                }
            }
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        path = "/api/identity/me/recovery-codes",
        methods = [HttpMethod.POST],
        summary = "Regenerate recovery codes",
        description = "Replaces the recovery codes of the signed-in user with a new set, returned once. The old codes stop working at once. Needs a current code of the user in the body",
        tags = ["Identity"],
        requestBody = OpenApiRequestBody(content = [ OpenApiContent(from = CodeRequest::class) ]),
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = RecoveryCodesDto::class) ]),
            OpenApiResponse(status = "400", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "403", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "409", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "429", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ],
    )
    private val regenerateRecovery = ReposiliteRoute<RecoveryCodesDto>("/api/identity/me/recovery-codes", Route.POST, bodyLimit = SMALL_JSON_BODY_LIMIT) {
        authenticated {
            val caller = callerOf(identifier)
            val code = runCatching { body<CodeRequest>() }.getOrNull()?.code?.takeIf { it.isNotBlank() }
            response = caller.flatMap { user ->
                if (code == null) {
                    badRequestError("Regeneration requires code")
                } else {
                    when (val outcome = facade.regenerateRecoveryCodes(user.principalId, user.name, code, ctx.ip(), auditActor())) {
                        is RecoveryCodesOutcome.Regenerated -> RecoveryCodesDto(recoveryCodes = outcome.codes).asSuccess()
                        RecoveryCodesOutcome.NoFactor -> errorResponse(CONFLICT, "Recovery codes belong to a second factor, add an authenticator first")
                        RecoveryCodesOutcome.WrongCode -> errorResponse(FORBIDDEN, "The code is not valid")
                        is RecoveryCodesOutcome.Blocked -> tooManyRequests(ctx, outcome.retryAfterSeconds, "Too many failed codes, try again later")
                    }
                }
            }
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        path = "/api/identity/principals/{name}/factors",
        methods = [HttpMethod.GET],
        summary = "List a user's second factors",
        description = "Instance admins only. Names, kinds and times of the factors of a user, never secrets",
        tags = ["Identity"],
        pathParams = [OpenApiParam(name = "name", description = "Name of the user", required = true)],
        responses = [
            OpenApiResponse(status = "200", description = "The factors of the user"),
            OpenApiResponse(status = "404", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ],
    )
    private val listOfPrincipal = ReposiliteRoute<List<SecondFactorDto>>("/api/identity/principals/{name}/factors", Route.GET) {
        managerOnly {
            val name = requireParameter("name")
            response = facade.secondFactorsOfPrincipal(name)
                ?.map { it.toDto() }?.asSuccess()
                ?: notFoundError("Principal not found")
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        path = "/api/identity/principals/{name}/factors",
        methods = [HttpMethod.DELETE],
        summary = "Reset a user's second factors",
        description = "Instance admins only. Removes every second factor and recovery code of a user, and ends the sessions of the user. The next sign-in needs no second factor",
        tags = ["Identity"],
        pathParams = [OpenApiParam(name = "name", description = "Name of the user", required = true)],
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = FactorResetDto::class) ]),
            OpenApiResponse(status = "404", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ],
    )
    private val resetAll = ReposiliteRoute<FactorResetDto>("/api/identity/principals/{name}/factors", Route.DELETE) {
        managerOnly {
            val name = requireParameter("name")
            response = facade.resetSecondFactors(name, factorId = null, actor = auditActor())
                ?.let { FactorResetDto(removed = it).asSuccess() }
                ?: notFoundError("Principal not found")
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        path = "/api/identity/principals/{name}/factors/{id}",
        methods = [HttpMethod.DELETE],
        summary = "Reset one second factor of a user",
        description = "Instance admins only. Removes one factor of a user and ends the sessions of the user",
        tags = ["Identity"],
        pathParams = [
            OpenApiParam(name = "name", description = "Name of the user", required = true),
            OpenApiParam(name = "id", description = "Id of the factor", required = true),
        ],
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = FactorResetDto::class) ]),
            OpenApiResponse(status = "404", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ],
    )
    private val resetOne = ReposiliteRoute<FactorResetDto>("/api/identity/principals/{name}/factors/{id}", Route.DELETE) {
        managerOnly {
            val name = requireParameter("name")
            val factorId = requireParameter("id").toIntOrNull()
            response = factorId
                ?.let { facade.resetSecondFactors(name, factorId = it, actor = auditActor()) }
                ?.takeIf { it > 0 }
                ?.let { FactorResetDto(removed = it).asSuccess() }
                ?: notFoundError("Factor not found")
        }
    }

    override val routes = routes(listOwn, startTotp, confirmTotp, removeOwn, regenerateRecovery, listOfPrincipal, resetAll, resetOne)

    private fun FactorRecord.toDto() = SecondFactorDto(id = id, kind = kind, name = name, createdAt = createdAt, lastUsedAt = lastUsedAt)

}
