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

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequestError
import com.reposilite.shared.errorResponse
import com.reposilite.shared.unauthorizedError
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import com.reposilite.web.api.SMALL_JSON_BODY_LIMIT
import io.javalin.community.routing.Route
import io.javalin.http.Context
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE
import io.javalin.http.Header
import io.javalin.http.HttpStatus.TOO_MANY_REQUESTS
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginOutcome
import net.onelitefeather.ingot.identity.login.SetupOutcome
import net.onelitefeather.ingot.identity.login.mfa.ChallengeCompletion
import net.onelitefeather.ingot.identity.login.mfa.NO_RELYING_PARTY_MESSAGE
import net.onelitefeather.ingot.identity.login.mfa.PasskeyProof
import net.onelitefeather.ingot.identity.login.mfa.PasskeyStart
import net.onelitefeather.ingot.identity.login.mfa.RelyingParty
import net.onelitefeather.ingot.identity.login.mfa.SecondFactorCookie
import net.onelitefeather.ingot.identity.login.mfa.SecondFactorMethod
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.session.SessionCookies
import net.onelitefeather.ingot.identity.session.SessionTokens
import panda.std.Result
import panda.std.asSuccess
import panda.std.reactive.Reference

/**
 * The login endpoints of a browser: providers, password login, first administrator setup, renewal and logout. Login,
 * setup, renewal and logout set or clear the session cookies, so that the browser never handles a token itself.
 */
internal class AuthLoginEndpoints(
    private val facade: LoginFacade,
    private val basePath: Reference<String>,
    /** `login.publicUrl`, which the relying party of a passkey is taken from when it is set. */
    private val publicUrl: () -> String,
) : ReposiliteRoutes() {

    @OpenApi(
        path = "/api/auth/providers",
        methods = [HttpMethod.GET],
        summary = "List login providers",
        description = "Public. Lists the configured login providers and whether the first admin still has to be set up",
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = LoginProvidersResponse::class) ]),
        ]
    )
    private val providers = ReposiliteRoute<LoginProvidersResponse>("/api/auth/providers", Route.GET) {
        response = LoginProvidersResponse(
            providers = facade.providers().map { LoginProviderDto(id = it.id, type = it.type.name, label = it.label, icon = it.icon, loginUrl = it.loginUrl) },
            tokenLogin = true,
            setupRequired = facade.setupRequired(),
        ).asSuccess()
    }

    @OpenApi(
        path = "/api/auth/login",
        methods = [HttpMethod.POST],
        summary = "Log in with a login provider",
        description = "Checks the credentials of a human user and starts a browser session. Wrong credentials and unknown users get the same answer. A user with an authenticator gets 200 with mfaRequired set instead of a session: the challenge cookie then goes to /api/auth/mfa/verify",
        tags = ["Auth"],
        requestBody = OpenApiRequestBody(content = [ OpenApiContent(from = LoginRequest::class) ]),
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = LoginResponse::class) ]),
            OpenApiResponse(status = "400", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "401", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "403", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "429", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ]
    )
    private val login = ReposiliteRoute<Any>("/api/auth/login", Route.POST, bodyLimit = SMALL_JSON_BODY_LIMIT) {
        val request = runCatching { body<LoginRequest>() }.getOrNull()
        val provider = request?.provider?.takeIf { it.isNotBlank() }
        val username = request?.username?.takeIf { it.isNotBlank() }
        val password = request?.password?.takeIf { it.isNotBlank() }

        response = if (provider == null || username == null || password == null) {
            badRequestError("Login requires provider, username and password")
        } else {
            when (val outcome = facade.login(provider, username, password, ctx.ip(), ctx.header(Header.USER_AGENT))) {
                is LoginOutcome.Succeeded -> startLoginSession(ctx, facade, basePath.get(), outcome.principal, outcome.mustChangePassword)
                is LoginOutcome.SecondFactorRequired -> secondFactorChallenge(ctx, basePath.get(), outcome.challenge, outcome.methods)
                LoginOutcome.Rejected -> unauthorizedError("Invalid username or password")
                LoginOutcome.Disabled -> errorResponse(FORBIDDEN, "This user is disabled")
                is LoginOutcome.Blocked -> tooManyRequests(ctx, outcome.retryAfterSeconds, "Too many failed login attempts, try again later")
                LoginOutcome.UnknownProvider -> badRequestError("Unknown login provider")
                is LoginOutcome.Refused -> errorResponse(FORBIDDEN, outcome.reason.code)
                LoginOutcome.Unavailable -> errorResponse(SERVICE_UNAVAILABLE, "The login provider cannot be reached, try again later")
            }
        }
    }

    @OpenApi(
        path = "/api/auth/mfa/verify",
        methods = [HttpMethod.POST],
        summary = "Complete a sign-in with a second factor",
        description = "Completes the sign-in that the login route started for a user with a second factor. Takes the code from the authenticator app or a recovery code, or a passkey assertion of the options that /api/auth/mfa/passkey/options returned. Needs the challenge cookie. The challenge is single use and expires after five minutes. A wrong code or assertion keeps it open. Starts the browser session on success. A body with a passkey takes it before a code",
        tags = ["Auth"],
        requestBody = OpenApiRequestBody(content = [ OpenApiContent(from = SecondFactorRequest::class) ]),
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = LoginResponse::class) ]),
            OpenApiResponse(status = "400", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "401", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "409", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "429", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ]
    )
    private val verifySecondFactor = ReposiliteRoute<LoginResponse>("/api/auth/mfa/verify", Route.POST, bodyLimit = SMALL_JSON_BODY_LIMIT) {
        val challenge = ctx.cookie(SecondFactorCookie.NAME)
        val request = runCatching { body<SecondFactorRequest>() }.getOrNull()
        val code = request?.code?.takeIf { it.isNotBlank() }

        response = when {
            challenge == null -> unauthorizedError("Sign in with your password first")
            request?.passkey != null -> verifyWithPasskey(ctx, challenge, request.passkey)
            code == null -> badRequestError("Verification requires code")
            else -> finishSecondFactor(ctx, facade.completeSecondFactor(challenge, code, ctx.ip()), rejected = "Invalid code")
        }
    }

    @OpenApi(
        path = "/api/auth/mfa/passkey/options",
        methods = [HttpMethod.POST],
        summary = "Start a passkey sign-in",
        description = "Starts the passkey step of the sign-in that the login route started, and returns the assertion options: the challenge the authenticator signs and the passkeys that may answer. Needs the challenge cookie. The relying party is the public address of the instance, or the request host when login.publicUrl is blank. The options are completed at /api/auth/mfa/verify",
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = PasskeyOptionsDto::class) ]),
            OpenApiResponse(status = "401", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "409", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ]
    )
    private val passkeyOptions = ReposiliteRoute<PasskeyOptionsDto>("/api/auth/mfa/passkey/options", Route.POST, bodyLimit = SMALL_JSON_BODY_LIMIT) {
        val challenge = ctx.cookie(SecondFactorCookie.NAME)

        response = when {
            challenge == null -> unauthorizedError("Sign in with your password first")
            else -> {
                val relyingParty = RelyingParty.of(publicUrl(), secure(ctx), ctx.host())
                when {
                    relyingParty == null -> errorResponse(CONFLICT, NO_RELYING_PARTY_MESSAGE)
                    else -> when (val started = facade.startPasskeySignIn(challenge)) {
                        is PasskeyStart.Started -> PasskeyOptionsDto(
                            challengeId = started.challenge.challengeId,
                            challenge = started.challenge.challenge,
                            rpId = relyingParty.id,
                            allowCredentials = started.challenge.allowedCredentialIds,
                        ).asSuccess()
                        PasskeyStart.Gone -> unauthorizedError("The sign-in expired, start again")
                        PasskeyStart.NoPasskey -> errorResponse(CONFLICT, "No passkey is registered for this account")
                    }
                }
            }
        }
    }

    @OpenApi(
        path = "/api/auth/setup",
        methods = [HttpMethod.GET],
        summary = "Setup status",
        description = "Public. Tells whether the first administrator still has to be created",
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = SetupStatusResponse::class) ]),
        ]
    )
    private val setupStatus = ReposiliteRoute<SetupStatusResponse>("/api/auth/setup", Route.GET) {
        response = SetupStatusResponse(required = facade.setupRequired()).asSuccess()
    }

    @OpenApi(
        path = "/api/auth/setup",
        methods = [HttpMethod.POST],
        summary = "Create the first administrator",
        description = "Creates the first user as an instance administrator, using the one-time setup code from the log, and starts a session. Only while no user exists",
        tags = ["Auth"],
        requestBody = OpenApiRequestBody(content = [ OpenApiContent(from = SetupRequest::class) ]),
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = LoginResponse::class) ]),
            OpenApiResponse(status = "400", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "403", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "409", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "429", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ]
    )
    private val setup = ReposiliteRoute<LoginResponse>("/api/auth/setup", Route.POST, bodyLimit = SMALL_JSON_BODY_LIMIT) {
        if (refuseWhileReadOnly()) return@ReposiliteRoute
        val request = runCatching { body<SetupRequest>() }.getOrNull()
        val code = request?.setupCode?.takeIf { it.isNotBlank() }
        val username = request?.username?.takeIf { it.isNotBlank() }
        val password = request?.password?.takeIf { it.isNotBlank() }

        response = if (request == null || code == null || username == null || password == null) {
            badRequestError("Setup requires setupCode, username and password")
        } else {
            when (val outcome = facade.completeSetup(code, username, password, request.displayName, ctx.ip())) {
                is SetupOutcome.Created -> startLoginSession(ctx, facade, basePath.get(), outcome.principal, mustChangePassword = false)
                SetupOutcome.NotRequired -> errorResponse(FORBIDDEN, "Setup is not required, a user already exists")
                SetupOutcome.AlreadyCompleted -> errorResponse(CONFLICT, "The first administrator was created by another instance of this database, sign in instead")
                SetupOutcome.WrongCode -> errorResponse(FORBIDDEN, "Invalid setup code")
                is SetupOutcome.Blocked -> tooManyRequests(ctx, outcome.retryAfterSeconds, "Too many failed setup attempts, try again later")
                is SetupOutcome.BreachCheckUnavailable -> serviceUnavailable(ctx, outcome.retryAfterSeconds, BREACH_CHECK_UNAVAILABLE)
                SetupOutcome.NameTaken -> errorResponse(CONFLICT, "This user name is already taken")
                is SetupOutcome.InvalidInput -> badRequestError(outcome.problem)
            }
        }
    }

    @OpenApi(
        path = "/api/auth/refresh",
        methods = [HttpMethod.POST],
        summary = "Renew the browser session",
        description = "Exchanges the refresh cookie for a new session. The refresh token is single use: presenting a spent one revokes the whole session",
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = SessionResponse::class) ]),
            OpenApiResponse(status = "401", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ]
    )
    private val refresh = ReposiliteRoute<SessionResponse>("/api/auth/refresh", Route.POST, bodyLimit = SMALL_JSON_BODY_LIMIT) {
        val session = ctx.cookie(SessionCookies.REFRESH)?.let { facade.refreshSession(it, ctx.ip(), ctx.header("User-Agent")) }

        response = if (session == null) {
            clearSessionCookies(ctx, basePath.get())
            unauthorizedError("Invalid or expired session")
        } else {
            writeSessionCookies(ctx, session, basePath.get())
            SessionResponse(principal = session.principal.toLoginDto(), expiresAt = session.sessionExpiresAt).asSuccess()
        }
    }

    @OpenApi(
        path = "/api/auth/logout",
        methods = [HttpMethod.POST],
        summary = "End the browser session",
        description = "Revokes the refresh token family of the session and clears the session cookies",
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "200", description = "Logged out"),
        ]
    )
    private val logout = ReposiliteRoute<Unit>("/api/auth/logout", Route.POST, bodyLimit = SMALL_JSON_BODY_LIMIT) {
        if (refuseWhileReadOnly()) return@ReposiliteRoute
        facade.endSession(ctx.cookie(SessionCookies.REFRESH), ctx.ip())
        clearSessionCookies(ctx, basePath.get())
        response = Unit.asSuccess()
    }

    /**
     * Completes the sign-in with the assertion of a passkey. The relying party is the public address of the instance, so an
     * instance without one refuses the passkey with 409 and the reason.
     */
    private fun verifyWithPasskey(ctx: Context, challenge: String, passkey: PasskeyAssertionDto): Result<LoginResponse, ErrorResponse> {
        val proof = passkey.toProof()
            ?: return badRequestError("Passkey sign-in requires challengeId, credentialId, clientDataJSON, authenticatorData and signature in base64url")
        val relyingParty = RelyingParty.of(publicUrl(), secure(ctx), ctx.host())
            ?: return errorResponse(CONFLICT, NO_RELYING_PARTY_MESSAGE)
        return finishSecondFactor(ctx, facade.completePasskeySecondFactor(challenge, proof, relyingParty.id, relyingParty.origin, ctx.ip()), rejected = PASSKEY_REJECTED)
    }

    /**
     * The answer of a completed second factor: the session on success, the cookie cleared when the challenge is over, and the
     * refusal otherwise. [rejected] is the message of a wrong code or assertion.
     */
    private fun finishSecondFactor(ctx: Context, outcome: ChallengeCompletion, rejected: String): Result<LoginResponse, ErrorResponse> =
        when (outcome) {
            is ChallengeCompletion.Verified -> {
                ctx.res().addHeader("Set-Cookie", SecondFactorCookie.clear(basePath.get(), secure(ctx)))
                startLoginSession(ctx, facade, basePath.get(), outcome.principal, facade.mustChangePassword(outcome.principal.id), mfa = true)
                    .also { if (it.isOk) facade.recordSessionIssued(outcome.principal.name, ctx.ip()) }
            }
            ChallengeCompletion.WrongCode -> unauthorizedError(rejected)
            ChallengeCompletion.Gone -> {
                ctx.res().addHeader("Set-Cookie", SecondFactorCookie.clear(basePath.get(), secure(ctx)))
                unauthorizedError("The sign-in expired, start again")
            }
            is ChallengeCompletion.Blocked -> tooManyRequests(ctx, outcome.retryAfterSeconds, "Too many failed codes, try again later")
        }

    override val routes = routes(providers, login, verifySecondFactor, passkeyOptions, setupStatus, setup, refresh, logout)

}

/** A 429 that tells the client when to try again, as `Retry-After` in whole seconds. */
internal fun <V> tooManyRequests(ctx: Context, retryAfterSeconds: Long, message: String): Result<V, ErrorResponse> {
    ctx.header("Retry-After", retryAfterSeconds.toString())
    return errorResponse(TOO_MANY_REQUESTS, message)
}

/** The answer of a request that the server refuses for now, because a service it depends on does not answer. */
internal fun <V> serviceUnavailable(ctx: Context, retryAfterSeconds: Long, message: String): Result<V, ErrorResponse> {
    ctx.header("Retry-After", retryAfterSeconds.toString())
    return errorResponse(SERVICE_UNAVAILABLE, message)
}

/** The message of a password change or setup that the breached-password service could not check. */
internal const val BREACH_CHECK_UNAVAILABLE = "The breached password check is not reachable, try again later"

/** The answer of a password login that needs a second factor: no session, and the challenge cookie for the verification. */
private fun secondFactorChallenge(ctx: Context, basePath: String, challenge: String, methods: List<SecondFactorMethod>): Result<Any, ErrorResponse> {
    ctx.res().addHeader("Set-Cookie", SecondFactorCookie.set(challenge, basePath, secure(ctx)))
    return SecondFactorRequiredResponse(mfaRequired = true, methods = methods.map { it.wireName() }).asSuccess()
}

/** The message of a passkey that the sign-in did not accept. A wrong assertion keeps the challenge open, as a wrong code does. */
private const val PASSKEY_REJECTED = "The passkey was not accepted"

/** The passkey proof of a request, `null` when a field is missing or not base64url. */
private fun PasskeyAssertionDto.toProof(): PasskeyProof? {
    val ceremony = challengeId?.takeIf { it.isNotBlank() } ?: return null
    val credential = credentialId?.takeIf { decodeBase64Url(it) != null } ?: return null
    return PasskeyProof(
        ceremonyId = ceremony,
        credentialId = credential,
        clientDataJson = decodeBase64Url(clientDataJSON) ?: return null,
        authenticatorData = decodeBase64Url(authenticatorData) ?: return null,
        signature = decodeBase64Url(signature) ?: return null,
    )
}

private fun startLoginSession(
    ctx: Context,
    facade: LoginFacade,
    basePath: String,
    principal: PrincipalRecord,
    mustChangePassword: Boolean,
    mfa: Boolean = false,
): Result<LoginResponse, ErrorResponse> {
    val session = facade.startSession(principal, ctx.ip(), ctx.header("User-Agent"), mfa)
        ?: return errorResponse(SERVICE_UNAVAILABLE, "Browser sessions need the database")
    writeSessionCookies(ctx, session, basePath)
    return LoginResponse(
        principal = principal.toLoginDto(),
        mustChangePassword = mustChangePassword,
        expiresAt = session.sessionExpiresAt,
    ).asSuccess()
}

private fun PrincipalRecord.toLoginDto() = LoginPrincipalDto(
    id = id,
    kind = kind.name,
    name = name,
    displayName = displayName,
)

/** Sets the session, refresh and csrf cookies of a session, in the form the browser keeps them. */
internal fun writeSessionCookies(ctx: Context, session: SessionTokens, basePath: String) {
    SessionCookies.setCookies(session, basePath, secure(ctx)).forEach { ctx.res().addHeader("Set-Cookie", it) }
}

private fun clearSessionCookies(ctx: Context, basePath: String) {
    SessionCookies.clearCookies(basePath, secure(ctx)).forEach { ctx.res().addHeader("Set-Cookie", it) }
}

private fun secure(ctx: Context): Boolean =
    SessionCookies.isSecure(ctx)
