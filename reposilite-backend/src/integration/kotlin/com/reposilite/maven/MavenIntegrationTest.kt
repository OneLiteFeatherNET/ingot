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

package com.reposilite.maven

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.RecommendedRemoteSpecificationJunitExtension
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.maven.specification.MavenIntegrationSpecification
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.extensions.maxAge
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.UNAUTHORIZED
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kong.unirest.core.HeaderNames.CACHE_CONTROL
import kong.unirest.core.HeaderNames.CONTENT_LENGTH
import kong.unirest.core.Unirest.delete
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.head
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalMavenIntegrationTest : MavenIntegrationTest()

@ExtendWith(RecommendedRemoteSpecificationJunitExtension::class)
internal class RemoteMavenIntegrationTest : MavenIntegrationTest()

/** Upper bound for each concurrent upload, so that a stuck request fails the test rather than the build. */
private const val UPLOAD_TIMEOUT_SECONDS = 60L

internal abstract class MavenIntegrationTest : MavenIntegrationSpecification() {

    @Test
    fun `should support head requests`() {
        // given: the details about an existing in repository file
        val (repository, gav, file, content) = useDocument("immutable", "gav", "artifact.jar", "content", true)

        // when: client requests head data
        val response = head("$base/$repository/$gav/$file").asEmpty()

        // then: service returns valid file metadata
        assertThat(response.isSuccess).isTrue
        assertThat(response.headers.getFirst(CONTENT_LENGTH).toInt()).isEqualTo(content.length)
        assertThat(response.headers.getFirst(CACHE_CONTROL)).isEqualTo("public, max-age=$maxAge")
    }

    @Test
    fun `should respond with requested file`() {
        // given: the details about an existing in repository file
        val (repository, gav, file, content) = useDocument("releases", "gav", "artifact.jar", "content", true)

        // when: client requests head data
        val response = get("$base/$repository/$gav/$file").asString()

        // then: service returns content of requested file
        assertThat(response.isSuccess).isTrue
        assertThat(response.body).isEqualTo(content)
    }

    @Test
    fun `should reject unauthorized deploy request`() {
        // given: a document to upload
        val (repository, gav, file, content) = useDocument("releases", "gav", "artifact.jar", "content")

        // when: client tries to deploy file without valid credentials
        val response = put("$base/$repository/$gav/$file")
            .body(content)
            .basicAuth("name", "invalid-secret")
            .asObject(ErrorResponse::class.java)

        // then: service should reject the request
        assertThat(response.status).isEqualTo(UNAUTHORIZED.code)
    }

    @Test
    fun `should accept deploy request with valid credentials` () {
        val calls = useFacade<LocalConfiguration>().webThreadPool.get() * 3
        // Assertions made on the worker threads only count when their futures are awaited: an exception
        // inside runAsync is otherwise swallowed. Each future is bounded, so a stuck upload fails the test
        // instead of hanging the build.
        val uploads = (0 until calls).map { idx ->
            CompletableFuture.runAsync {
                // given: file to upload and valid credentials
                val (repository, gav, file) = useDocument("releases", "com/reposilite", "$idx.jar")
                val (name, secret) = useDefaultManagementToken()
                val (content, length) = useFile(file, 8)

                // when: client wants to upload artifact
                val response = put("$base/$repository/$gav/$file")
                    .body(content.inputStream())
                    .basicAuth(name, secret)
                    .asEmpty()

                // then: service properly accepts connection and deploys file, the deploy answers without a body
                assertThat(response.isSuccess).describedAs("deploy of $file answered with HTTP ${response.status}").isTrue

                val stored = get("$base/$repository/$gav/$file").asBytes()
                assertThat(stored.isSuccess).isTrue
                assertThat(stored.body.size.toLong()).isEqualTo(length)
            }
        }

        uploads.forEachIndexed { idx, upload ->
            try {
                upload.get(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (timeout: TimeoutException) {
                fail("concurrent upload #$idx did not finish within $UPLOAD_TIMEOUT_SECONDS seconds", timeout)
            } catch (failed: ExecutionException) {
                throw failed.cause ?: failed
            }
        }
    }

    @Test
    fun `should reject unauthorized delete request`() {
        // given: the details about an existing in repository file
        val (repository, gav, file) = useDocument("releases", "gav", "artifact.jar", "content", true)
        val address = "$base/$repository/$gav/$file"

        // when: unauthorized client tries to delete existing file
        val response = delete(address)
            .basicAuth("name", "invalid-secret")
            .asObject(ErrorResponse::class.java)

        // then: service rejects request and file still exists
        assertThat(response.isSuccess).isFalse
        assertThat(get(address).asEmpty().isSuccess).isTrue
    }

    @Test
    fun `should accept delete request with valid credentials`() {
        // given: the details about an existing in repository file
        val (repository, gav, file) = useDocument("releases", "gav", "artifact.jar", "content", true)
        val address = "$base/$repository/$gav/$file"
        val (name, secret) = useDefaultManagementToken()

        // when: unauthorized client tries to delete existing file
        val response = delete(address)
            .basicAuth(name, secret)
            .asString()

        // then: service rejects request and file still exists
        assertThat(response.isSuccess).isTrue
        assertThat(get(address).asEmpty().isSuccess).isFalse
    }

    @Test
    fun `should respond with custom 404 page`() {
        // given: an address to the non-existing resource
        val address = "$base/unknown-repository/unknown-gav/unknown-file"

        // when: unauthorized client tries to delete existing file
        val response = get(address).asString()

        // then: service responds with custom 404 page
        assertThat(response.status).isEqualTo(NOT_FOUND.code)
        assertThat(response.body).contains("Ingot - 404 Not Found")
    }

}