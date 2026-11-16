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

package net.onelitefeather.ingot.storage.azure

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.status.FailureFacade
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.javalin.http.HttpStatus.NOT_FOUND
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result

/**
 * The error mapping of design D7: a missing blob is not found, while an authorization failure and a server error are internal server
 * errors that reach the failure log, and never a not found.
 */
internal class AzureBlobStorageProviderFailureTest {

    private val client = FakeAzureBlobClient()
    private val failures = FailureFacade(InMemoryLogger())
    private val provider = AzureBlobStorageProvider(
        failureFacade = failures,
        client = client,
        blobPrefix = "",
        blockSize = 8,
    )

    private val resource: Location = "/directory/data.jar".toLocation()

    init {
        // given: a stored blob, which the failures below stand in front of
        client.committed["directory/data.jar"] = "0123".toByteArray()
    }

    private fun assertStatus(result: Result<*, *>, status: Int) {
        assertThat(result.isErr).`as`("the result is an error").isTrue()
        assertThat((result.error as ErrorResponse).status).`as`("status of the error").isEqualTo(status)
    }

    @Test
    fun `should answer an authorization failure as an internal server error and not as not found`() {
        // given: the identity is refused, which is not the same as a missing blob
        client.forcedFailure = AzureServiceException("directory/data.jar", 403, "AuthorizationPermissionMismatch")

        // when: the details are requested
        val details = provider.getFileDetails(resource)

        // then: the answer is an internal server error, so the outage is not hidden as a miss
        assertStatus(details, INTERNAL_SERVER_ERROR.code)
    }

    @Test
    fun `should answer a server error that survived the retries as an internal server error`() {
        // given: the service stays busy after the SDK retries
        client.forcedFailure = AzureServiceException("directory/data.jar", 503, "ServerBusy")

        // when: the file is read
        val content = provider.getFile(resource)

        // then: the answer is an internal server error
        assertStatus(content, INTERNAL_SERVER_ERROR.code)
    }

    @Test
    fun `should record an authorization failure in the failure log`() {
        // given: the identity is refused
        client.forcedFailure = AzureServiceException("directory/data.jar", 403, "AuthorizationFailure")

        // when: the file is read
        provider.getFile(resource)

        // then: the failure is recorded with the operation that failed
        assertThat(failures.getFailures().joinToString("\n"))
            .`as`("failure log")
            .contains("Azure Blob storage provider failed to read")
    }

    @Test
    fun `should report a refused identity as a location that does not exist without raising it to the caller`() {
        // given: the identity is refused for every call
        client.forcedFailure = AzureServiceException("directory/data.jar", 403, "AuthorizationFailure")

        // when: the existence of the blob is asked
        val exists = provider.exists(resource)

        // then: the answer is false and the failure is in the log
        assertThat(exists).`as`("exists").isFalse()
        assertThat(failures.hasFailures()).`as`("failure log has records").isTrue()
    }

    @Test
    fun `should answer a missing blob as not found while a refused blob stays an internal error`() {
        // when: a blob that is not stored is requested
        val missing = provider.getFileDetails("/missing.jar".toLocation())

        // then: the answer is not found, which an authorization failure never is
        assertStatus(missing, NOT_FOUND.code)
    }

}
