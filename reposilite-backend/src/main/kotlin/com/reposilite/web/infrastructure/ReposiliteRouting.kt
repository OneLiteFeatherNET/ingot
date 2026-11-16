package com.reposilite.web.infrastructure

import com.reposilite.audit.decision.DecisionLogUnavailableException
import com.reposilite.auth.AuthenticationFacade
import com.reposilite.auth.SignInPath
import com.reposilite.auth.api.Credentials
import com.reposilite.journalist.Journalist
import com.reposilite.shared.ContextDsl
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.extensions.response
import com.reposilite.shared.extensions.uri
import com.reposilite.shared.extractFromHeader
import com.reposilite.shared.unauthorizedError
import com.reposilite.status.FailureFacade
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenType.PRINCIPAL
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.web.infrastructure.ReposiliteDsl.ReposiliteConfiguration
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.api.EnrolmentRoutes
import net.onelitefeather.ingot.identity.login.api.MFA_ENROLMENT_REQUIRED
import net.onelitefeather.ingot.logging.AccessLogHandler
import net.onelitefeather.ingot.identity.session.SessionCookies
import net.onelitefeather.ingot.identity.session.VerifiedSession
import panda.std.reactive.Reference
import panda.std.asSuccess
import panda.std.Result
import io.javalin.community.routing.dsl.DslExceptionHandler
import io.javalin.community.routing.dsl.DslRoute
import io.javalin.community.routing.dsl.DslRouting
import io.javalin.community.routing.dsl.RoutingDslConfiguration
import io.javalin.community.routing.dsl.RoutingDslFactory
import io.javalin.http.Context
import io.javalin.http.ExceptionHandler
import io.javalin.http.Handler
import io.javalin.http.Header
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE
import io.javalin.util.javalinLazy
import com.reposilite.shared.internalServer
import com.reposilite.shared.extensions.error
import com.reposilite.shared.toErrorResponse
import net.onelitefeather.ingot.identity.session.CsrfPolicy
import net.onelitefeather.ingot.identity.session.SESSION_CLAIMS_ATTRIBUTE
import net.onelitefeather.ingot.identity.session.FetchMetadataPolicy
import net.onelitefeather.ingot.identity.session.FetchMetadataSettings

typealias ReposiliteRouting = DslRouting<ReposiliteConfiguration, ReposiliteDslRoute, ReposiliteScope, Unit>
typealias ReposiliteDslRoute = DslRoute<ContextDsl<*>, Unit>
typealias ReposiliteExceptionHandler = DslExceptionHandler<ContextDsl<*>, Exception, Unit>
typealias ReposiliteScope = ContextDsl<*>

class ReposiliteDsl(
    private val routeFactory: (ReposiliteDslRoute) -> Handler,
    private val exceptionRouteFactory: (ReposiliteExceptionHandler) -> ExceptionHandler<Exception>
) : RoutingDslFactory<ReposiliteConfiguration, ReposiliteDslRoute, ContextDsl<*>, Unit> {

    open class ReposiliteConfiguration : RoutingDslConfiguration<ReposiliteDslRoute, ContextDsl<*>, Unit>()

    override fun createConfiguration(): ReposiliteConfiguration =
        ReposiliteConfiguration()

    override fun createHandler(route: ReposiliteDslRoute): Handler =
        routeFactory.invoke(route)

    override fun createExceptionHandler(handler: ReposiliteExceptionHandler): ExceptionHandler<Exception> =
        exceptionRouteFactory.invoke(handler)

}

private fun Context.fetchMetadataRequest(): FetchMetadataPolicy.Request =
    FetchMetadataPolicy.Request(
        method = method().name,
        path = path(),
        hasAuthorizationHeader = header(Header.AUTHORIZATION) != null,
        sessionCookie = cookie(SessionCookies.SESSION),
        refreshCookie = cookie(SessionCookies.REFRESH),
        secFetchSite = header("Sec-Fetch-Site"),
        secFetchMode = header("Sec-Fetch-Mode"),
        secFetchDest = header("Sec-Fetch-Dest"),
    )

/**
 * The refusal of a restricted browser session (scope `enrol`) that calls a route outside its set, see [EnrolmentRoutes]. A request with
 * an Authorization header is not a browser session, so it passes. The verified session is kept on the request, so that the
 * authentication of the route reads it instead of verifying the token again.
 */
private fun enrolmentRefusal(ctx: Context, loginFacade: LoginFacade, basePath: String): ErrorResponse? {
    if (ctx.header(Header.AUTHORIZATION) != null) return null
    val token = ctx.cookie(SessionCookies.SESSION) ?: return null
    val session = loginFacade.verifySession(token)?.also { ctx.attribute(SESSION_CLAIMS_ATTRIBUTE, it) } ?: return null
    return ErrorResponse(FORBIDDEN.code, MFA_ENROLMENT_REQUIRED).takeIf {
        EnrolmentRoutes.refused(session.scope, ctx.method().name, ctx.path(), basePath)
    }
}

private fun Context.csrfRequest(): CsrfPolicy.Request =
    CsrfPolicy.Request(
        method = method().name,
        path = path(),
        hasAuthorizationHeader = header(Header.AUTHORIZATION) != null,
        sessionCookie = cookie(SessionCookies.SESSION),
        refreshCookie = cookie(SessionCookies.REFRESH),
        csrfCookie = cookie(SessionCookies.CSRF),
        csrfHeader = header(SessionCookies.CSRF_HEADER),
    )

fun createReposiliteDsl(
    journalist: Journalist,
    accessTokenFacade: AccessTokenFacade,
    authenticationFacade: AuthenticationFacade,
    failureFacade: FailureFacade,
    loginFacade: LoginFacade,
    basePath: Reference<String>,
    fetchMetadata: FetchMetadataSettings = FetchMetadataSettings.resolve(),
    readOnly: () -> Boolean = { false },
): ReposiliteRouting {
    /**
     * A request with an Authorization header is authenticated by it, as before. Without one, a browser session cookie
     * authenticates the request as its user principal. Anything else is anonymous.
     */
    fun Context.authenticate(): Result<out AccessTokenDto, ErrorResponse> {
        val authorization = header(Header.AUTHORIZATION)
        val sessionToken = cookie(SessionCookies.SESSION)
        if (authorization != null && authorization.startsWith("Bearer ", ignoreCase = true)) {
            // A Bearer secret names its token through its id. The name goes through the same check as Basic, so a
            // wrong secret counts towards the failure limit whether or not it names a token.
            val secret = authorization.substring("Bearer ".length).trim()
            val name = loginFacade.bearerTokenName(secret).orEmpty()
            return authenticationFacade.authenticateByCredentials(
                Credentials(ip = ip(), name = name, secret = secret),
                path = SignInPath.BEARER,
                userAgent = header(Header.USER_AGENT),
            ) { seconds ->
                header("Retry-After", seconds.toString())
            }
        }
        if (authorization == null && sessionToken != null) {
            // The verified claims travel with the request, so that a sensitive action can check the age of the second factor.
            val session = attribute<VerifiedSession>(SESSION_CLAIMS_ATTRIBUTE)
                ?: loginFacade.verifySession(sessionToken)
                ?: return unauthorizedError<AccessTokenDto>("Invalid or expired session")
            return accessTokenFacade.getAccessTokenById(AccessTokenIdentifier(type = PRINCIPAL, value = session.principalId))
                ?.also { attribute(SESSION_CLAIMS_ATTRIBUTE, session) }
                ?.asSuccess()
                ?: unauthorizedError<AccessTokenDto>("Invalid or expired session")
        }
        return extractFromHeader(authorization)
            .map { (name, secret) -> Credentials(ip = ip(), name = name, secret = secret) }
            .flatMap { authenticationFacade.authenticateByCredentials(it, userAgent = header(Header.USER_AGENT)) { seconds -> header("Retry-After", seconds.toString()) } }
    }

    fun Context.toDslContext(): ContextDsl<Any> =
        ContextDsl(
            logger = journalist.logger,
            ctx = this,
            accessTokenFacade = accessTokenFacade,
            // The name of the resolved token goes to the request, where the access record reads it
            authenticationResult = javalinLazy {
                authenticate().also { result -> result.orNull()?.let { attribute(AccessLogHandler.USER_ATTRIBUTE, it.name) } }
            },
            readOnly = readOnly,
        )

    fun ContextDsl<Any>.consumeResponse() {
        response?.also { result ->
            result.onError { journalist.logger.debug("ERR RESULT | ${ctx.method()} ${ctx.uri()} errored with $it") }
            ctx.response(result)
        }
    }

    val dsl = ReposiliteDsl(
        routeFactory = { route ->
            Handler { ctx ->
                try {
                    if (fetchMetadata.enabled && FetchMetadataPolicy.violation(ctx.fetchMetadataRequest(), basePath.get())) {
                        ctx.status(FORBIDDEN)
                        ctx.response(FORBIDDEN.toErrorResponse("Cookie request refused: the request comes from another site"))
                        return@Handler
                    }
                    if (CsrfPolicy.violation(ctx.csrfRequest(), basePath.get())) {
                        ctx.status(FORBIDDEN)
                        ctx.response(FORBIDDEN.toErrorResponse("Cookie request refused: the X-CSRF-Token header does not match the session"))
                        return@Handler
                    }
                    enrolmentRefusal(ctx, loginFacade, basePath.get())?.let { refusal ->
                        ctx.status(FORBIDDEN)
                        ctx.response(refusal)
                        return@Handler
                    }
                    val dsl = ctx.toDslContext()
                    route.handler(dsl)
                    dsl.consumeResponse()
                } catch (unavailable: DecisionLogUnavailableException) {
                    // A sync decision record that could not be stored: the request is retried once the store is back
                    ctx.error(SERVICE_UNAVAILABLE.toErrorResponse(unavailable.message))
                } catch (throwable: Throwable) {
                    failureFacade.throwException(ctx.uri(), throwable)
                    ctx.error(internalServer("Internal server error"))
                }
            }
        },
        exceptionRouteFactory = { exceptionRoute ->
            ExceptionHandler { exception, ctx ->
                try {
                    val dsl = ctx.toDslContext()
                    exceptionRoute.invoke(dsl, exception)
                    dsl.consumeResponse()
                } catch (throwable: Throwable) {
                    failureFacade.throwException(ctx.uri(), throwable)
                    ctx.error(internalServer("Internal server error"))
                }
            }
        }
    )

    return ReposiliteRouting(dsl)
}