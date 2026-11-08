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

package com.reposilite.storage.s3

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.application.FailureComponents
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object
import java.io.ByteArrayInputStream

internal class S3StorageProviderFailureTest {

    private val logger = InMemoryLogger()
    private val failureFacade = FailureComponents(journalist = logger).failureFacade()

    private val file = "com/example/lib/1.0.0/lib-1.0.0.jar"

    /**
     * Every object request fails with [failure]. The bucket listing returns [keys] so that a test can decide whether
     * a directory exists in the bucket.
     */
    private class StubS3Client(
        private val failure: Throwable,
        private val keys: List<String> = emptyList(),
        private val listFailure: Throwable? = null,
    ) : S3Client {

        override fun serviceName(): String =
            S3Client.SERVICE_NAME

        override fun close() {}

        override fun headBucket(request: HeadBucketRequest): HeadBucketResponse =
            HeadBucketResponse.builder().build()

        override fun headObject(request: HeadObjectRequest): HeadObjectResponse =
            throw failure

        override fun getObject(request: GetObjectRequest): ResponseInputStream<GetObjectResponse> =
            throw failure

        override fun putObject(request: PutObjectRequest, body: RequestBody): PutObjectResponse =
            throw failure

        override fun deleteObject(request: DeleteObjectRequest): DeleteObjectResponse =
            throw failure

        override fun listObjectsV2(request: ListObjectsV2Request): ListObjectsV2Response =
            listFailure?.let { throw it } ?: ListObjectsV2Response.builder()
                .contents(keys.filter { it.startsWith(request.prefix().orEmpty()) }.map { S3Object.builder().key(it).size(1).build() })
                .isTruncated(false)
                .build()

    }

    private fun providerFailingWith(
        failure: Throwable,
        keys: List<String> = emptyList(),
        listFailure: Throwable? = null,
    ): S3StorageProvider =
        S3StorageProvider(
            failureFacade = failureFacade,
            s3 = StubS3Client(failure, keys, listFailure),
            bucket = "test",
        )

    private fun s3Failure(status: Int): Throwable =
        S3Exception.builder().statusCode(status).message("Simulated S3 failure $status").build()

    @Test
    fun `getFile should answer not found when S3 returns a bare 404 without an error body`() {
        // given: a missing object, answered by S3 as a 404 without the NoSuchKey error body
        val provider = providerFailingWith(s3Failure(404))

        // when: the file is opened
        val result = provider.getFile(file.toLocation())

        // then: it is reported as not found, so a mirror miss falls through to the upstream
        assertThat(assertError(result).status).describedAs("status of a bare 404").isEqualTo(404)
    }

    @Test
    fun `getFile should answer not found when S3 returns NoSuchKey`() {
        // given: a missing object, answered by S3 with the NoSuchKey error body
        val provider = providerFailingWith(NoSuchKeyException.builder().statusCode(404).message("No such key").build())

        // when: the file is opened
        val result = provider.getFile(file.toLocation())

        // then: it is reported as not found
        assertThat(assertError(result).status).describedAs("status of NoSuchKey").isEqualTo(404)
    }

    @Test
    fun `removeFile should not expose the S3 failure message when the delete fails`() {
        // given: S3 rejects the delete with a message that names an internal detail
        val provider = providerFailingWith(S3Exception.builder().statusCode(500).message("internal-detail-42").build())

        // when: the file is removed
        val error = assertError(provider.removeFile(file.toLocation()))

        // then: the status is still a server error
        assertThat(error.status).describedAs("status of a failed delete").isEqualTo(500)

        // and: neither the failure message nor the class name reach the response
        assertThat(error.message)
            .describedAs("message of a failed delete")
            .doesNotContain("internal-detail-42", "S3Exception")
    }

    @Test
    fun `getFileSize should not expose the S3 message of a missing key`() {
        // given: a missing object, answered by S3 with a NoSuchKey message that names an internal detail
        val provider = providerFailingWith(NoSuchKeyException.builder().statusCode(404).message("internal-detail-42").build())

        // when: the size of the file is requested
        val error = assertError(provider.getFileSize(file.toLocation()))

        // then: the status is still not found
        assertThat(error.status).describedAs("status of a missing key").isEqualTo(404)

        // and: the S3 message does not reach the response
        assertThat(error.message).describedAs("message of a missing key").doesNotContain("internal-detail-42")
    }

    @Test
    fun `should answer not found for a missing file when S3 denies access, so that a mirror miss still falls through`() {
        // given: S3 answers the missing key with 403, as it does when the credentials lack ListBucket
        val provider = providerFailingWith(s3Failure(403), listFailure = s3Failure(403))

        // when: the file is opened and its details are requested
        val opened = provider.getFile(file.toLocation())
        val details = provider.getFileDetails(file.toLocation())

        // then: both are reported as not found, as they were before S3 errors were distinguished
        assertThat(assertError(opened).status).describedAs("status of getFile on a 403").isEqualTo(404)
        assertThat(assertError(details).status).describedAs("status of getFileDetails on a 403").isEqualTo(404)
    }

    @Test
    fun `getFile should answer a server error when S3 returns 503`() {
        // given: S3 is temporarily unavailable
        val provider = providerFailingWith(s3Failure(503))

        // when: the file is opened
        val result = provider.getFile(file.toLocation())

        // then: a server error is returned instead of a false not found
        assertThat(assertError(result).status).describedAs("status of a 503").isEqualTo(500)
    }

    @Test
    fun `getFile should answer a server error when the connection to S3 fails`() {
        // given: the network to S3 is broken
        val provider = providerFailingWith(SdkClientException.create("Connection reset"))

        // when: the file is opened
        val result = provider.getFile(file.toLocation())

        // then: a server error is returned instead of a false not found
        assertThat(assertError(result).status).describedAs("status of a network failure").isEqualTo(500)
    }

    @Test
    fun `getFileDetails should answer a server error and not a missing directory when HEAD fails on an existing file`() {
        // given: the file exists, but HEAD fails for a transient reason and the bucket holds no directory under its name
        val provider = providerFailingWith(s3Failure(503), keys = listOf(file))

        // when: the details of the file are requested
        val result = provider.getFileDetails(file.toLocation())

        // then: a server error is returned, so clients do not cache a false miss
        assertThat(assertError(result).status).describedAs("status of a transient HEAD failure").isEqualTo(500)
    }

    @Test
    fun `getFileDetails should answer not found for a missing file when HEAD returns a bare 404`() {
        // given: a missing file, answered by HEAD with a bare 404 and an empty bucket
        val provider = providerFailingWith(s3Failure(404))

        // when: the details of the file are requested
        val result = provider.getFileDetails(file.toLocation())

        // then: it is reported as not found
        assertThat(assertError(result).status).describedAs("status of a missing file").isEqualTo(404)
    }

    @Test
    fun `getFileDetails should list the directory when HEAD returns a bare 404 for a directory`() {
        // given: a directory with a file in it, and HEAD answers 404 without an error body for the directory key
        val provider = providerFailingWith(s3Failure(404), keys = listOf(file))

        // when: the details of the directory are requested
        val result = provider.getFileDetails("com/example/lib/1.0.0".toLocation())

        // then: the directory is resolved from the listing
        assertThat(assertOk(result)).isInstanceOf(DirectoryInfo::class.java)
    }

    @Test
    fun `putFile should answer a server error when S3 rejects the upload`() {
        // given: S3 fails the upload with a server error
        val provider = providerFailingWith(s3Failure(500))

        // when: a file is stored
        val result = provider.putFile(file.toLocation(), ByteArrayInputStream(ByteArray(1)))

        // then: a server error is returned instead of an exception escaping the provider
        assertThat(assertError(result).status).describedAs("status of a failed upload").isEqualTo(500)
    }

}
