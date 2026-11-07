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

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.ReposiliteRunner.Companion.upstreamReference
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import io.javalin.Javalin
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import panda.std.reactive.ReferenceUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A group whose stored loopback member is a stored proxy. The group's mirror task runs on the IO pool and waits for the
 * member, whose own mirror task needs a second IO thread. With a pool of one thread the second task can never start.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class NestedMirrorWaitIntegrationTest : ReposiliteSpecification() {

    private val ioThreads = 1
    private val responseTimeoutSeconds = 10L

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        ReferenceUtils.setValue(localConfiguration.ioThreadPool, ioThreads)
    }

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { old ->
            old.copy(
                repositories = old.repositories + RepositorySettings(
                    id = "group",
                    proxied = listOf(MirroredRepositorySettings(reference = "proxied-stored", store = true))
                )
            )
        }
    }

    @Test
    fun `should serve a group artifact from a stored member when the IO pool has one thread`() {
        // given: an upstream that serves one artifact
        val gav = "com/example/nested/1.0/nested-1.0.jar"
        val content = "nested-content"
        val started = CountDownLatch(1)
        val upstream = Javalin.start { config ->
            config.jetty.port = 0
            config.events.serverStarted { started.countDown() }
            config.routes.get("/releases/$gav") { ctx -> ctx.result(content) }
        }

        val client = Executors.newSingleThreadExecutor()
        try {
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue
            pointStoredProxyAt(upstream.port())

            // when: the group is asked for the artifact, with a bounded wait for the answer
            val response = client.submit<HttpResponse<String>> { get("$base/group/$gav").asString() }
            val answer = try {
                response.get(responseTimeoutSeconds, TimeUnit.SECONDS)
            } catch (timeout: TimeoutException) {
                fail(
                    "Group request did not finish within $responseTimeoutSeconds s with $ioThreads IO thread: " +
                        "the group's mirror task waits on the IO pool for its member's mirror task, which is queued behind it"
                )
            }

            // then: the group answers with the artifact
            assertThat(answer.status).describedAs("status of the group request").isEqualTo(200)
            assertThat(answer.body).describedAs("body served by the group").isEqualTo(content)
        } finally {
            client.shutdownNow()
            upstream.stop()
        }
    }

    private fun pointStoredProxyAt(port: Int) {
        reposilite.extensions.facade<SharedConfigurationFacade>()
            .getDomainSettings<MavenSettings>()
            .update { old ->
                old.copy(
                    repositories = old.repositories.map { repository ->
                        if (repository.id == "proxied-stored") {
                            repository.copy(proxied = listOf(MirroredRepositorySettings(reference = upstreamReference(port), store = true)))
                        } else {
                            repository
                        }
                    }
                )
            }
    }

}
