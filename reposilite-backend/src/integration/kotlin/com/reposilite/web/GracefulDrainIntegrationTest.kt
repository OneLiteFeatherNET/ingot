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

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.status.api.ReadinessResponse
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.web.ShutdownSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * A drain with a timeout of five seconds and a pre-stop delay of three seconds. The delay is recorded and not waited for.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class GracefulDrainIntegrationTest : GracefulDrainSpecification(
    ShutdownSettings(timeout = Duration.ofSeconds(5), delay = Duration.ofSeconds(3)),
) {

    @Test
    @Timeout(60)
    fun `should complete a request in flight normally once the drain has begun`() {
        // given: a request held in its handler
        val held = startHeldRequest()

        // when: the shutdown starts and the drain begins, then the held request is released
        val shutdown = shutdownInBackground()
        assertThat(drainBegun.await(10, TimeUnit.SECONDS)).`as`("the drain begins").isTrue
        release.countDown()

        // then: the held request completes with its normal response, and the stop finishes
        val response = held.get(10, TimeUnit.SECONDS)
        assertThat(response.status).`as`("status of the held request").isEqualTo(200)
        assertThat(response.body).`as`("body of the held request").isEqualTo("released")
        shutdown.join(TimeUnit.SECONDS.toMillis(10))
        assertThat(shutdown.isAlive).`as`("the shutdown has finished").isFalse
    }

    @Test
    @Timeout(60)
    fun `should answer a new request with 503 once the drain has begun`() {
        // given: a request held in its handler, and a drain that has begun
        val held = startHeldRequest()
        val shutdown = shutdownInBackground()
        assertThat(drainBegun.await(10, TimeUnit.SECONDS)).`as`("the drain begins").isTrue

        // when: a new request arrives during the drain
        val response = Unirest.get("$base/api/status/instance").asString()

        // then: the new request is refused
        assertThat(response.status).`as`("status of a new request during the drain").isEqualTo(503)

        release.countDown()
        held.get(10, TimeUnit.SECONDS)
        shutdown.join(TimeUnit.SECONDS.toMillis(10))
    }

    @Test
    @Timeout(60)
    fun `should keep health up and list draining first in readiness during the drain`() {
        // given: a request held in its handler, and a drain that has begun
        val held = startHeldRequest()
        val shutdown = shutdownInBackground()
        assertThat(drainBegun.await(10, TimeUnit.SECONDS)).`as`("the drain begins").isTrue

        // when: health and readiness are requested during the drain
        val health = Unirest.get("$base/api/status/health").asString()
        val readiness = Unirest.get("$base/api/status/ready").asObject(ReadinessResponse::class.java)

        // then: health stays up so the orchestrator does not restart the instance, and readiness names draining first
        assertThat(health.status).`as`("status of health during the drain").isEqualTo(200)
        assertThat(readiness.status).`as`("status of readiness during the drain").isEqualTo(503)
        assertThat(readiness.body.failing).`as`("failing checks during the drain").startsWith("draining")

        release.countDown()
        held.get(10, TimeUnit.SECONDS)
        shutdown.join(TimeUnit.SECONDS.toMillis(10))
    }

    @Test
    @Timeout(60)
    fun `should report draining on readiness while the pre-stop delay runs`() {
        // when: the instance shuts down with no request in flight
        reposilite.shutdown()

        // then: while the delay ran, readiness already listed draining first and answered 503
        assertThat(readinessDuringDelay.get())
            .`as`("readiness answered during the delay")
            .isEqualTo(503 to listOf("draining"))
    }

    @Test
    @Timeout(60)
    fun `should call the delay once with the configured duration before the drain begins`() {
        // when: the instance shuts down with no request in flight
        reposilite.shutdown()

        // then: the delay ran once, with three seconds, before the drain began
        assertThat(events.toList())
            .`as`("the shutdown calls, in order")
            .containsExactly("delay 3s", "draining")
    }

}
