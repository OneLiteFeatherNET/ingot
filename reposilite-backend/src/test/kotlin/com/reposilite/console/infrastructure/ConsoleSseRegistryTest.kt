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

package com.reposilite.console.infrastructure

import com.reposilite.scheduling.runAtOnce
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.atomic.AtomicInteger

/**
 * The live log connections are registered by request threads, released by close callbacks and closed at shutdown, all
 * at the same time. A pending ping that never fires keeps each session's future observable, and no test waits on time.
 */
internal class ConsoleSseRegistryTest {

    private val pings: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val released = ConcurrentHashMap<String, AtomicInteger>()
    private val registry = ConsoleSseRegistry<Any> { session -> recordRelease(session) }

    @AfterEach
    fun shutDownPings() {
        pings.shutdownNow()
    }

    @Test
    fun `releases a registered connection once and cancels its ping`() {
        // given: an open connection with a session
        val connection = Any()
        val session = session("deploy-bot")
        registry.register(connection, session) { false }

        // when: the connection is released twice, as a close callback and a shutdown can both do
        registry.release(connection)
        registry.release(connection)

        // then: the session was released once, its ping was cancelled and no connection is left
        assertThat(released.getValue("deploy-bot").get()).`as`("releases of the session").isEqualTo(1)
        assertThat(session.scheduler.isCancelled).`as`("ping cancelled").isTrue()
        assertThat(registry.snapshot()).`as`("registered connections").isEmpty()
    }

    @Test
    fun `keeps an open connection registered until it is released`() {
        // given: a connection that is open when it is registered
        val connection = Any()
        val session = session("deploy-bot")

        // when: it is registered
        val open = registry.register(connection, session) { false }

        // then: it is reported open, and its session and ping stay alive
        assertThat(open).`as`("connection still open after registration").isTrue()
        assertThat(registry.snapshot()).containsExactly(connection)
        assertThat(session.scheduler.isCancelled).`as`("ping cancelled").isFalse()
    }

    @Test
    fun `releases a connection that closed before its registration`() {
        // given: a connection whose close callback ran before the registration, so it found nothing to release
        val connection = Any()
        val session = session("deploy-bot")

        // when: it is registered after it was already closed
        val open = registry.register(connection, session) { true }

        // then: it is released at once instead of leaking its subscriber and ping
        assertThat(open).`as`("connection still open after registration").isFalse()
        assertThat(registry.snapshot()).`as`("registered connections").isEmpty()
        assertThat(released.getValue("deploy-bot").get()).isEqualTo(1)
        assertThat(session.scheduler.isCancelled).isTrue()
    }

    @Test
    fun `releases every connection exactly once when its close callback and the shutdown release it at the same instant`() {
        // given: connections that are registered, each released by two threads at once
        val connections = (1..16).map { Any() }
        connections.forEachIndexed { index, connection -> registry.register(connection, session("bot-$index")) { false } }
        val tasks = connections.flatMap { connection -> listOf<() -> Unit>({ registry.release(connection) }, { registry.release(connection) }) }

        // when: all releases run together
        runAtOnce(tasks)

        // then: every connection was released exactly once, so nothing is left behind
        assertThat(registry.snapshot()).`as`("registered connections").isEmpty()
        assertThat(released.values.sumOf { it.get() }).`as`("releases in total").isEqualTo(connections.size)
        assertThat(released.values.map { it.get() }).`as`("releases per session").containsOnly(1)
    }

    @Test
    fun `closes a snapshot while connections are added and removed concurrently`() {
        // given: connections already registered, and new ones that arrive while the shutdown closes a snapshot
        val existing = (1..8).map { Any() }
        existing.forEachIndexed { index, connection -> registry.register(connection, session("existing-$index")) { false } }
        val incoming = (1..8).map { Any() }

        val tasks = incoming.mapIndexed { index, connection ->
            { registry.register(connection, session("incoming-$index")) { false } }
        } + listOf<() -> Unit>({
            registry.snapshot().forEach { connection -> registry.release(connection) }
        })

        // when: the registrations and the snapshot close run together
        runAtOnce(tasks)

        // then: the snapshot could be iterated without failing, and after the final releases nothing is left
        (existing + incoming).forEach { registry.release(it) }
        assertThat(registry.snapshot()).`as`("registered connections").isEmpty()
        assertThat(released.values.sumOf { it.get() }).`as`("releases in total").isEqualTo(existing.size + incoming.size)
    }

    private fun session(identifier: String): SseSession =
        SseSession(identifier, subscriberId = identifier.hashCode(), scheduler = pings.schedule(Runnable {}, 1, HOURS))

    private fun recordRelease(session: SseSession) {
        released.computeIfAbsent(session.identifier) { AtomicInteger() }.incrementAndGet()
        session.scheduler.cancel(false)
    }

}
