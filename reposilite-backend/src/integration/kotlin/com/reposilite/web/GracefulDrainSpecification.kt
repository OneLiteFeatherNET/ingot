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

package com.reposilite.web

import com.reposilite.Reposilite
import com.reposilite.ReposiliteRunner
import com.reposilite.ReposiliteSpecification
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.api.ReadinessResponse
import com.reposilite.web.api.HttpServerDrainingEvent
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.RoutingSetupEvent
import io.javalin.community.routing.Route.GET
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.web.ShutdownSettings
import org.junit.jupiter.api.AfterEach
import java.time.Duration
import java.util.Queue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.assertj.core.api.Assertions.assertThat

/**
 * An instance with one route that holds its request until the test releases it. The test decides when a request is
 * in flight and when it finishes, so no test waits for a time. The drain timeline is recorded in [events], in the order
 * the shutdown performs it, and the pre-stop delay is a recorder that does not wait.
 */
internal abstract class GracefulDrainSpecification(
    private val settings: ShutdownSettings,
) : ReposiliteSpecification() {

    protected val inMemoryLog = InMemoryLogger()

    /** Calls the shutdown made, in order: the delay with its duration, and the drain beginning. */
    protected val events: Queue<String> = ConcurrentLinkedQueue()

    /** The readiness answer served while the pre-stop delay runs, as its status and failing checks. */
    protected val readinessDuringDelay = AtomicReference<Pair<Int, List<String>>?>(null)

    protected val blockedEntered = CountDownLatch(1)

    /** Released by the test. The held request waits for it, and [releaseHeldRequests] frees it when a test ends. */
    protected val release = CountDownLatch(1)

    protected val drainBegun = CountDownLatch(1)

    private val blockedRoute = ReposiliteRoute<String>("/api/drain/blocked", GET) {
        blockedEntered.countDown()
        release.await(30, TimeUnit.SECONDS)
        ctx.result("released")
    }

    override fun rootLogger(): Logger =
        inMemoryLog

    override fun shutdownSettings(): ShutdownSettings =
        settings

    override fun awaitDelay(duration: Duration) {
        events.add("delay ${duration.seconds}s")
        Unirest.get("$base/api/status/ready")
            .asObject(ReadinessResponse::class.java)
            .let { readinessDuringDelay.set(it.status to it.body.failing) }
    }

    override fun beforeLaunch(reposilite: Reposilite) {
        reposilite.extensions.registerEvent { event: RoutingSetupEvent -> event.register(blockedRoute) }
        reposilite.extensions.registerEvent { _: HttpServerDrainingEvent ->
            events.add("draining")
            drainBegun.countDown()
        }
    }

    @AfterEach
    fun releaseHeldRequests() {
        release.countDown()
    }

    /** Sends a request to the held route and returns once the server has taken it into its handler. */
    protected fun startHeldRequest(): CompletableFuture<HttpResponse<String>> {
        val request = CompletableFuture.supplyAsync {
            Unirest.get("$base/api/drain/blocked")
                .basicAuth(ReposiliteRunner.DEFAULT_TOKEN.first, ReposiliteRunner.DEFAULT_TOKEN.second)
                .asString()
        }

        assertThat(blockedEntered.await(10, TimeUnit.SECONDS))
            .`as`("the held request reaches its handler")
            .isTrue
        return request
    }

    /** Starts the shutdown on its own thread, because it waits for the requests in flight. */
    protected fun shutdownInBackground(): Thread =
        Thread { reposilite.shutdown() }.apply { start() }

}
