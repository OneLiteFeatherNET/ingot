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
import com.reposilite.console.ConfirmationNeeded
import com.reposilite.console.ConsoleFacade
import com.reposilite.console.PreparedCommand
import com.reposilite.console.StructuredExecution
import com.reposilite.console.StructuredRun
import com.reposilite.console.CommandStatus
import com.reposilite.journalist.Channel
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
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.nio.channels.ClosedChannelException
import net.onelitefeather.ingot.web.TrustedProxies
import java.util.Collections
import java.util.UUID
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

/**
 * The identity of an authenticated connection: the token name, and the host it connected from. [token] and [sessionJwt]
 * let a version 1 connection check again that the principal may still use the console.
 */
private data class WsIdentity(
    val tokenName: String,
    val address: String,
    val token: AccessTokenDto? = null,
    val sessionJwt: String? = null,
) {
    val identifier: String get() = "$tokenName@$address"
}

/** The protocol version of the envelope protocol. A first frame that is a hello opens it, any other first frame is legacy. */
private const val PROTOCOL_VERSION = 1
private val SUPPORTED_PROTOCOLS = setOf(PROTOCOL_VERSION)

/** How long a dangerous command waits for its confirm before it is declined. */
private val CONFIRM_DEADLINE: Duration = Duration.ofSeconds(120)

/** The largest version 1 frame, in UTF-8 bytes. */
private const val MAX_FRAME_BYTES = 4096

/** The exit code of a declined or unanswered confirmation. Nothing runs. It is the code of the HTTP 409 answer. */
private const val DECLINED_EXIT_CODE = 3

private const val CLOSE_GOING_AWAY = 1001
private const val CLOSE_PROTOCOL_ERROR = 1002
private const val CLOSE_POLICY_VIOLATION = 1008
private const val CLOSE_SLOW_CONSUMER = 1013

private val frameMapper = ObjectMapper()

/** The replay states of a welcome: resumed after the number the client holds, or the whole cache for another boot. */
private const val REPLAY_RESUMED = "resumed"
private const val REPLAY_FULL = "full"

/** A dangerous command that waits for its confirm. [deadline] declines it when no answer arrives in time. */
private class PendingConfirm(val id: String, val prepared: PreparedCommand, val deadline: ConsoleTimer)

/**
 * A version 1 connection after its hello. Its frames go through [outbox], and [subscriberId] names its log subscription.
 * At most one dangerous command waits for its confirm, and every other execute is busy.
 */
private class V1Session(val identity: WsIdentity, val outbox: ConsoleV1Outbox, val subscriberId: Int) {

    private var pending: PendingConfirm? = null

    @Synchronized
    fun isBusy(): Boolean = pending != null

    @Synchronized
    fun hold(confirm: PendingConfirm) {
        pending = confirm
    }

    /** Takes the pending command if it waits for [id], and stops its deadline. Null for any other id. */
    @Synchronized
    fun take(id: String): PendingConfirm? {
        val held = pending?.takeIf { it.id == id } ?: return null
        pending = null
        held.deadline.cancel()
        return held
    }

    /** Drops the pending command without running it, when the connection is released. */
    @Synchronized
    fun dropAll() {
        pending?.deadline?.cancel()
        pending = null
    }

}

/** The JSON object of a version 1 frame, or null when the message is not one. */
private fun frameOf(message: String): JsonNode? {
    if (!message.startsWith("{")) {
        return null
    }
    return try {
        frameMapper.readTree(message)?.takeIf { it.isObject }
    } catch (_: IOException) {
        null
    }
}

private fun JsonNode.text(field: String): String? =
    get(field)?.takeIf { it.isTextual }?.asText()

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
    /** The instance id that a version 1 result names as its node. */
    private val nodeId: String,
    /** The id of this process. A version 1 welcome names it, and a client resumes only within the boot it was given. */
    private val boot: String = UUID.randomUUID().toString(),
) : Consumer<WsConfig> {

    /**
     * Written by the message and close callbacks of every connection, each on its own Jetty thread, so it is synchronized.
     */
    private val users: MutableMap<ConsoleConnection, WsSession> = Collections.synchronizedMap(WeakHashMap())

    /** The version 1 sessions, keyed like [users]. Written by the callbacks of each connection, so it is synchronized. */
    private val v1Sessions: MutableMap<ConsoleConnection, V1Session> = Collections.synchronizedMap(WeakHashMap())

    @OpenApi(
        path = "/api/console/sock",
        methods = [HttpMethod.PATCH],
        tags = ["Console"]
    )
    override fun accept(ws: WsConfig) {
        // No hello deadline on connect: protocol 1 is announced in band by the hello itself, so a socket that is silent so
        // far may be a legacy client, and a legacy socket was never closed for waiting before it authenticates.
        ws.onMessage { ctx -> handleMessage(JavalinConsoleConnection(ctx), ctx.message()) }
        ws.onClose { ctx -> release(JavalinConsoleConnection(ctx)) }
        ws.onError { ctx -> release(JavalinConsoleConnection(ctx)) }
    }

    /** Handles one text frame of [connection]: the credentials while it is not authenticated, a command or keep-alive after. */
    internal fun handleMessage(connection: ConsoleConnection, message: String) {
        v1Sessions[connection]?.let { session ->
            handleVersionOneFrame(connection, session, message)
            return
        }
        if (users[connection] == null && frameOf(message)?.text("type") == "hello") {
            handleHello(connection, message)
            return
        }
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

    /** Stops the version 1 connections when the server stops: each gets an error with the code `shutdown` and is closed with 1001. */
    internal fun shutdown() {
        val connections = synchronized(v1Sessions) { v1Sessions.toMap() }
        connections.forEach { (connection, session) ->
            closeSession(connection, session, CLOSE_GOING_AWAY, ErrorFrame(code = "shutdown", message = "the server is stopping"))
        }
    }

    /**
     * Stops the queue of [session] so nothing follows [error], sends the error and closes with [code]. The close callback
     * releases the subscription.
     */
    private fun closeSession(connection: ConsoleConnection, session: V1Session, code: Int, error: ErrorFrame) {
        session.dropAll()
        session.outbox.close()
        runCatching { connection.sendJson(error) }
        runCatching { connection.close(code, error.code) }
    }

    /**
     * Checks before an execute or an accepted confirm that the principal may still use the console. A revoked token, a removed
     * console area or a session that is no longer valid closes the connection with 1008.
     */
    private fun stillAuthorized(connection: ConsoleConnection, session: V1Session): Boolean {
        val token = session.identity.token ?: return true
        if (consoleAuthenticator.isStillAuthorized(token, session.identity.sessionJwt)) {
            return true
        }
        journalist.logger.info("CLI | ${session.identity.identifier} is no longer authorized for the remote console")
        closeSession(connection, session, CLOSE_POLICY_VIOLATION, ErrorFrame(code = "unauthorized", message = "the credentials are no longer valid"))
        return false
    }

    /** Releases the writer and the subscription of a connection. Both close callbacks may run, and only the first one finds the session. */
    internal fun release(connection: ConsoleConnection) {
        v1Sessions.remove(connection)?.let { session ->
            session.dropAll()
            session.outbox.close()
            journalist.consoleLog.unsubscribe(session.subscriberId)
        }
        val session = users.remove(connection) ?: return
        session.outbox.close()
        journalist.logger.info("CLI | ${session.identifier} closed connection")
        journalist.unsubscribe(session.subscriberId)
    }

    /**
     * Answers a hello: the highest common protocol and the token, checked like the legacy first frame. A resume replays the
     * cached lines after the number the client holds when it is for this boot, and the whole cache otherwise.
     */
    private fun handleHello(connection: ConsoleConnection, message: String) {
        val hello = frameOf(message) ?: return
        val protocol = hello.get("protocols")?.takeIf { it.isArray }
            ?.mapNotNull { if (it.isInt) it.asInt() else null }
            ?.filter { it in SUPPORTED_PROTOCOLS }
            ?.maxOrNull()

        if (protocol == null) {
            connection.sendJson(
                ErrorFrame(
                    code = "unsupported_protocol",
                    message = "no common protocol, this server speaks ${SUPPORTED_PROTOCOLS.sorted()}",
                    supported = SUPPORTED_PROTOCOLS.sorted(),
                )
            )
            connection.close(CLOSE_PROTOCOL_ERROR, "unsupported_protocol")
            return
        }

        authenticateContext(connection, AUTHORIZATION_PREFIX + hello.text("token").orEmpty())
            .peek { identity ->
                journalist.logger.info("CLI | ${identity.identifier} accessed remote console (protocol $protocol)")
                val outbox = scheduler.openFrameOutbox(
                    send = { connection.sendJson(it) },
                    onFailure = { failure -> handleSendFailure(connection, identity.identifier, failure) },
                    onSlowConsumer = { slowConsumer(connection) },
                )
                val resume = hello.get("resume")?.takeIf { it.isObject }
                val resumedSeq = resume?.get("seq")?.takeIf { it.isIntegralNumber }?.asLong()
                val replay = when {
                    resume == null -> null
                    resume.text("boot") == boot && resumedSeq != null -> REPLAY_RESUMED
                    else -> REPLAY_FULL
                }
                // The welcome is queued before the subscription, so the replayed lines follow it.
                outbox.putFrame(WelcomeFrame(protocol = protocol, boot = boot, seq = journalist.consoleLog.lastSequence(), replay = replay))
                val replayFrom: Long? = when (replay) {
                    REPLAY_RESUMED -> resumedSeq
                    REPLAY_FULL -> 0L
                    else -> null
                }
                val threshold = levelOf(hello)
                val subscriberId = journalist.consoleLog.subscribe(from = replayFrom) { line ->
                    if (line.level.priority >= threshold.priority) {
                        outbox.offerLog(LogLine(seq = line.seq, node = line.node ?: nodeId, level = line.level.name, text = line.text))
                    }
                }
                v1Sessions[connection] = V1Session(identity, outbox, subscriberId)
            }
            .onError {
                journalist.logger.info("CLI | ${it.message} (${it.status})")
                connection.sendJson(ErrorFrame(code = "unauthorized", message = it.message))
                connection.close(CLOSE_POLICY_VIOLATION, "unauthorized")
            }
    }

    /** The level of the log lines a hello asks for. It can raise the visible threshold but never lower it. */
    private fun levelOf(hello: JsonNode): Channel {
        val visible = journalist.visibleThreshold
        val requested = hello.text("logLevel")?.let { Channel.of(it.uppercase()).orElse(null) } ?: return visible
        return if (requested.priority > visible.priority) requested else visible
    }

    /** Refuses a connection that does not read its frames: the queue is full of frames, so it is closed with 1013. */
    private fun slowConsumer(connection: ConsoleConnection) {
        connection.sendJson(ErrorFrame(code = "slow_consumer", message = "this connection reads too slowly"))
        connection.close(CLOSE_SLOW_CONSUMER, "slow_consumer")
    }

    /** Handles one frame of a version 1 session: an execute, a confirm or a complete, and an error for anything else. */
    private fun handleVersionOneFrame(connection: ConsoleConnection, session: V1Session, message: String) {
        if (message.toByteArray(StandardCharsets.UTF_8).size > MAX_FRAME_BYTES) {
            session.outbox.putFrame(ErrorFrame(code = "too_long", message = "a frame may hold $MAX_FRAME_BYTES bytes at most"))
            return
        }
        val frame = frameOf(message)
        if (frame == null) {
            session.outbox.putFrame(ErrorFrame(code = "bad_frame", message = "a frame is one JSON object"))
            return
        }
        val id = frame.text("id")
        when (val type = frame.text("type")) {
            "execute" -> handleExecute(connection, session, id, frame)
            "confirm" -> handleConfirm(connection, session, id, frame)
            "complete" -> handleComplete(session, id, frame)
            else -> session.outbox.putFrame(ErrorFrame(code = "unknown_type", message = "unknown frame type ${type ?: "none"}", id = id))
        }
    }

    private fun handleExecute(connection: ConsoleConnection, session: V1Session, id: String?, frame: JsonNode) {
        if (id == null) {
            session.outbox.putFrame(ErrorFrame(code = "bad_frame", message = "an execute needs an id"))
            return
        }
        if (!stillAuthorized(connection, session)) {
            return
        }
        if (session.isBusy()) {
            session.outbox.putFrame(ErrorFrame(code = "busy", message = "a command waits for its confirmation", id = id, retryable = true))
            return
        }
        val confirmed = frame.get("confirm")?.takeIf { it.isBoolean }?.asBoolean() ?: false
        consoleFacade.prepareCommand(frame.text("command").orEmpty())
            .peek { prepared -> runPrepared(connection, session, id, prepared, confirmed) }
            .onError { session.outbox.putFrame(ErrorFrame(code = "bad_command", message = it.message, id = id)) }
    }

    /**
     * Answers a complete at once from the registered commands. It does not wait for a pending confirmation and is never
     * answered busy, because it runs nothing.
     */
    private fun handleComplete(session: V1Session, id: String?, frame: JsonNode) {
        if (id == null) {
            session.outbox.putFrame(ErrorFrame(code = "bad_frame", message = "a complete needs an id"))
            return
        }
        val line = frame.text("line").orEmpty()
        val cursor = frame.get("cursor")?.takeIf { it.isInt }?.asInt() ?: line.length
        val completion = consoleFacade.completeCommand(line, cursor)
        session.outbox.putFrame(
            CompleteFrame(id = id, replace = ReplaceRange(start = completion.start, end = completion.end), candidates = completion.candidates)
        )
    }

    /** Runs the command, or holds a dangerous one until its confirm arrives or its deadline passes. */
    private fun runPrepared(connection: ConsoleConnection, session: V1Session, id: String, prepared: PreparedCommand, confirmed: Boolean) {
        when (val answer = prepared.executeStructured(confirmed)) {
            is StructuredRun -> finish(connection, session, id, prepared, answer.execution, confirmed)
            is ConfirmationNeeded -> {
                val deadline = scheduler.schedule(CONFIRM_DEADLINE) {
                    session.take(id)?.let { decline(connection, session, it) }
                }
                session.hold(PendingConfirm(id, prepared, deadline))
                session.outbox.putFrame(ConfirmFrame(id = id, command = answer.command, reason = answer.reason))
            }
        }
    }

    private fun handleConfirm(connection: ConsoleConnection, session: V1Session, id: String?, frame: JsonNode) {
        val pending = id?.let { session.take(it) }
        if (pending == null) {
            session.outbox.putFrame(ErrorFrame(code = "unknown_id", message = "no command waits for this confirmation", id = id))
            return
        }
        val accept = frame.get("accept")?.takeIf { it.isBoolean }?.asBoolean() ?: false
        if (!accept) {
            decline(connection, session, pending)
            return
        }
        if (!stillAuthorized(connection, session)) {
            return
        }
        when (val answer = pending.prepared.executeStructured(confirmed = true)) {
            is StructuredRun -> finish(connection, session, pending.id, pending.prepared, answer.execution, confirmed = true)
            is ConfirmationNeeded -> session.outbox.putFrame(ErrorFrame(code = "confirmation_required", message = "the command still needs its confirmation", id = pending.id))
        }
    }

    private fun decline(connection: ConsoleConnection, session: V1Session, pending: PendingConfirm) {
        audit(
            consoleCommandAudit(
                actor = AuditActor.named(session.identity.tokenName, session.identity.address),
                command = pending.prepared.redactedCommand,
                channel = "websocket",
                client = null,
                confirmed = false,
                exitCode = DECLINED_EXIT_CODE,
            )
        )
        session.outbox.putFrame(
            ResultFrame(
                id = pending.id,
                status = CommandStatus.FAILED,
                exitCode = DECLINED_EXIT_CODE,
                output = emptyList(),
                data = null,
                node = nodeId,
                confirmed = false,
            )
        )
    }

    private fun finish(connection: ConsoleConnection, session: V1Session, id: String, prepared: PreparedCommand, execution: StructuredExecution, confirmed: Boolean) {
        audit(
            consoleCommandAudit(
                actor = AuditActor.named(session.identity.tokenName, session.identity.address),
                command = prepared.redactedCommand,
                channel = "websocket",
                client = null,
                confirmed = confirmed,
                exitCode = execution.exitCode,
            )
        )
        session.outbox.putFrame(
            ResultFrame(
                id = id,
                status = execution.status,
                exitCode = execution.exitCode,
                output = execution.output,
                data = execution.data,
                node = nodeId,
                confirmed = confirmed,
            )
        )
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
        val sessionJwt = credentials.takeIf { it.startsWith(SESSION_PREFIX) }?.removePrefix(SESSION_PREFIX)?.trim()
        val identity: Result<out AccessTokenDto, ErrorResponse> =
            if (sessionJwt != null) {
                consoleAuthenticator.authenticateSession(sessionJwt, host)
            } else {
                extractFromString(credentials)
                    .map { (name, secret) -> Credentials(ip = host, name = name, secret = secret) }
                    .flatMap { consoleAuthenticator.authenticate(it) }
            }
        return identity.map { WsIdentity(tokenName = it.name, address = host, token = it, sessionJwt = sessionJwt) }
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
        override fun close(code: Int, reason: String) {
            ctx.closeSession(code, reason)
        }

        override fun equals(other: Any?): Boolean = other is JavalinConsoleConnection && other.ctx == ctx
        override fun hashCode(): Int = ctx.hashCode()
    }

}
