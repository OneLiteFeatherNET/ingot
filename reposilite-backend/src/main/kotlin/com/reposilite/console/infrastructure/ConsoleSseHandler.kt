package com.reposilite.console.infrastructure

import com.reposilite.ReposiliteJournalist
import com.reposilite.auth.api.Credentials
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.extractFromHeader
import com.reposilite.token.api.AccessTokenDto
import io.javalin.http.Context
import io.javalin.http.Header
import io.javalin.http.sse.SseClient
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import net.onelitefeather.ingot.identity.session.SessionCookies
import panda.std.Result
import panda.std.reactive.Reference
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val SSE_EVENT_NAME = "log"

data class SseSession(
    val identifier: String,
    val subscriberId: Int,
    val scheduler: ScheduledFuture<*>
)

internal class ConsoleSseHandler(
    private val journalist: ReposiliteJournalist,
    private val consoleAuthenticator: ConsoleAuthenticator,
    private val forwardedIp: Reference<String>,
    private val scheduler: ScheduledExecutorService
) {

    /** Request threads register here, close callbacks release here and the shutdown closes everything registered. */
    private val connections = ConsoleSseRegistry<SseClient> { session ->
        session.scheduler.cancel(false)
        journalist.logger.info("CLI | ${session.identifier} closed connection")
        journalist.unsubscribe(session.subscriberId)
    }

    /** Closes every live log connection. The close callbacks release them. */
    fun closeAll() {
        connections.snapshot().forEach(SseClient::close)
    }

    @OpenApi(
        path = "/api/console/log",
        methods = [HttpMethod.GET],
        headers = [OpenApiParam(name = "Authorization", description = "Name and secret provided as basic auth credentials. Without it, the session cookie of a browser login is used.", required = false)],
        description = "Streams the output of logs through an SSE Connection.",
        responses = [
            OpenApiResponse(
                status = "200",
                description = "Continuously sends out the log as messages under the `log` event. Sends a keepalive ping through comments."
            )
        ],
        tags = ["Console"]
    )
    fun handleSseLiveLog(sse: SseClient) {
        sse.keepAlive()
        sse.onClose { connections.release(sse) }

        authenticateContext(sse.ctx())
            .peek { identifier ->
                journalist.logger.info("CLI | $identifier accessed remote console")

                val subscriberId = journalist.subscribe {
                    // stop stack overflow log spam
                    if (!sse.terminated()) {
                        sse.sendEvent(SSE_EVENT_NAME, it.value)
                    }
                }

                val watcher = scheduler.scheduleWithFixedDelay({
                    sse.sendComment("ping")
                }, 5, 5, TimeUnit.SECONDS)

                if (!connections.register(sse, SseSession(identifier, subscriberId, watcher)) { sse.terminated() }) {
                    return@peek
                }

                journalist.cachedLogger.messages
                    .filter { it.key.priority >= journalist.visibleThreshold.priority }
                    .forEach { message -> sse.sendEvent(SSE_EVENT_NAME, message.value) }
            }
            .onError {
                journalist.logger.info("CLI | ${it.message} (${it.status})")
                sse.sendEvent(SSE_EVENT_NAME, it)
                sse.close()
            }
    }

    /**
     * A request with an Authorization header is authenticated by it, as before. A browser cannot set that header on an
     * EventSource, so without one the session cookie of the login authenticates the stream.
     */
    private fun authenticateContext(connection: Context): Result<String, ErrorResponse> {
        val ip = connection.ip()
        val authorization = connection.header(Header.AUTHORIZATION)
        val sessionToken = connection.cookie(SessionCookies.SESSION)
        val identity: Result<out AccessTokenDto, ErrorResponse> =
            if (authorization == null && sessionToken != null) {
                consoleAuthenticator.authenticateSession(sessionToken, ip)
            } else {
                extractFromHeader(authorization)
                    .map { (name, secret) -> Credentials(ip = ip, name = name, secret = secret) }
                    .flatMap { consoleAuthenticator.authenticate(it) }
            }
        return identity.map { "${it.name}@$ip" }
    }
}
