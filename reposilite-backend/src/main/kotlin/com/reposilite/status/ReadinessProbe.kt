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

package com.reposilite.status

import com.reposilite.status.api.ReadinessResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * One readiness check. [applies] is false when the check does not count for this instance, such as the cluster
 * check without a bus. [check] answers true when the check passes.
 */
data class ReadinessCheck(
    val name: String,
    val applies: () -> Boolean = { true },
    val check: () -> Boolean,
)

/**
 * Runs the readiness checks and names the failing ones. Every check runs on its own daemon thread and all of them are
 * awaited together for at most [bound], so the answer comes back within that bound even when a backend hangs. A check
 * that is still running from an earlier evaluation counts as failing and is not started again, so a hung backend cannot
 * pile up threads with each probe.
 */
class ReadinessProbe(
    private val checks: List<ReadinessCheck>,
    private val bound: Duration = Duration.ofSeconds(2),
    private val onFailure: (name: String, cause: Throwable?) -> Unit = { _, _ -> },
) {

    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "status-readiness").apply { isDaemon = true }
    }

    private val running = ConcurrentHashMap.newKeySet<String>()

    private val overrides = ConcurrentHashMap<String, () -> Boolean>()

    /**
     * Replaces the answer of the check called [name] until the returned function is called. Used by tests to make one
     * backend fail without touching it.
     */
    fun override(name: String, check: () -> Boolean): () -> Unit {
        overrides[name] = check
        return { overrides.remove(name) }
    }

    fun evaluate(): ReadinessResponse {
        val launched = checks
            .filter { it.applies() }
            .map { check -> check.name to launch(check) }

        val pending = launched.mapNotNull { (_, future) -> future }
        runCatching { CompletableFuture.allOf(*pending.toTypedArray()).get(bound.toMillis(), TimeUnit.MILLISECONDS) }

        val failing = launched.mapNotNull { (name, future) ->
            val outcome: Result<Boolean> = when {
                future == null -> Result.failure(TimeoutException("the check is still running from an earlier evaluation"))
                !future.isDone -> Result.failure(TimeoutException("the check did not answer within $bound"))
                else -> future.get()
            }

            if (outcome.getOrNull() == true) {
                null
            } else {
                onFailure(name, outcome.exceptionOrNull())
                name
            }
        }

        return ReadinessResponse(
            status = if (failing.isEmpty()) "UP" else "DOWN",
            failing = failing
        )
    }

    /** Starts the check, or returns null when an earlier run of the same check is still in flight. */
    private fun launch(check: ReadinessCheck): CompletableFuture<Result<Boolean>>? {
        if (!running.add(check.name)) {
            return null
        }

        return CompletableFuture.supplyAsync({
            try {
                runCatching { overrides[check.name]?.invoke() ?: check.check() }
            } finally {
                running.remove(check.name)
            }
        }, executor)
    }

}
