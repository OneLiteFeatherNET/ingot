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

package net.onelitefeather.ingot.storage.gcs

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.status.FailureFacade
import com.reposilite.storage.api.toLocation
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.javalin.http.HttpStatus.NOT_FOUND
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result

/**
 * The error mapping of design D8: a missing object is not found, a refused identity is a forbidden answer that is logged and never an
 * empty repository, and any other failure is an internal server error. No text of the service reaches the answer.
 */
internal class GcsStorageProviderFailureTest {

    private val client = FakeGcsObjectClient().apply {
        objects["a/data.bin"] = ByteArray(4)
        objects["a/dir/one.jar"] = ByteArray(4)
    }
    private val failures = FailureFacade(InMemoryLogger())
    private val provider = GcsStorageProvider(
        failureFacade = failures,
        client = client,
        objectPrefix = "",
        chunkSize = 4,
        bucket = "repositories",
    )

    private fun assertStatus(result: Result<*, *>, status: Int) {
        assertThat(result.isErr).`as`("the result is an error").isTrue()
        assertThat((result.error as ErrorResponse).status).`as`("status of the error").isEqualTo(status)
    }

    @Test
    fun `should answer a refused identity with a forbidden error and not with not found`() {
        // given: the identity is refused for the object
        client.forcedFailure = GcsServiceException(403, "forbidden")

        // when: the object is read
        val result = provider.getFile("/a/data.bin".toLocation())

        // then: the answer is forbidden, so a missing permission is never taken for an empty repository
        assertStatus(result, FORBIDDEN.code)
    }

    @Test
    fun `should answer a refused identity on a listing with forbidden and not as an empty directory`() {
        // given: the identity is refused for the listing
        client.forcedFailure = GcsServiceException(403, "forbidden")

        // when: a directory is listed
        val result = provider.getFiles("/a/dir".toLocation())

        // then: the answer is forbidden, not not found
        assertStatus(result, FORBIDDEN.code)
    }

    @Test
    fun `should record a refused identity in the failure log`() {
        // given: the identity is refused
        client.forcedFailure = GcsServiceException(401, "unauthenticated")

        // when: the object is read
        provider.getFile("/a/data.bin".toLocation())

        // then: the failure is recorded
        assertThat(failures.hasFailures()).`as`("failure log has records").isTrue()
    }

    @Test
    fun `should name the bucket and the role in the message of a refused identity`() {
        // given: the identity is refused
        client.forcedFailure = GcsServiceException(403, "forbidden")

        // when: the object is read
        val message = (provider.getFile("/a/data.bin".toLocation()).error as ErrorResponse).message

        // then: the message names the bucket and the role that the operator must grant
        assertThat(message).`as`("message").contains("repositories").contains("roles/storage.objectUser")
    }

    @Test
    fun `should not carry the text of a service error into the answer`() {
        // given: a service error whose text holds a key and the userinfo of an endpoint
        client.forcedFailure = GcsServiceException(403, "private_key=SECRET-KEY-VALUE at https://user:pass@storage.example")

        // when: the object is read
        val message = (provider.getFile("/a/data.bin".toLocation()).error as ErrorResponse).message

        // then: neither the key nor the userinfo is part of the answer
        assertThat(message).`as`("message").doesNotContain("SECRET-KEY-VALUE").doesNotContain("user:pass")
    }

    @Test
    fun `should answer a server error that survived the client retries as an internal server error`() {
        // given: the service stays unavailable after the retries of the client
        client.forcedFailure = GcsServiceException(503, "unavailable")

        // when: the object is read
        val result = provider.getFile("/a/data.bin".toLocation())

        // then: the answer is an internal server error, not a miss
        assertStatus(result, INTERNAL_SERVER_ERROR.code)
    }

    @Test
    fun `should answer a missing object with not found while a refused identity stays forbidden`() {
        // when: an object that is not stored is read
        val result = provider.getFile("/a/missing.bin".toLocation())

        // then: the answer is not found
        assertStatus(result, NOT_FOUND.code)
    }

    @Test
    fun `should report a refused identity as a missing object in exists and log it`() {
        // given: the identity is refused for every call
        client.forcedFailure = GcsServiceException(403, "forbidden")

        // when: the existence of the object is asked
        val exists = provider.exists("/a/data.bin".toLocation())

        // then: the answer is false and the failure is in the log
        assertThat(exists).`as`("exists").isFalse()
        assertThat(failures.hasFailures()).`as`("failure log has records").isTrue()
    }

}
