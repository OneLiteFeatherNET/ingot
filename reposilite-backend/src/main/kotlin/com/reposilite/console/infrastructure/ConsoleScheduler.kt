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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS

/** The lines of one connection on their way to the client. [ConsoleSocketOutbox] is the production one. */
internal interface ConsoleOutbox {

    /** Queues [line] without waiting. Returns false when the line was dropped. */
    fun offer(line: String): Boolean

    /** Queues [line], waiting for room if needed. */
    fun put(line: String)

    /** Stops delivery. Safe to call more than once. */
    fun close()

}

/** A timer started by [ConsoleScheduler.schedule]. */
internal fun interface ConsoleTimer {
    fun cancel()
}

/**
 * Time and deferred work of the console socket: the clock, the timers, and the writer that delivers the lines of each
 * connection. Injected into [ConsoleWebSocketHandler] so tests drive all three by hand instead of waiting on time.
 */
internal interface ConsoleScheduler {

    val clock: Clock

    /** Runs [task] once after [delay], unless the returned timer is cancelled first. */
    fun schedule(delay: Duration, task: () -> Unit): ConsoleTimer

    /** Opens the started outbox of one connection. [send] writes a line to the client, [onFailure] reports a failed write. */
    fun openOutbox(send: (String) -> Unit, onFailure: (Exception) -> Unit): ConsoleOutbox

}

/** The system clock, one daemon timer thread created on first use, and a [ConsoleSocketOutbox] with its own writer per connection. */
internal class SystemConsoleScheduler(override val clock: Clock = Clock.systemUTC()) : ConsoleScheduler {

    private val timers: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { Thread(it, "ingot-console-socket-timer").apply { isDaemon = true } }
    }

    override fun schedule(delay: Duration, task: () -> Unit): ConsoleTimer {
        val future = timers.schedule(task, delay.toMillis(), MILLISECONDS)
        return ConsoleTimer { future.cancel(false) }
    }

    override fun openOutbox(send: (String) -> Unit, onFailure: (Exception) -> Unit): ConsoleOutbox =
        ConsoleSocketOutbox(send = send, onFailure = onFailure).also { it.start() }

}
