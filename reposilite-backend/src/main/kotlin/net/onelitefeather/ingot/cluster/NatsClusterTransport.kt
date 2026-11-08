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

package net.onelitefeather.ingot.cluster

import io.nats.client.AuthenticationException
import io.nats.client.Connection
import io.nats.client.ConnectionListener
import io.nats.client.ErrorListener
import io.nats.client.Nats
import io.nats.client.Options
import io.nats.client.ReconnectDelayHandler
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Opens the NATS transport for the schemes `nats` and `tls`. [NatsClusterTransport] is the only class that refers to the
 * client library, so the library is touched only when one of these schemes is configured.
 */
class NatsClusterTransportFactory : ClusterTransportFactory {

    override fun open(url: ClusterBusUrl, settings: ClusterSettings, clusterIdentifier: String): NatsClusterTransport =
        NatsClusterTransport(url, settings, clusterIdentifier).also { it.start() }
}

/**
 * Core publish and subscribe over NATS, on the subject `ingot.<cluster>.events`. Core messages are not persisted: a message
 * published during an outage is lost, and the database remains the source of truth.
 *
 * [open] never waits for the broker. The connection is made on a background thread and retried with [backoff]. Once it is
 * established, the client keeps reconnecting with the same delays. [publish] hands the message to the client, which buffers
 * it while the broker is away. Credentials come from [ClusterSettings.credentialsFile] or from the URL. They never reach a
 * log line: only the connection state and the exception type are logged.
 */
class NatsClusterTransport internal constructor(
    private val url: ClusterBusUrl,
    private val settings: ClusterSettings,
    cluster: String,
    private val backoff: ReconnectBackoff = ReconnectBackoff(),
    private val log: (String) -> Unit = { LOGGER.info(it) },
) : ClusterTransport {

    /** The subject all instances of one cluster publish to and read from. */
    val subject: String = "ingot.$cluster.events"

    init {
        require(SAFE_CLUSTER_IDENTIFIER.matches(cluster)) { "cluster identifier is not usable in a subject" }
    }

    private val lock = ReentrantLock()
    private val stateChanged = lock.newCondition()
    private val closedSignal = CountDownLatch(1)
    private val stateLog = ConnectionStateLog(log)
    private val connection = AtomicReference<Connection?>()

    // Guarded by lock.
    private var state = ConnectionState.RECONNECTING
    private var subscribed = false
    private var closed = false

    // Read by the client's dispatcher thread, so it is volatile rather than guarded.
    @Volatile
    private var listener: ((String) -> Unit)? = null

    /** The connection state as last reported by the client: connected, reconnecting or disabled. */
    fun connectionState(): ConnectionState = lock.withLock { state }

    internal fun start() {
        // Built here rather than on the connect thread, so that an unreadable credentials file fails the open.
        val options = options()
        stateLog.transition(ConnectionState.RECONNECTING)
        thread(name = "ingot-cluster-nats", isDaemon = true) { connectUntilClosed(options) }
    }

    /**
     * Waits until the state is [expected], for at most [timeout]. Returns `false` when the bound passes first.
     */
    fun awaitState(expected: ConnectionState, timeout: Duration): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()
        lock.withLock {
            while (state != expected) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) return false
                stateChanged.awaitNanos(remaining)
            }
            return true
        }
    }

    /**
     * Waits until the broker has processed everything sent so far, subscriptions included, so that a message published
     * afterwards reaches this instance. Returns `false` while there is no connection or when the bound passes first.
     */
    fun flushSubscriptions(timeout: Duration): Boolean {
        val current = connection.get() ?: return false
        return try {
            current.flush(timeout)
            true
        } catch (exception: TimeoutException) {
            false
        } catch (exception: IllegalStateException) {
            false
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    override fun publish(message: String) {
        val current = connection.get() ?: throw IllegalStateException("the cluster bus is not connected")
        current.publish(subject, message.toByteArray(StandardCharsets.UTF_8))
    }

    override fun subscribe(listener: (String) -> Unit) {
        this.listener = listener
        connection.get()?.let { ensureSubscription(it) }
    }

    override fun close() {
        val current = lock.withLock {
            if (closed) return
            closed = true
            closedSignal.countDown()
            connection.getAndSet(null)
        }
        changeState(ConnectionState.DISABLED)
        try {
            current?.close()
        } catch (exception: Exception) {
            log("Cluster | the bus connection could not be closed cleanly: ${exception.javaClass.simpleName}")
        }
    }

    private fun options(): Options {
        val builder = Options.Builder()
            .server("nats://${url.host}${url.port?.let { ":$it" } ?: ""}")
            .connectionName("ingot-${settings.instanceId}")
            .maxReconnects(-1)
            .reconnectDelayBehavior(Options.ReconnectDelayBehavior.BeforeAllRounds)
            .reconnectDelayHandler(ReconnectDelayHandler { attempt -> backoff.delay(attempt.coerceAtLeast(1).toInt()) })
            .connectionListener(ConnectionListener { current, event -> onEvent(current, event) })
            .errorListener(reportingErrors())

        if (url.scheme == "tls") {
            builder.secure()
        }
        val credentials = settings.credentialsFile
        when {
            credentials != null -> builder.authHandler(Nats.credentials(credentials.toString()))
            url.username != null -> builder.userInfo(url.username.toCharArray(), (url.password ?: "").toCharArray())
            url.password != null -> builder.token(url.password.toCharArray())
        }
        return builder.build()
    }

    private fun connectUntilClosed(options: Options) {
        var attempt = 1
        while (!isClosed()) {
            try {
                adopt(Nats.connectReconnectOnConnect(options))
                return
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } catch (exception: IOException) {
                log("Cluster | the bus is not reachable yet (${exception.javaClass.simpleName}), retrying")
                val delay = backoff.delay(attempt++)
                if (closedSignal.await(delay.toMillis(), TimeUnit.MILLISECONDS)) return
            }
        }
    }

    private fun adopt(current: Connection) {
        val accepted = lock.withLock {
            if (closed) {
                false
            } else {
                connection.set(current)
                true
            }
        }
        if (accepted) {
            ensureSubscription(current)
        } else {
            current.close()
        }
    }

    /**
     * Creates the one subscription of this instance once both a connection and a listener exist. The claim is taken under the
     * lock; the client call runs outside it, because the client calls back into [onEvent] on its own threads. The client
     * re-sends the subscription after every reconnect by itself.
     */
    private fun ensureSubscription(current: Connection) {
        val claimed = lock.withLock {
            if (subscribed || listener == null) {
                false
            } else {
                subscribed = true
                true
            }
        }
        if (claimed) {
            current.createDispatcher { message -> deliver(message.data) }.subscribe(subject)
        }
    }

    private fun deliver(data: ByteArray) {
        val receive = listener ?: return
        try {
            receive(String(data, StandardCharsets.UTF_8))
        } catch (exception: RuntimeException) {
            log("Cluster | a received message could not be applied: ${exception.javaClass.simpleName}")
        }
    }

    private fun onEvent(current: Connection, event: ConnectionListener.Events) {
        if (!isClosed()) {
            connection.compareAndSet(null, current)
        }
        when (event) {
            ConnectionListener.Events.CONNECTED, ConnectionListener.Events.RECONNECTED -> {
                connection.get()?.let { ensureSubscription(it) }
                changeState(ConnectionState.CONNECTED)
            }
            ConnectionListener.Events.DISCONNECTED -> changeState(ConnectionState.RECONNECTING)
            ConnectionListener.Events.CLOSED -> if (!isClosed()) changeState(ConnectionState.DISABLED)
            else -> Unit
        }
    }

    private fun reportingErrors() = object : ErrorListener {
        override fun exceptionOccurred(current: Connection, exception: Exception) {
            // A client exception message can contain the server address, so only the exception type is logged.
            if (exception is AuthenticationException) {
                log("Cluster | the bus refused the credentials, check the cluster credentials file or password")
            } else {
                log("Cluster | the bus client reported ${exception.javaClass.simpleName}")
            }
        }
    }

    private fun changeState(next: ConnectionState) {
        lock.withLock {
            if (state == next) return
            state = next
            stateChanged.signalAll()
        }
        stateLog.transition(next)
    }

    private fun isClosed(): Boolean =
        lock.withLock { closed }

    private companion object {
        val LOGGER = LoggerFactory.getLogger(NatsClusterTransport::class.java)

        /** Cluster identifiers are UUIDs. Anything that could change the subject is refused. */
        val SAFE_CLUSTER_IDENTIFIER = Regex("[A-Za-z0-9-]{1,64}")
    }
}
