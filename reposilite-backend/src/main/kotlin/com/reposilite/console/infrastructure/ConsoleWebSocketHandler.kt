/*
 * Copyright (c) 2023 dzikoysk
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

import com.reposilite.ReposiliteJournalist
import com.reposilite.auth.api.Credentials
import com.reposilite.console.ConsoleFacade
import com.reposilite.console.consoleCommandAudit
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.extractFromString
import com.reposilite.shared.unauthorizedError
import com.reposilite.token.api.AccessTokenDto
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.websocket.WsConfig
import io.javalin.websocket.WsContext
import panda.std.Result
import panda.std.reactive.Reference
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import net.onelitefeather.ingot.web.TrustedProxies
import java.util.Collections
import java.util.WeakHashMap
import java.util.function.Consumer

private const val AUTHORIZATION_PREFIX = "Authorization:"

/** The session form of the first message, `Authorization:Session <jwt>`. A Bearer secret means a token, see the HTTP API. */
private const val SESSION_PREFIX = "Session "

private data class WsSession(
    val identifier: String,
    val tokenName: String,
    val address: String,
    val subscriberId: Int,
    val outbox: ConsoleOutbox,
)

/** The identity of an authenticated connection: the token name, and the host it connected from. */
private data class WsIdentity(val tokenName: String, val address: String) {
    val identifier: String get() = "$tokenName@$address"
}

internal class ConsoleWebSocketHandler(
    private val journalist: ReposiliteJournalist,
    private val consoleAuthenticator: ConsoleAuthenticator,
    private val consoleFacade: ConsoleFacade,
    private val forwardedIp: Reference<String>,
    private val trustedProxies: TrustedProxies = TrustedProxies.NONE,
    /** Emits an audit event for every command that reaches execution on this channel. */
    private val audit: (AuditEvent) -> Unit,
    /** The clock, the timers and the line writer of each connection. Tests drive them by hand. */
    private val scheduler: ConsoleScheduler = SystemConsoleScheduler(),
) : Consumer<WsConfig> {

    /**
     * Written by the message and close callbacks of every connection, each on its own Jetty thread, so it is synchronized.
     */
    private val users: MutableMap<ConsoleConnection, WsSession> = Collections.synchronizedMap(WeakHashMap())

    @OpenApi(
        path = "/api/console/sock",
        methods = [HttpMethod.PATCH],
        tags = ["Console"]
    )
    override fun accept(ws: WsConfig) {
        ws.onMessage { ctx -> handleMessage(JavalinConsoleConnection(ctx), ctx.message()) }
        ws.onClose { ctx -> release(JavalinConsoleConnection(ctx)) }
        ws.onError { ctx -> release(JavalinConsoleConnection(ctx)) }
    }

    /** Handles one text frame of [connection]: the credentials while it is not authenticated, a command or keep-alive after. */
    internal fun handleMessage(connection: ConsoleConnection, message: String) {
        when (val session = users[connection]) {
            null ->
                authenticateContext(connection, message)
                    .peek { identity ->
                        val identifier = identity.identifier
                        journalist.logger.info("CLI | $identifier accessed remote console")

                        // Subscribers run on the thread that logs, which is often an unrelated request or job, so they only
                        // queue the line. The outbox's own writer sends it, and a client that reads too slowly loses lines
                        // instead of stalling the logs. Its close callback releases the writer and the subscription.
                        val outbox = scheduler.openOutbox(
                            send = { connection.send(it) },
                            onFailure = { failure -> handleSendFailure(connection, identifier, failure) },
                        )

                        val subscriberId = journalist.subscribe { outbox.offer(it.value) }

                        users[connection] = WsSession(identifier, identity.tokenName, identity.address, subscriberId, outbox)

                        journalist.cachedLogger.messages
                            .filter { it.key.priority >= journalist.visibleThreshold.priority }
                            .forEach { cached -> outbox.put(cached.value) }
                    }
                    .onError {
                        journalist.logger.info("CLI | ${it.message} (${it.status})")
                        connection.sendJson(it)
                        connection.disconnect()
                    }
            else ->
                when (message) {
                    "keep-alive" ->
                        session.outbox.put("keep-alive")
                    else ->
                        consoleFacade.prepareCommand(message).peek { command ->
                            journalist.logger.info("CLI | ${session.identifier}> ${command.redactedCommand}")
                            val execution = command.executePlain()
                            audit(
                                consoleCommandAudit(
                                    actor = AuditActor.named(session.tokenName, session.address),
                                    command = command.redactedCommand,
                                    channel = "websocket",
                                    client = null,
                                    confirmed = false,
                                    exitCode = execution.exitCode,
                                )
                            )
                        }

                }
        }
    }

    /** Releases the writer and the subscription of a connection. Both close callbacks may run, and only the first one finds the session. */
    internal fun release(connection: ConsoleConnection) {
        val session = users.remove(connection) ?: return
        session.outbox.close()
        journalist.logger.info("CLI | ${session.identifier} closed connection")
        journalist.unsubscribe(session.subscriberId)
    }

    private fun handleSendFailure(connection: ConsoleConnection, identifier: String, failure: Exception) {
        if (failure is ClosedChannelException) {
            journalist.logger.debug("CLI | $identifier tried to write to closed channel")
        }
        runCatching { connection.disconnect() }
    }

    private fun authenticateContext(connection: ConsoleConnection, authMessage: String): Result<WsIdentity, ErrorResponse> {
        if (!authMessage.startsWith(AUTHORIZATION_PREFIX)) {
            return unauthorizedError("Unauthorized CLI access request from ${connection.host} (missing credentials)")
        }

        val credentials = authMessage.removePrefix(AUTHORIZATION_PREFIX)
        val host = connection.host
        val identity: Result<out AccessTokenDto, ErrorResponse> =
            if (credentials.startsWith(SESSION_PREFIX)) {
                consoleAuthenticator.authenticateSession(credentials.removePrefix(SESSION_PREFIX).trim(), host)
            } else {
                extractFromString(credentials)
                    .map { (name, secret) -> Credentials(ip = host, name = name, secret = secret) }
                    .flatMap { consoleAuthenticator.authenticate(it) }
            }
        return identity.map { WsIdentity(tokenName = it.name, address = host) }
    }

    private fun WsContext.getHost(): String {
        val peer = (session.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress
        if (trustedProxies.enabled && peer != null) {
            return trustedProxies.resolve(peer, forwardedIp.get()?.let { header(it) })
        }
        return forwardedIp.get()?.let { header(it) }
            ?: session.remoteSocketAddress?.toString()
            ?: "unknown host"
    }

    /** A Javalin context as a [ConsoleConnection]. Every callback gets a new context, and contexts of one socket are equal. */
    private inner class JavalinConsoleConnection(private val ctx: WsContext) : ConsoleConnection {
        override val host: String get() = ctx.getHost()
        override fun send(text: String) = ctx.send(text)
        override fun sendJson(value: Any) = ctx.send(value)
        override fun disconnect() = ctx.session.disconnect()
        override fun equals(other: Any?): Boolean = other is JavalinConsoleConnection && other.ctx == ctx
        override fun hashCode(): Int = ctx.hashCode()
    }

}
