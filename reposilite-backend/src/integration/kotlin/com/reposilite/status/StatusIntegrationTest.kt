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

@file:Suppress("FunctionName")

package com.reposilite.status

import com.reposilite.ExperimentalLocalSpecificationJunitExtension
import com.reposilite.ExperimentalRemoteSpecificationJunitExtension
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.RecommendedRemoteSpecificationJunitExtension
import com.reposilite.VERSION
import com.reposilite.status.api.FailuresResponse
import com.reposilite.status.api.HealthResponse
import com.reposilite.status.api.InstanceStatusResponse
import com.reposilite.status.api.StatusSnapshot
import com.reposilite.status.specification.StatusIntegrationSpecification
import com.reposilite.token.AccessTokenPermission.MANAGER
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.UNAUTHORIZED
import kong.unirest.core.Unirest.delete
import kong.unirest.core.Unirest.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant

@ExtendWith(ExperimentalRemoteSpecificationJunitExtension::class)
internal class ExperimentalRemoteStatusIntegrationTest : StatusIntegrationTest()

@ExtendWith(RecommendedRemoteSpecificationJunitExtension::class)
internal class RemoteStatusIntegrationTest : StatusIntegrationTest()

@ExtendWith(ExperimentalLocalSpecificationJunitExtension::class)
internal class ExperimentalLocalStatusIntegrationTest : StatusIntegrationTest()

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalStatusIntegrationTest : StatusIntegrationTest()

internal abstract class StatusIntegrationTest : StatusIntegrationSpecification() {

    @Test
    fun `should respond with instance status`() {
        // when: status service is requested without valid credentials
        val unauthorizedResponse = get("$base/api/status/instance").asString()

        // then: service rejects request
        assertThat(unauthorizedResponse.status).isEqualTo(UNAUTHORIZED.code)

        // given: a valid credentials
        val (name, secret) = useAuth("name", "secret", listOf(MANAGER))

        // when: service is requested with valid credentials
        val response = get("$base/api/status/instance")
            .basicAuth(name, secret)
            .asObject(InstanceStatusResponse::class.java)

        // then: service should respond with valid instance dto
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body.version).isEqualTo(VERSION)
    }

    @Test
    fun `should respond with status snapshots`() {
        // when: status service is requested without valid credentials
        val unauthorizedResponse = get("$base/api/status/snapshots").asString()

        // then: service rejects request
        assertThat(unauthorizedResponse.status).isEqualTo(UNAUTHORIZED.code)

        // given: a valid credentials
        val (name, secret) = useAuth("name", "secret", listOf(MANAGER))

        // when: service is requested with valid credentials
        val response = get("$base/api/status/snapshots")
            .basicAuth(name, secret)
            .asJacksonObject(Array<StatusSnapshot>::class)

        // then: service should respond with valid instance dto
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body.getOrNull(0)).isNotNull
    }

    @Test
    fun `should expose unauthenticated health endpoint`() {
        // when: health service is requested without credentials
        val response = get("$base/api/status/health").asObject(HealthResponse::class.java)

        // then: it responds with 200 and a healthy status
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body.status).isEqualTo("UP")
    }

    @Test
    fun `should reject failure listing without credentials and without manager permission`() {
        // when: failures are requested without credentials
        val anonymousResponse = get("$base/api/status/failures").asString()

        // then: service rejects the request
        assertThat(anonymousResponse.status).isEqualTo(UNAUTHORIZED.code)

        // given: a token without management permission
        val (name, secret) = useAuth("unauthorized-token", "secret")

        // when: failures are requested with that token
        val response = get("$base/api/status/failures").basicAuth(name, secret).asString()

        // then: service rejects the request
        assertThat(response.status).isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `should list recorded failures with their details`() {
        // given: no failures recorded yet, and one failure reported by the server
        val failures = useFacade<FailureFacade>()
        failures.clearFailures()
        failures.throwException("/api/maven/details", IllegalStateException("repository is broken"))
        val (name, secret) = useAuth("name", "secret", listOf(MANAGER))

        // when: failures are requested by a manager
        val response = get("$base/api/status/failures")
            .basicAuth(name, secret)
            .asJacksonObject(FailuresResponse::class)

        // then: the failure is listed with its source, type, message, count and a parseable timestamp
        assertThat(response.status).isEqualTo(OK.code)
        val failure = response.body.failures.single()
        assertThat(failure.source).isEqualTo("/api/maven/details")
        assertThat(failure.type).isEqualTo("IllegalStateException")
        assertThat(failure.message).isEqualTo("repository is broken")
        assertThat(failure.count).isEqualTo(1)
        assertThat(failure.stacktrace).contains("  at ")
        assertThat(Instant.parse(failure.timestamp)).isNotNull
    }

    @Test
    fun `should clear recorded failures on request of a manager`() {
        // given: a recorded failure and a manager token
        val failures = useFacade<FailureFacade>()
        failures.clearFailures()
        failures.throwException("/api/maven/details", RuntimeException("boom"))
        val (name, secret) = useAuth("name", "secret", listOf(MANAGER))

        // when: failures are cleared by a manager
        val deleteResponse = delete("$base/api/status/failures").basicAuth(name, secret).asString()

        // then: the request succeeds and no failure is left
        assertThat(deleteResponse.status).isEqualTo(OK.code)
        assertThat(failures.hasFailures()).isFalse
    }

}