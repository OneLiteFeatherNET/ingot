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

package com.reposilite.cleanup

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.RecommendedRemoteSpecificationJunitExtension
import com.reposilite.cleanup.infrastructure.CleanupPreviewResponse
import com.reposilite.maven.specification.MavenIntegrationSpecification
import com.reposilite.storage.api.Location
import io.javalin.Javalin
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalCleanupMirrorCacheIntegrationTest : CleanupMirrorCacheIntegrationTest()

@ExtendWith(RecommendedRemoteSpecificationJunitExtension::class)
internal class RemoteCleanupMirrorCacheIntegrationTest : CleanupMirrorCacheIntegrationTest()

/**
 * The repository `proxied-stored` mirrors an upstream and stores what it fetches, so it is a repository that hosts
 * deployed artifacts and also holds mirror copies. The copies are recorded by the mirror store, the deployment is not.
 */
internal abstract class CleanupMirrorCacheIntegrationTest : MavenIntegrationSpecification() {

    private val repository = "proxied-stored"
    private val artifact = "com/example/app"
    private val upstreamFiles = listOf(
        "$artifact/1.0/app-1.0.pom",
        "$artifact/1.0/app-1.0.jar",
        "$artifact/1.1/app-1.1.pom",
        "$artifact/1.1/app-1.1.jar",
    )

    @Test
    fun `should plan only the older mirror copy and never the deployed version of a mixed repository`() {
        // given: two mirror copies fetched through the storing mirror, and a deployed 2.0 in the same repository
        val upstream = serveUpstream(upstreamFiles)
        try {
            upstreamFiles.forEach { Unirest.get("$base/$repository/$it").asEmpty() }
            useDocument(repository, "$artifact/2.0", "app-2.0.pom", "<project/>", store = true)
            useDocument(repository, "$artifact/2.0", "app-2.0.jar", "hosted-jar", store = true)
            configureKeepNewestMirrorPolicy()
            val (token, secret) = useDefaultManagementToken()

            // when: the policy is previewed
            val preview = Unirest.post("$base/api/cleanup/policies/mirror-copies/dry-run")
                .basicAuth(token, secret)
                .asJacksonObject(CleanupPreviewResponse::class)

            // then: the older copy is the only planned deletion, the deployed 2.0 is not a candidate at all
            assertThat(preview.status).isEqualTo(OK.code)
            assertThat(preview.body.deletions.map { it.path }).containsExactly("$artifact/1.0")
        } finally {
            upstream.stop()
        }
    }

    @Test
    fun `should delete the older mirror copy on a run and keep the newer copy and the deployed version`() {
        // given: the same mixed repository as above
        val upstream = serveUpstream(upstreamFiles)
        try {
            upstreamFiles.forEach { Unirest.get("$base/$repository/$it").asEmpty() }
            useDocument(repository, "$artifact/2.0", "app-2.0.pom", "<project/>", store = true)
            configureKeepNewestMirrorPolicy()
            val (token, secret) = useDefaultManagementToken()

            // when: the policy is run
            val run = Unirest.post("$base/api/cleanup/policies/mirror-copies/run")
                .basicAuth(token, secret)
                .asEmpty()

            // then: the run succeeded and only the older copy is gone
            assertThat(run.status).isEqualTo(OK.code)
            val storage = mavenFacade.getRepository(repository)!!.storageProvider
            assertThat(storage.exists(Location.of("$artifact/1.0/app-1.0.pom"))).`as`("older mirror copy").isFalse
            assertThat(storage.exists(Location.of("$artifact/1.1/app-1.1.pom"))).`as`("newer mirror copy").isTrue
            assertThat(storage.exists(Location.of("$artifact/2.0/app-2.0.pom"))).`as`("deployed version").isTrue
        } finally {
            upstream.stop()
        }
    }

    private fun serveUpstream(files: List<String>): Javalin {
        val started = CountDownLatch(1)
        val upstream = Javalin.start { config ->
            config.jetty.port = 0
            config.events.serverStarted { started.countDown() }
            files.forEach { gav ->
                val content = "content-of-$gav"
                config.routes.head("/releases/$gav") { ctx -> ctx.header("Content-Length", content.length.toString()).status(200) }
                config.routes.get("/releases/$gav") { ctx -> ctx.result(content) }
            }
        }
        assertThat(started.await(10, TimeUnit.SECONDS)).`as`("upstream started").isTrue
        useUpstreamPort(upstream.port())
        return upstream
    }

    private fun configureKeepNewestMirrorPolicy() {
        val (token, secret) = useDefaultManagementToken()
        val body = """{"policies":[{"id":"mirror-copies","enabled":false,"repositories":["$repository"],"scope":"MIRROR_CACHE","keepLatest":1}]}"""
        val response = Unirest.put("$base/api/settings/domain/cleanup").basicAuth(token, secret).body(body).asEmpty()
        assertThat(response.status).`as`("policy update").isEqualTo(OK.code)
    }

}
