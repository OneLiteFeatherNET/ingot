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

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** A console socket that records what the handler sends. Equal only to itself, like one real socket. */
internal class FakeConsoleConnection(override val host: String = "203.0.113.7") : ConsoleConnection {

    val texts = mutableListOf<String>()
    val json = mutableListOf<Any>()
    var disconnected = false
        private set
    var closeCode: Int? = null
        private set
    var closeReason: String? = null
        private set

    override fun send(text: String) {
        texts += text
    }

    override fun sendJson(value: Any) {
        json += value
    }

    override fun disconnect() {
        disconnected = true
    }

    override fun close(code: Int, reason: String) {
        closeCode = code
        closeReason = reason
    }

}

/**
 * A scheduler on a clock that only moves when [advance] is called. Timers run on the calling thread once their time has
 * come, and every outbox sends its lines at once on the thread that queues them.
 */
internal class FakeConsoleScheduler(start: Instant = Instant.EPOCH) : ConsoleScheduler {

    private class Pending(val at: Instant, val task: () -> Unit)

    private var now = start
    private val pending = mutableListOf<Pending>()
    val outboxes = mutableListOf<DirectOutbox>()

    override val clock: Clock
        get() = Clock.fixed(now, ZoneOffset.UTC)

    override fun schedule(delay: Duration, task: () -> Unit): ConsoleTimer {
        val timer = Pending(now.plus(delay), task)
        pending += timer
        return ConsoleTimer { pending.remove(timer) }
    }

    /** Moves the clock by [duration] and runs the timers that became due, earliest first. */
    fun advance(duration: Duration) {
        now = now.plus(duration)
        while (true) {
            val due = pending.filter { !it.at.isAfter(now) }.minByOrNull { it.at } ?: return
            pending.remove(due)
            due.task()
        }
    }

    override fun openOutbox(send: (String) -> Unit, onFailure: (Exception) -> Unit): ConsoleOutbox =
        DirectOutbox(send, onFailure).also { outboxes += it }

}

/** An outbox without a queue: every line is sent at once, and a failed send is reported like the real writer does. */
internal class DirectOutbox(private val send: (String) -> Unit, private val onFailure: (Exception) -> Unit) : ConsoleOutbox {

    var closed = false
        private set

    override fun offer(line: String): Boolean {
        if (closed) {
            return false
        }
        try {
            send(line)
        } catch (failure: Exception) {
            onFailure(failure)
        }
        return true
    }

    override fun put(line: String) {
        offer(line)
    }

    override fun close() {
        closed = true
    }

}
