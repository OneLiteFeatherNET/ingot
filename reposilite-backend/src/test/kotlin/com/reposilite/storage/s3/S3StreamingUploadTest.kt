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
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertOk
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadResponse
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import software.amazon.awssdk.services.s3.model.UploadPartResponse
import java.io.ByteArrayOutputStream

internal class S3StreamingUploadTest {

    private val logger = InMemoryLogger()
    private val failureFacade = FailureComponents(journalist = logger).failureFacade()

    private val file = "com/example/lib/1.0.0/lib-1.0.0.jar"
    private val mebibyte = 1024 * 1024

    /** Deterministic content, so that a failure is reproducible and no random seed is involved. */
    private fun content(size: Int): ByteArray =
        ByteArray(size) { (it % 251).toByte() }

    /** Records every call the provider makes, and stores nothing on disk. */
    private class RecordingS3Client : S3Client {

        val puts = mutableListOf<Pair<PutObjectRequest, ByteArray>>()
        val createdUploads = mutableListOf<CreateMultipartUploadRequest>()
        val uploadedParts = mutableListOf<Pair<Int, ByteArray>>()
        val completions = mutableListOf<CompleteMultipartUploadRequest>()
        val aborts = mutableListOf<AbortMultipartUploadRequest>()

        override fun serviceName(): String =
            S3Client.SERVICE_NAME

        override fun close() {}

        override fun headBucket(request: HeadBucketRequest): HeadBucketResponse =
            HeadBucketResponse.builder().build()

        override fun putObject(request: PutObjectRequest, body: RequestBody): PutObjectResponse {
            puts += request to body.contentStreamProvider().newStream().readAllBytes()
            return PutObjectResponse.builder().eTag("etag-put").build()
        }

        override fun createMultipartUpload(request: CreateMultipartUploadRequest): CreateMultipartUploadResponse {
            createdUploads += request
            return CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build()
        }

        override fun uploadPart(request: UploadPartRequest, body: RequestBody): UploadPartResponse {
            uploadedParts += request.partNumber() to body.contentStreamProvider().newStream().readAllBytes()
            return UploadPartResponse.builder().eTag("etag-${request.partNumber()}").build()
        }

        override fun completeMultipartUpload(request: CompleteMultipartUploadRequest): CompleteMultipartUploadResponse {
            completions += request
            return CompleteMultipartUploadResponse.builder().build()
        }

        override fun abortMultipartUpload(request: AbortMultipartUploadRequest): AbortMultipartUploadResponse {
            aborts += request
            return AbortMultipartUploadResponse.builder().build()
        }

        companion object {
            const val UPLOAD_ID = "upload-1"
        }
    }

    private fun provider(s3: S3Client): S3StorageProvider =
        S3StorageProvider(
            failureFacade = failureFacade,
            s3 = s3,
            bucket = "test",
        )

    private fun upload(s3: S3Client, data: ByteArray, location: String = file) =
        provider(s3).putFile(location.toLocation(), data.inputStream())

    @Test
    fun `should send a small stream in one put with its exact length`() {
        // given: a stream of 1 KiB, whose length the provider does not know in advance
        val s3 = RecordingS3Client()
        val data = content(1024)

        // when: the stream is uploaded
        assertOk(upload(s3, data))

        // then: one put carries the bytes, and its content length is the exact size of the stream
        assertThat(s3.puts).describedAs("put requests for a 1 KiB stream").hasSize(1)
        assertThat(s3.puts.single().first.contentLength()).describedAs("content length of the put").isEqualTo(1024L)
        assertThat(s3.puts.single().second.contentEquals(data)).describedAs("bytes of the put").isTrue()
        assertThat(s3.createdUploads).describedAs("multipart uploads for a 1 KiB stream").isEmpty()
    }

    @Test
    fun `should send a stream of exactly one part in one put`() {
        // given: a stream of exactly 8 MiB, the size of one part
        val s3 = RecordingS3Client()
        val data = content(8 * mebibyte)

        // when: the stream is uploaded
        assertOk(upload(s3, data))

        // then: the stream is one put, and no multipart upload with a single part is started
        assertThat(s3.puts).describedAs("put requests for an 8 MiB stream").hasSize(1)
        assertThat(s3.puts.single().first.contentLength()).describedAs("content length of the put").isEqualTo(8L * mebibyte)
        assertThat(s3.createdUploads).describedAs("multipart uploads for an 8 MiB stream").isEmpty()
    }

    @Test
    fun `should send a stream of 20 MiB as multipart upload with 8 MiB parts and a smaller last part`() {
        // given: a stream of 20 MiB whose length the provider does not know in advance
        val s3 = RecordingS3Client()
        val data = content(20 * mebibyte)

        // when: the stream is uploaded
        assertOk(upload(s3, data))

        // then: a multipart upload is created, and it is completed
        assertThat(s3.createdUploads).describedAs("multipart uploads created for a 20 MiB stream").hasSize(1)
        assertThat(s3.completions).describedAs("multipart uploads completed for a 20 MiB stream").hasSize(1)
        assertThat(s3.puts).describedAs("single puts for a 20 MiB stream").isEmpty()

        // and: the parts are 8 MiB, 8 MiB and 4 MiB, sent in order
        assertThat(s3.uploadedParts.map { it.first })
            .describedAs("part numbers of the 20 MiB upload")
            .containsExactly(1, 2, 3)
        assertThat(s3.uploadedParts.map { it.second.size })
            .describedAs("part sizes of the 20 MiB upload")
            .containsExactly(8 * mebibyte, 8 * mebibyte, 4 * mebibyte)

        // and: the parts, joined in order, are the bytes of the stream
        val assembled = ByteArrayOutputStream().apply { s3.uploadedParts.forEach { write(it.second) } }.toByteArray()
        assertThat(assembled.contentEquals(data)).describedAs("bytes of the multipart upload, joined in order").isTrue()
    }

}
