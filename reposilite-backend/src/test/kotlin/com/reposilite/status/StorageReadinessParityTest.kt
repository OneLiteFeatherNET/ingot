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

package com.reposilite.status

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.application.FailureComponents
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import com.reposilite.storage.s3.S3StorageProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import software.amazon.awssdk.core.ResponseInputStream
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.File
import kotlin.io.path.createDirectories

/**
 * Pins the answer of the readiness listing for an empty root, a missing root and a failing listing, as the status plugin reads it.
 */
internal class StorageReadinessParityTest {

    @TempDir
    lateinit var workingDirectory: File

    private val logger = InMemoryLogger()
    private val failureFacade = FailureComponents(journalist = logger).failureFacade()

    /** Answers a listing with [keys], or with [listFailure] when it is set. */
    private class ListingS3Client(
        private val keys: List<String> = emptyList(),
        private val listFailure: Throwable? = null,
    ) : S3Client {

        override fun serviceName(): String = S3Client.SERVICE_NAME

        override fun close() {}

        override fun headBucket(request: HeadBucketRequest): HeadBucketResponse =
            HeadBucketResponse.builder().build()

        override fun listObjectsV2(request: ListObjectsV2Request): ListObjectsV2Response {
            listFailure?.let { throw it }
            return ListObjectsV2Response.builder()
                .contents(keys.map { software.amazon.awssdk.services.s3.model.S3Object.builder().key(it).size(1).build() })
                .isTruncated(false)
                .build()
        }

        override fun headObject(request: HeadObjectRequest): HeadObjectResponse =
            throw UnsupportedOperationException("not used by the readiness listing")

        override fun getObject(request: GetObjectRequest): ResponseInputStream<GetObjectResponse> =
            throw UnsupportedOperationException("not used by the readiness listing")

        override fun putObject(request: PutObjectRequest, body: RequestBody): PutObjectResponse =
            throw UnsupportedOperationException("not used by the readiness listing")

        override fun deleteObject(request: DeleteObjectRequest): DeleteObjectResponse =
            throw UnsupportedOperationException("not used by the readiness listing")

    }

    @Test
    fun `an empty file system root answers the readiness listing`() {
        // given: a file system repository whose root exists and holds nothing
        val root = workingDirectory.toPath().resolve("empty").createDirectories()
        val storage = FileSystemStorageProviderFactory.of(logger, root, "100%")

        // when: the root is listed for readiness
        val answered = storage.getFiles(Location.empty()).answersListing()

        // then: the empty root counts as answered
        assertThat(answered).`as`("an empty file system root answers the listing").isTrue
    }

    @Test
    fun `a missing file system root answers the readiness listing as not found`() {
        // given: a file system repository whose root does not exist yet
        val root = workingDirectory.toPath().resolve("missing")
        val storage = FileSystemStorageProviderFactory.of(logger, root, "100%")

        // when: the root is listed for readiness
        val answered = storage.getFiles(Location.empty()).answersListing()

        // then: the backend answered, so the missing root counts as answered
        assertThat(answered).`as`("a missing file system root answers the listing").isTrue
    }

    @Test
    fun `an empty S3 bucket answers the readiness listing`() {
        // given: an S3 repository whose bucket lists nothing
        val storage = S3StorageProvider(failureFacade = failureFacade, s3 = ListingS3Client(), bucket = "test")

        // when: the root is listed for readiness
        val answered = storage.getFiles(Location.empty()).answersListing()

        // then: the not found answer of an empty S3 root counts as answered
        assertThat(answered).`as`("an empty S3 root answers the listing").isTrue
    }

    @Test
    fun `a failing S3 listing does not answer the readiness listing`() {
        // given: an S3 repository whose listing fails on the server side
        val failure = S3Exception.builder().statusCode(500).message("Simulated S3 failure 500").build()
        val storage = S3StorageProvider(failureFacade = failureFacade, s3 = ListingS3Client(listFailure = failure), bucket = "test")

        // when: the root is listed for readiness
        val answered = storage.getFiles(Location.empty()).answersListing()

        // then: the failing listing is not an answer
        assertThat(answered).`as`("a failing S3 listing answers the readiness listing").isFalse
    }

}
