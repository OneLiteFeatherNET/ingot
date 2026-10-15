package com.reposilite.web.infrastructure

import com.reposilite.auth.AuthenticationFacade
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
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.web.infrastructure.ReposiliteDsl.ReposiliteConfiguration
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.session.SessionCookies
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
import io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.javalin.util.javalinLazy
import com.reposilite.shared.toErrorResponse
import net.onelitefeather.ingot.identity.session.CsrfPolicy

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
): ReposiliteRouting {
    /**
     * A request with an Authorization header is authenticated by it, as before. Without one, a browser session cookie
     * authenticates the request as its user principal. Anything else is anonymous.
     */
    fun Context.authenticate(): Result<out AccessTokenDto, ErrorResponse> {
        val authorization = header(Header.AUTHORIZATION)
        val sessionToken = cookie(SessionCookies.SESSION)
        if (authorization == null && sessionToken != null) {
            return loginFacade.authenticateSession(sessionToken)
                ?.let { accessTokenFacade.getAccessTokenById(it) }
                ?.asSuccess()
                ?: unauthorizedError<AccessTokenDto>("Invalid or expired session")
        }
        return extractFromHeader(authorization)
            .map { (name, secret) -> Credentials(ip = ip(), name = name, secret = secret) }
            .flatMap { authenticationFacade.authenticateByCredentials(it) }
    }

    fun Context.toDslContext(): ContextDsl<Any> =
        ContextDsl(
            logger = journalist.logger,
            ctx = this,
            accessTokenFacade = accessTokenFacade,
            authenticationResult = javalinLazy { authenticate() }
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
                    if (CsrfPolicy.violation(ctx.csrfRequest())) {
                        ctx.status(FORBIDDEN)
                        ctx.response(FORBIDDEN.toErrorResponse("Cookie request refused: the X-CSRF-Token header does not match the session"))
                        return@Handler
                    }
                    val dsl = ctx.toDslContext()
                    route.handler(dsl)
                    dsl.consumeResponse()
                } catch (throwable: Throwable) {
                    failureFacade.throwException(ctx.uri(), throwable)
                    ctx.status(INTERNAL_SERVER_ERROR)
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
                    ctx.status(INTERNAL_SERVER_ERROR)
                }
            }
        }
    )

    return ReposiliteRouting(dsl)
}