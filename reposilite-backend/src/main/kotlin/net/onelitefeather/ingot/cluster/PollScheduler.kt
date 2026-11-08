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

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs [task] at a fixed interval until the returned handle is closed. Injected so that tests call the poll themselves.
 */
internal fun interface PollScheduler {

    fun every(intervalMillis: Long, task: () -> Unit): AutoCloseable
}

/** The production schedule: one daemon thread per transport. A failing task does not end the schedule. */
internal object ExecutorPollScheduler : PollScheduler {

    override fun every(intervalMillis: Long, task: () -> Unit): AutoCloseable {
        val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "ingot-cluster-database-poll").apply { isDaemon = true }
        }
        executor.scheduleWithFixedDelay({ runCatching { task() } }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS)
        return AutoCloseable { executor.shutdownNow() }
    }
}
