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
import com.reposilite.maven.StorageTarget
import com.reposilite.maven.StorageUsageService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
import software.amazon.awssdk.services.s3.model.S3Object
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executor

/**
 * Pins the usage snapshot of an S3 repository: the figures of its listing, no quota and no quota bytes.
 */
internal class S3UsageParityTest {

    private val logger = InMemoryLogger()
    private val failureFacade = FailureComponents(journalist = logger).failureFacade()
    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val synchronous = Executor { task -> task.run() }

    /** Lists two objects of one byte each, and accepts every bucket request. */
    private class ListingS3Client(private val keys: List<String>) : S3Client {

        override fun serviceName(): String = S3Client.SERVICE_NAME

        override fun close() {}

        override fun headBucket(request: HeadBucketRequest): HeadBucketResponse =
            HeadBucketResponse.builder().build()

        override fun listObjectsV2(request: ListObjectsV2Request): ListObjectsV2Response =
            ListObjectsV2Response.builder()
                .contents(keys.map { S3Object.builder().key(it).size(1).build() })
                .isTruncated(false)
                .build()

        override fun headObject(request: HeadObjectRequest): HeadObjectResponse =
            throw UnsupportedOperationException("not used by the usage walk")

        override fun getObject(request: GetObjectRequest): ResponseInputStream<GetObjectResponse> =
            throw UnsupportedOperationException("not used by the usage walk")

        override fun putObject(request: PutObjectRequest, body: RequestBody): PutObjectResponse =
            throw UnsupportedOperationException("not used by the usage walk")

        override fun deleteObject(request: DeleteObjectRequest): DeleteObjectResponse =
            throw UnsupportedOperationException("not used by the usage walk")

    }

    @Test
    fun `an S3 repository reports the figures of its listing without a quota`() {
        // given: an S3 repository whose bucket lists two objects
        val provider = S3StorageProvider(
            failureFacade = failureFacade,
            s3 = ListingS3Client(listOf("a.jar", "b.jar")),
            bucket = "test",
        )
        val service = StorageUsageService(
            logger,
            clock,
            { listOf(StorageTarget(id = "archive", storage = provider, quota = null, storageType = "s3")) },
            synchronous,
        )

        // when: usage is measured
        val usage = service.refresh().get()

        // then: the figures are the listing, and neither a quota nor quota bytes are reported
        val archive = usage.repositories.single()
        assertThat(archive.usedBytes).`as`("used bytes of the listing").isEqualTo(2L)
        assertThat(archive.fileCount).`as`("file count of the listing").isEqualTo(2L)
        assertThat(archive.quota).`as`("quota of an S3 repository").isNull()
        assertThat(archive.quotaBytes).`as`("quota bytes of an S3 repository").isNull()
        assertThat(archive.storageType).`as`("storage type").isEqualTo("s3")
        assertThat(archive.group).`as`("group flag").isFalse
    }

}
