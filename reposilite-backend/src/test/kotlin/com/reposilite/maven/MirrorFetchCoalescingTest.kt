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

package com.reposilite.maven

import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.shared.http.FakeRemoteClientProvider
import com.reposilite.shared.http.RemoteClientProvider
import com.reposilite.storage.api.DocumentInfo
import io.javalin.http.ContentType.TEXT_XML
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.asSuccess
import panda.std.Result
import com.reposilite.shared.ErrorResponse

/**
 * Pins how concurrent requests for one artifact reach the upstream mirror. The upstream holds its first answer until the
 * test releases it, so the second request arrives while the first fetch is still in progress. Every wait is bounded and
 * driven by a latch or a queue, never by a sleep.
 */
internal class MirrorFetchCoalescingTest : MavenSpecification() {

    private val upstreamEntries = LinkedBlockingQueue<String>()
    private val releaseUpstream = CountDownLatch(1)
    private val upstreamGets = AtomicInteger()

    override fun repositories() = listOf(
        RepositorySettings("stored-proxy", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = true),
        )),
        RepositorySettings("streamed-proxy", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = false),
        )),
    )

    override fun remoteClientProvider(): RemoteClientProvider = FakeRemoteClientProvider(
        headHandler = { uri, _, _, _ -> DocumentInfo(name = uri.substringAfterLast('/'), contentType = TEXT_XML).asSuccess() },
        getHandler = { uri, _, _, _ -> upstreamAnswer(uri) },
    )

    private fun upstreamAnswer(uri: String): Result<InputStream, ErrorResponse> {
        upstreamGets.incrementAndGet()
        upstreamEntries.offer(uri)
        releaseUpstream.await(BOUNDED_WAIT_SECONDS, TimeUnit.SECONDS)
        return REMOTE_CONTENT.byteInputStream().asSuccess()
    }

    @Test
    fun `should share one upstream fetch between two requests through a storing mirror`() {
        // given: a file that the stored mirror fetches from the upstream
        val file = FileSpec("stored-proxy", "/com/example/shared/1.0/shared-1.0.jar", REMOTE_CONTENT)

        // when: the first request is held inside the upstream fetch, and a second one arrives meanwhile
        val first = Executors.newSingleThreadExecutor()
        val second = Executors.newSingleThreadExecutor()
        try {
            val firstLookup = CompletableFuture.supplyAsync({ mavenFacade.findFile(file.toLookupRequest(UNAUTHORIZED)) }, first)
            assertThat(upstreamEntries.poll(BOUNDED_WAIT_SECONDS, TimeUnit.SECONDS))
                .`as`("the first request reaches the upstream")
                .isNotNull()

            val secondThread = CompletableFuture<Thread>()
            val secondLookup = CompletableFuture.supplyAsync({
                secondThread.complete(Thread.currentThread())
                mavenFacade.findFile(file.toLookupRequest(UNAUTHORIZED))
            }, second)
            awaitWaiting(secondThread.get(BOUNDED_WAIT_SECONDS, TimeUnit.SECONDS))

            releaseUpstream.countDown()

            // then: both requests receive the file, and the upstream was asked once
            val firstResponse = firstLookup.get(BOUNDED_WAIT_SECONDS, TimeUnit.SECONDS)
            val secondResponse = secondLookup.get(BOUNDED_WAIT_SECONDS, TimeUnit.SECONDS)
            assertThat(firstResponse.isOk).`as`("first response").isTrue
            assertThat(secondResponse.isOk).`as`("second response").isTrue
            assertThat(upstreamGets.get()).`as`("upstream requests for one artifact through a storing mirror").isEqualTo(1)
        } finally {
            first.shutdownNow()
            second.shutdownNow()
        }
    }

    @Test
    fun `should send one upstream request per caller through a mirror that does not store`() {
        // given: a file that the streaming mirror fetches from the upstream
        val file = FileSpec("streamed-proxy", "/com/example/streamed/1.0/streamed-1.0.jar", REMOTE_CONTENT)

        // when: the first request is held inside the upstream fetch, and a second one arrives meanwhile
        val first = Executors.newSingleThreadExecutor()
        val second = Executors.newSingleThreadExecutor()
        try {
            val firstLookup = CompletableFuture.supplyAsync({ mavenFacade.findFile(file.toLookupRequest(UNAUTHORIZED)) }, first)
            assertThat(upstreamEntries.poll(BOUNDED_WAIT_SECONDS, TimeUnit.SECONDS))
                .`as`("the first request reaches the upstream")
                .isNotNull()

            val secondLookup = CompletableFuture.supplyAsync({ mavenFacade.findFile(file.toLookupRequest(UNAUTHORIZED)) }, second)

            // then: the second request is not shared with the first one, so it reaches the upstream on its own
            assertThat(upstreamEntries.poll(BOUNDED_WAIT_SECONDS, TimeUnit.SECONDS))
                .`as`("the second request reaches the upstream while the first is still held")
                .isNotNull()

            releaseUpstream.countDown()
            firstLookup.get(BOUNDED_WAIT_SECONDS, TimeUnit.SECONDS)
            secondLookup.get(BOUNDED_WAIT_SECONDS, TimeUnit.SECONDS)
            assertThat(upstreamGets.get()).`as`("upstream requests, one for each caller").isEqualTo(2)
        } finally {
            first.shutdownNow()
            second.shutdownNow()
        }
    }

    /** Waits until the thread is parked, which is where a request waits for the shared fetch it joined. Bounded. */
    private fun awaitWaiting(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BOUNDED_WAIT_SECONDS)
        while (thread.state != Thread.State.WAITING && thread.state != Thread.State.TIMED_WAITING) {
            if (System.nanoTime() > deadline) {
                throw AssertionError("The second request did not wait for the shared fetch within $BOUNDED_WAIT_SECONDS s")
            }
            Thread.onSpinWait()
        }
    }

    private companion object {
        const val BOUNDED_WAIT_SECONDS = 10L
    }

}
