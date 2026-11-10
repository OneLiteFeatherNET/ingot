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
import com.reposilite.ReposiliteObjectMapper
import com.reposilite.ReposiliteRunner
import com.reposilite.ReposiliteSpecification
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.shared.ErrorResponse
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.RoutingSetupEvent
import io.javalin.community.routing.Route.GET
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * An instance that admits one request at a time. A route holds its request until the test releases it, so the test
 * decides when the slot is taken and when it is free, and no test waits for a time.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RequestLimitIntegrationTest : ReposiliteSpecification() {

    private val blockedEntered = CountDownLatch(1)

    private val release = CountDownLatch(1)

    private val blockedRoute = ReposiliteRoute<String>("/api/limit/blocked", GET) {
        blockedEntered.countDown()
        release.await(30, TimeUnit.SECONDS)
        ctx.result("released")
    }

    override fun maxConcurrentRequests(): Int? =
        1

    override fun beforeLaunch(reposilite: Reposilite) {
        reposilite.extensions.registerEvent { event: RoutingSetupEvent -> event.register(blockedRoute) }
    }

    @AfterEach
    fun releaseHeldRequest() {
        release.countDown()
    }

    @Test
    @Timeout(60)
    fun `should refuse a request above the limit with 503 and Retry-After`() {
        // given: the only slot is taken by a held request
        val held = startHeldRequest()

        // when: another request arrives
        val response = Unirest.get("$base/api/status/instance")
            .basicAuth(ReposiliteRunner.DEFAULT_TOKEN.first, ReposiliteRunner.DEFAULT_TOKEN.second)
            .asString()

        // then: it is refused at once with the retry hint and the error body
        assertThat(response.status).`as`("status above the limit").isEqualTo(503)
        assertThat(response.headers.getFirst("Retry-After")).`as`("Retry-After above the limit").isEqualTo("1")
        val error = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readValue(response.body, ErrorResponse::class.java)
        assertThat(error.status).`as`("status in the error body").isEqualTo(503)
        assertThat(error.message).`as`("message in the error body").isNotBlank()

        release.countDown()
        held.get(10, TimeUnit.SECONDS)
    }

    @Test
    @Timeout(60)
    fun `should answer the liveness probe while the limit is reached`() {
        // given: the only slot is taken by a held request
        val held = startHeldRequest()

        // when: the liveness probe is requested
        val health = Unirest.get("$base/api/status/health").asString()

        // then: the probe is answered normally
        assertThat(health.status).`as`("status of the liveness probe at the limit").isEqualTo(200)

        release.countDown()
        held.get(10, TimeUnit.SECONDS)
    }

    @Test
    @Timeout(60)
    fun `should serve a request again once the held request has completed`() {
        // given: the held request has completed
        val held = startHeldRequest()
        release.countDown()
        held.get(10, TimeUnit.SECONDS)

        // when: a request arrives after the slot is free
        val response = Unirest.get("$base/api/status/instance")
            .basicAuth(ReposiliteRunner.DEFAULT_TOKEN.first, ReposiliteRunner.DEFAULT_TOKEN.second)
            .asString()

        // then: it is served normally
        assertThat(response.status).`as`("status after the slot is free").isEqualTo(200)
    }

    @Test
    @Timeout(60)
    fun `should answer a refusal as problem details when the client prefers them`() {
        // given: the only slot is taken by a held request
        val held = startHeldRequest()

        // when: a refused request names the problem media type first
        val response: HttpResponse<String> = Unirest.get("$base/api/status/instance")
            .header("Accept", "application/problem+json, application/json;q=0.5")
            .asString()

        // then: the refusal is answered as problem details
        assertThat(response.status).`as`("status above the limit").isEqualTo(503)
        assertThat(response.headers.getFirst("Content-Type"))
            .`as`("content type with a problem preference")
            .startsWith("application/problem+json")

        release.countDown()
        held.get(10, TimeUnit.SECONDS)
    }

    /** Sends a request to the held route and returns once the server has taken it into its handler. */
    private fun startHeldRequest(): CompletableFuture<HttpResponse<String>> {
        val request = CompletableFuture.supplyAsync {
            Unirest.get("$base/api/limit/blocked").asString()
        }

        assertThat(blockedEntered.await(10, TimeUnit.SECONDS))
            .`as`("the held request reaches its handler")
            .isTrue
        return request
    }

}
