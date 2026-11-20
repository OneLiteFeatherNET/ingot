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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.enrichment

import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** The deadline of one enrichment call (artifact enrichment, D4 and D5). */
internal val ARTIFACT_ENRICHMENT_DEADLINE: Duration = Duration.ofSeconds(5)

/** The calls that may wait in the queue of the enrichment pool before a call is skipped as busy (artifact enrichment, D5). */
internal const val ARTIFACT_ENRICHMENT_QUEUE_CAPACITY: Int = 64

/** The single thread of the enrichment pool, with a bounded queue. One call runs at a time for the whole pipeline. */
internal fun artifactEnrichmentPool(): ExecutorService = ThreadPoolExecutor(
    1,
    1,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(ARTIFACT_ENRICHMENT_QUEUE_CAPACITY),
    { runnable -> Thread(runnable, "ingot-artifact-enrichment").apply { isDaemon = true } },
)

/**
 * Runs one enricher call on [pool] and waits at most [deadline], measured on [clock]. The result is never an exception of the
 * enricher: a throw becomes [EnrichmentFailed] with the simple name of its class, a deadline becomes a timeout with the call
 * cancelled, and a refused submission becomes [EnrichmentSkipped] with reason "busy". An [Error] is not caught.
 */
internal class ArtifactEnrichmentExecutor(
    private val pool: ExecutorService,
    private val clock: Clock,
    private val deadline: Duration = ARTIFACT_ENRICHMENT_DEADLINE,
) {

    fun call(enricher: ArtifactEnricher, request: ArtifactEnrichmentRequest): EnrichmentResult {
        val started = clock.millis()
        val future = try {
            pool.submit(Callable { invoke(enricher, request) })
        } catch (refused: RejectedExecutionException) {
            return EnrichmentSkipped("busy")
        }
        val remaining = deadline.toMillis() - (clock.millis() - started)
        return try {
            future.get(remaining.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        } catch (timeout: TimeoutException) {
            future.cancel(true)
            EnrichmentFailed("timeout")
        } catch (failure: ExecutionException) {
            val cause = failure.cause
            if (cause is Error) throw cause
            EnrichmentFailed("exception: ${cause?.javaClass?.simpleName ?: "unknown"}")
        }
    }

    private fun invoke(enricher: ArtifactEnricher, request: ArtifactEnrichmentRequest): EnrichmentResult =
        try {
            enricher.enrich(request)
        } catch (failure: Exception) {
            EnrichmentFailed("exception: ${failure.javaClass.simpleName}")
        }

}
