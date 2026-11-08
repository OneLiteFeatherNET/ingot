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

import org.slf4j.LoggerFactory
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisClientConfig
import redis.clients.jedis.JedisPubSub
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * The Redis and Valkey transport over plain PUBLISH and SUBSCRIBE on one channel per cluster. The server holds no
 * state for this transport: no lock, counter, key or revision. Each instance has one dedicated connection that
 * subscribes, and publishes on a second connection that is opened when needed.
 *
 * [publish] runs on the outbox thread of the plugin and waits at most for the connect timeout when the broker is down.
 * A failure is reported as a state change, not per message, so an outage does not flood the log.
 */
class JedisClusterTransport(
    private val url: ClusterBusUrl,
    private val channel: String,
    private val log: (String) -> Unit,
    private val backoff: ReconnectBackoff = ReconnectBackoff(),
) : ClusterTransport {

    private val states = ConnectionStateLog(log)
    private val closed = AtomicBoolean(false)
    private val wakeLock = ReentrantLock()
    private val wake = wakeLock.newCondition()
    private val publisherLock = Any()
    private val started = AtomicBoolean(false)
    private var publisher: Jedis? = null
    @Volatile
    private var subscription: JedisPubSub? = null
    @Volatile
    private var state = ConnectionState.RECONNECTING

    /** The state of the subscription: connected, reconnecting while the broker is unreachable, or disabled after close. */
    fun connectionState(): ConnectionState = state

    override fun publish(message: String) {
        if (closed.get()) return
        synchronized(publisherLock) {
            try {
                val connection = publisher ?: connect().also { publisher = it }
                connection.publish(channel, message)
            } catch (exception: Exception) {
                closePublisher()
                setState(ConnectionState.RECONNECTING)
                logger.debug("Cluster | publish failed: ${describe(exception)}")
            }
        }
    }

    /** Starts the subscription thread, once. Connection failures are retried with [ReconnectBackoff]. */
    override fun subscribe(listener: (String) -> Unit) {
        check(started.compareAndSet(false, true)) { "the transport is already subscribed" }
        thread(name = "ingot-cluster-subscriber", isDaemon = true) { receive(listener) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        wakeLock.withLock { wake.signalAll() }
        runCatching { subscription?.unsubscribe() }
        synchronized(publisherLock) { closePublisher() }
        setState(ConnectionState.DISABLED)
    }

    private fun setState(next: ConnectionState) {
        state = next
        states.transition(next)
    }

    private fun receive(listener: (String) -> Unit) {
        var attempt = 0
        setState(ConnectionState.RECONNECTING)
        while (!closed.get()) {
            var connection: Jedis? = null
            val pubSub = object : JedisPubSub() {
                override fun onSubscribe(channel: String, subscribedChannels: Int) {
                    attempt = 0
                    setState(ConnectionState.CONNECTED)
                }

                override fun onMessage(channel: String, message: String) {
                    try {
                        listener(message)
                    } catch (exception: Exception) {
                        logger.warn("Cluster | a received message could not be applied: ${describe(exception)}")
                    }
                }
            }
            subscription = pubSub
            try {
                // Jedis connects in its constructor, so a refused connection is raised here and retried below.
                val opened = connect()
                connection = opened
                opened.subscribe(pubSub, channel)
            } catch (exception: Exception) {
                if (!closed.get()) {
                    logger.debug("Cluster | subscription ended: ${describe(exception)}")
                }
            } finally {
                subscription = null
                connection?.let { runCatching { it.close() } }
            }
            if (closed.get()) break

            setState(ConnectionState.RECONNECTING)
            attempt++
            if (!pause(backoff.delay(attempt))) break
        }
    }

    /** Waits for [delay] unless the transport is closed first. Returns false when it was closed. */
    private fun pause(delay: Duration): Boolean = wakeLock.withLock {
        var remaining = delay.toNanos()
        while (!closed.get() && remaining > 0) {
            remaining = wake.awaitNanos(remaining)
        }
        !closed.get()
    }

    private fun connect(): Jedis =
        Jedis(HostAndPort(url.host, url.port ?: DEFAULT_PORT), clientConfig())

    private fun clientConfig(): JedisClientConfig {
        val builder = DefaultJedisClientConfig.builder()
            .ssl(url.scheme == "rediss" || url.scheme == "valkeys")
            .connectionTimeoutMillis(CONNECT_TIMEOUT_MILLIS)
        url.username?.let { builder.user(it) }
        url.password?.let { builder.password(it) }
        return builder.build()
    }

    private fun closePublisher() {
        runCatching { publisher?.close() }
        publisher = null
    }

    /** The exception class and message only. The message of a connection failure names the host, never the password. */
    private fun describe(exception: Exception): String =
        "${exception.javaClass.simpleName}${exception.message?.let { ": $it" } ?: ""}"

    companion object {
        internal val logger = LoggerFactory.getLogger(JedisClusterTransport::class.java)
        private const val DEFAULT_PORT = 6379
        private const val CONNECT_TIMEOUT_MILLIS = 2000

        internal fun channelOf(clusterIdentifier: String): String = "ingot:$clusterIdentifier:events"
    }
}

/**
 * Opens [JedisClusterTransport] for the schemes it serves. The channel is derived from the cluster identifier, so
 * instances of other installations on the same server never receive these events.
 */
class JedisClusterTransportFactory(
    private val log: (String) -> Unit = { JedisClusterTransport.logger.info(it) },
) : ClusterTransportFactory {

    override fun open(url: ClusterBusUrl, settings: ClusterSettings, clusterIdentifier: String): ClusterTransport =
        JedisClusterTransport(url, JedisClusterTransport.channelOf(clusterIdentifier), log)
}

/** The factories of the schemes served by Jedis. `redis` and `valkey` address the same server, so they share one factory. */
val JedisClusterTransports: Map<String, ClusterTransportFactory> = JedisClusterTransportFactory().let { factory ->
    listOf("redis", "rediss", "valkey", "valkeys").associateWith { factory }
}
