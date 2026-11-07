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

@file:Suppress("FunctionName")

package com.reposilite.maven

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenIntegrationSpecification
import java.util.concurrent.atomic.AtomicInteger
import kong.unirest.core.HeaderNames.CONTENT_LENGTH
import kong.unirest.core.HeaderNames.CONTENT_TYPE
import kong.unirest.core.HeaderNames.LAST_MODIFIED
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.head
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Head requests answer from the file details alone. The group lists a hosted repository and the plain proxy by id, so
 * the group reaches the proxy through its loopback reference, which is the path a group takes for every member.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class MavenHeadIntegrationTest : MavenIntegrationSpecification() {

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + RepositorySettings(
                    id = "head-group",
                    type = RepositoryType.GROUP,
                    proxied = listOf(
                        MirroredRepositorySettings(reference = "releases"),
                        MirroredRepositorySettings(reference = "proxied"),
                    ),
                )
            )
        }
    }

    @Test
    fun `should answer head request for hosted artifact with the length and type of get request`() {
        // given: a hosted artifact
        val gav = "com/reposilite/hosted-head.jar"
        useDocument("releases", "com/reposilite", "hosted-head.jar", "hosted-content", store = true)

        // when: the artifact is requested with head and with get
        val headResponse = head("$base/releases/$gav").asEmpty()
        val getResponse = get("$base/releases/$gav").asString()

        // then: head answers with the same length and type as get
        assertThat(headResponse.isSuccess).describedAs("head status ${headResponse.status}").isTrue
        assertThat(headResponse.headers.getFirst(CONTENT_LENGTH)).isEqualTo("hosted-content".length.toString())
        assertThat(headResponse.headers.getFirst(CONTENT_TYPE)).isEqualTo(getResponse.headers.getFirst(CONTENT_TYPE))
        assertThat(headResponse.headers.getFirst(LAST_MODIFIED)).isEqualTo(getResponse.headers.getFirst(LAST_MODIFIED))
    }

    @Test
    fun `should not fetch the body from a proxy on head request`() {
        // given: an upstream that serves the artifact and counts the get requests it receives
        val gav = "com/reposilite/head-proxied.jar"
        val getRequests = AtomicInteger()
        val upstream = startProxiedHost { config ->
            config.routes.head("/releases/$gav") { ctx ->
                ctx.contentType("application/java-archive").header(CONTENT_LENGTH, "10").status(200)
            }
            config.routes.get("/releases/$gav") { ctx ->
                getRequests.incrementAndGet()
                ctx.result("0123456789")
            }
        }

        try {
            // when: a head request for the artifact reaches the proxy
            val response = head("$base/proxied/$gav").asEmpty()

            // then: the proxy answers with the headers of the upstream
            assertThat(response.isSuccess).describedAs("head status ${response.status}").isTrue
            assertThat(response.headers.getFirst(CONTENT_LENGTH)).isEqualTo("10")
            assertThat(response.headers.getFirst(CONTENT_TYPE)).isEqualTo("application/java-archive")

            // and: the upstream is never asked for the body
            assertThat(getRequests.get()).describedAs("get requests sent to the upstream").isZero
        } finally {
            upstream.stop()
        }
    }

    @Test
    fun `should not fetch the body from a proxy member on head request to a group`() {
        // given: an upstream that serves the artifact and counts the get requests it receives
        val gav = "com/reposilite/head-group-proxied.jar"
        val getRequests = AtomicInteger()
        val upstream = startProxiedHost { config ->
            config.routes.head("/releases/$gav") { ctx ->
                ctx.contentType("application/java-archive").header(CONTENT_LENGTH, "10").status(200)
            }
            config.routes.get("/releases/$gav") { ctx ->
                getRequests.incrementAndGet()
                ctx.result("0123456789")
            }
        }

        try {
            // when: a head request for the artifact reaches the group
            val response = head("$base/head-group/$gav").asEmpty()

            // then: the group answers with the headers of the upstream
            assertThat(response.isSuccess).describedAs("head status ${response.status}").isTrue
            assertThat(response.headers.getFirst(CONTENT_LENGTH)).isEqualTo("10")
            assertThat(response.headers.getFirst(CONTENT_TYPE)).isEqualTo("application/java-archive")

            // and: the upstream is never asked for the body through the group
            assertThat(getRequests.get()).describedAs("get requests sent to the upstream").isZero
        } finally {
            upstream.stop()
        }
    }

    @Test
    fun `should answer head request to a group with not found when no member holds the artifact`() {
        // given: an upstream without the artifact
        val upstream = startProxiedHost { _ -> }

        try {
            // when: a head request for the missing artifact reaches the group
            val response = head("$base/head-group/com/reposilite/head-missing.jar").asEmpty()

            // then: the group answers with not found
            assertThat(response.status).describedAs("head status").isEqualTo(404)
        } finally {
            upstream.stop()
        }
    }

}
