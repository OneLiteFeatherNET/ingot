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
package net.onelitefeather.ingot.cli

import java.io.PrintStream
import java.time.Duration
import java.util.Locale

/**
 * The waits between reconnect attempts, as the dashboard has them: 1 second, doubling, at most 30 seconds, each moved by
 * a jitter of up to a fifth either way and never above 30 seconds. [random] gives a number from 0 to 1, and a test fixes it.
 */
class Backoff(private val random: () -> Double = Math::random) {

    private var attempt = 0

    fun next(): Duration {
        val step = minOf(CAP_MS, START_MS shl minOf(attempt, MAX_SHIFT))
        attempt = minOf(attempt + 1, MAX_SHIFT)
        return Duration.ofMillis(minOf(CAP_MS, (step * (0.8 + 0.4 * random())).toLong()))
    }

    fun reset() {
        attempt = 0
    }

    private companion object {
        const val START_MS = 1000L
        const val CAP_MS = 30_000L

        /** 1 second shifted this far is past the cap already. */
        const val MAX_SHIFT = 5
    }

}

/**
 * Opens a new session after the old one dropped. It waits one backoff step before each attempt, so a server that is
 * stopping is not asked again at once, and it tries again for as long as the server cannot be reached. A refusal ends
 * the attempts and is thrown. [pause] waits; a test records the waits instead.
 */
internal class Reconnector(
    private val backoff: Backoff,
    private val pause: (Duration) -> Unit,
    private val err: PrintStream,
    private val open: (ConsoleResume?) -> ConsoleSocket,
) {

    fun reconnect(resume: ConsoleResume?): ConsoleSocket {
        while (true) {
            val wait = backoff.next()
            err.println("Reconnecting in ${String.format(Locale.ROOT, "%.1f", wait.toMillis() / 1000.0)} s. Ctrl-C leaves.")
            pause(wait)
            try {
                return open(resume).also { backoff.reset() }
            } catch (_: ServerUnreachableException) {
                continue
            } catch (_: ConsoleSocketLostException) {
                continue
            }
        }
    }

}
