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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertOk
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A group whose stored loopback member is a stored proxy. Both mirror fetches are made on a pool of one IO thread:
 * the group's fetch occupies that thread and waits for the member's fetch, which can only run on the same pool.
 */
internal class MirrorFetchOnCallerTest : MavenSpecification() {

    private val responseTimeoutSeconds = 10L

    override fun createIoService(): ExecutorService = Executors.newSingleThreadExecutor()

    override fun repositories() = listOf(
        RepositorySettings("stored-proxy", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = true, authorization = REMOTE_AUTH),
        )),
        RepositorySettings("stored-group", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = "stored-proxy", store = true),
        )),
    )

    @Test
    fun `should fetch a file through a group with a stored proxy member on one IO thread`() {
        // given: a file the upstream serves to the stored proxy
        val file = FileSpec("stored-group", "/com/example/nested/1.0/nested-1.0.jar", REMOTE_CONTENT)

        // when: the file is requested through the group, with a bounded wait for the answer
        val caller = Executors.newSingleThreadExecutor()
        try {
            val lookup = CompletableFuture.supplyAsync({ mavenFacade.findFile(file.toLookupRequest(UNAUTHORIZED)) }, caller)
            val response = try {
                lookup.get(responseTimeoutSeconds, TimeUnit.SECONDS)
            } catch (timeout: TimeoutException) {
                throw AssertionError(
                    "Group lookup did not finish within $responseTimeoutSeconds s on one IO thread: the group's mirror fetch " +
                        "waits for its member's mirror fetch, which is queued on the same pool behind it"
                )
            }

            // then: the group answers with the file
            assertOk(response)
            assertThat(response.get().document).describedAs("document served by the group").isNotNull
        } finally {
            caller.shutdownNow()
        }
    }

}
