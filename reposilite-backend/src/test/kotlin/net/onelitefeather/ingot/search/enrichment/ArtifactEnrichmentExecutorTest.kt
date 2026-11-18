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

import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit.MILLISECONDS

internal class ArtifactEnrichmentExecutorTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `a normal return of the enricher is the result of the call`() {
        val executor = executor(FakePool.Behaviour.RUN)

        val result = executor.call(enricher { EnrichmentSkipped("not a zip") }, request())

        assertThat(result).`as`("result of a normal return").isEqualTo(EnrichmentSkipped("not a zip"))
    }

    @Test
    fun `a completed enricher yields completed`() {
        val executor = executor(FakePool.Behaviour.RUN)

        val result = executor.call(enricher { EnrichmentCompleted }, request())

        assertThat(result).`as`("result of a completed enricher").isEqualTo(EnrichmentCompleted)
    }

    @Test
    fun `an exception becomes failed with the simple name of its class and never its message`() {
        val executor = executor(FakePool.Behaviour.RUN)

        val result = executor.call(enricher { throw IllegalStateException("content of com/secret.jar") }, request())

        assertThat(result).`as`("result of a throwing enricher").isEqualTo(EnrichmentFailed("exception: IllegalStateException"))
    }

    @Test
    fun `an error of the enricher propagates to the caller`() {
        val executor = executor(FakePool.Behaviour.RUN)

        assertThatThrownBy { executor.call(enricher { throw OutOfMemoryError("heap") }, request()) }
            .`as`("an Error thrown by the enricher")
            .isInstanceOf(OutOfMemoryError::class.java)
    }

    @Test
    fun `a call that passes its deadline is failed with timeout and cancelled`() {
        val pool = FakePool(FakePool.Behaviour.TIME_OUT)
        val executor = ArtifactEnrichmentExecutor(pool, clock, deadline = java.time.Duration.ofSeconds(5))

        val result = executor.call(enricher { EnrichmentCompleted }, request())

        assertThat(result).`as`("result of a call past its deadline").isEqualTo(EnrichmentFailed("timeout"))
        assertThat(pool.lastFuture?.cancelled).`as`("the timed out call is cancelled").isTrue()
    }

    @Test
    fun `the wait of the call is the deadline measured on the injected clock`() {
        val pool = FakePool(FakePool.Behaviour.RUN)
        val executor = ArtifactEnrichmentExecutor(pool, clock, deadline = java.time.Duration.ofSeconds(5))

        executor.call(enricher { EnrichmentCompleted }, request())

        assertThat(pool.lastFuture?.waitedMillis).`as`("milliseconds waited for the call").isEqualTo(5_000L)
    }

    @Test
    fun `a call refused by a full queue is skipped as busy and the enricher is never called`() {
        val pool = FakePool(FakePool.Behaviour.REJECT)
        val executor = ArtifactEnrichmentExecutor(pool, clock)
        var calls = 0

        val result = executor.call(enricher { calls++; EnrichmentCompleted }, request())

        assertThat(result).`as`("result of a refused call").isEqualTo(EnrichmentSkipped("busy"))
        assertThat(calls).`as`("calls of the enricher after a refusal").isZero()
    }

    @Test
    fun `the enrichment pool has one thread and a queue of 64 calls`() {
        val pool = artifactEnrichmentPool() as java.util.concurrent.ThreadPoolExecutor
        try {
            assertThat(pool.queue.remainingCapacity()).`as`("free places of the queue of an idle pool").isEqualTo(64)
            assertThat(pool.maximumPoolSize).`as`("maximum threads of the pool").isEqualTo(1)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun executor(behaviour: FakePool.Behaviour) = ArtifactEnrichmentExecutor(FakePool(behaviour), clock)

    private fun enricher(body: (ArtifactEnrichmentRequest) -> EnrichmentResult) = object : ArtifactEnricher {
        override val id: String = "jar-manifest"
        override val apiVersion: Int = ARTIFACT_ENRICHER_API_VERSION
        override val version: String = "1"
        override val extensions: Set<String> = setOf("jar")
        override fun enrich(artifact: ArtifactEnrichmentRequest): EnrichmentResult = body(artifact)
    }

    private fun request() = ArtifactEnrichmentRequest(
        repository = "releases",
        path = "org/example/app/1.0/app-1.0.jar",
        file = ArtifactFileRecord(
            repository = "releases",
            path = "org/example/app/1.0/app-1.0.jar",
            groupId = "org.example",
            artifactId = "app",
            version = "1.0",
            classifier = null,
            extension = "jar",
            snapshot = false,
        ),
        origin = Origin.HOSTED,
        sourceHost = null,
        sizeBytes = 0L,
        lastModified = 0L,
        sha1 = null,
        sha256 = null,
        trigger = EnrichmentTrigger.CHANGED,
        content = object : ArtifactContent {
            override val sizeBytes: Long = 0L
            override fun range(offset: Long, length: Long) = java.io.ByteArrayInputStream(ByteArray(0))
        },
    )

    /** A pool that runs, times out or refuses every call on command. Nothing waits on a clock. */
    private class FakePool(private val behaviour: Behaviour) : AbstractExecutorService() {

        enum class Behaviour { RUN, TIME_OUT, REJECT }

        var lastFuture: ScriptedFuture<*>? = null

        override fun <T> submit(task: Callable<T>): Future<T> {
            if (behaviour == Behaviour.REJECT) throw RejectedExecutionException("queue full")
            val outcome = if (behaviour == Behaviour.RUN) runCatching { task.call() } else null
            return ScriptedFuture<T>(outcome).also { lastFuture = it }
        }

        override fun execute(command: Runnable) = throw UnsupportedOperationException("not used by the executor")
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown(): Boolean = false
        override fun isTerminated(): Boolean = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true

    }

    /** A future that is done when it has an [outcome]; otherwise a wait with a timeout reports a timeout at once. */
    internal class ScriptedFuture<T>(private val outcome: Result<T>?) : Future<T> {

        var cancelled = false
            private set

        var waitedMillis: Long? = null
            private set

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            cancelled = true
            return true
        }

        override fun isCancelled(): Boolean = cancelled

        override fun isDone(): Boolean = outcome != null

        override fun get(): T = throw UnsupportedOperationException("the executor waits with a deadline")

        override fun get(timeout: Long, unit: TimeUnit): T {
            waitedMillis = unit.toMillis(timeout)
            val result = outcome ?: throw TimeoutException("not done within the deadline")
            result.exceptionOrNull()?.let { throw ExecutionException(it) }
            return result.getOrThrow()
        }

    }

}
