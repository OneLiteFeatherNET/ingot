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
import panda.std.ResultAssertions.assertError
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
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import software.amazon.awssdk.services.s3.model.UploadPartResponse
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream

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

        /** The number of the part S3 rejects with a server error, if any. */
        var rejectedPart: Int? = null
        var rejectCompletion = false
        var rejectAbort = false

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
            if (request.partNumber() == rejectedPart) {
                throw S3Exception.builder().statusCode(500).message("internal-detail-42").build()
            }
            uploadedParts += request.partNumber() to body.contentStreamProvider().newStream().readAllBytes()
            return UploadPartResponse.builder().eTag("etag-${request.partNumber()}").build()
        }

        override fun completeMultipartUpload(request: CompleteMultipartUploadRequest): CompleteMultipartUploadResponse {
            if (rejectCompletion) {
                throw S3Exception.builder().statusCode(500).message("internal-detail-42").build()
            }
            completions += request
            return CompleteMultipartUploadResponse.builder().build()
        }

        override fun abortMultipartUpload(request: AbortMultipartUploadRequest): AbortMultipartUploadResponse {
            aborts += request
            if (rejectAbort) {
                throw S3Exception.builder().statusCode(500).message("abort-detail-9").build()
            }
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

    @Test
    fun `should end a multipart upload at a full part without sending an empty part`() {
        // given: a stream of exactly two parts, so the stream ends right after a full part
        val s3 = RecordingS3Client()

        // when: the stream is uploaded
        assertOk(upload(s3, content(16 * mebibyte)))

        // then: exactly two parts are sent, and no third part of zero bytes follows them
        assertThat(s3.uploadedParts.map { it.second.size })
            .describedAs("part sizes of a 16 MiB upload")
            .containsExactly(8 * mebibyte, 8 * mebibyte)
        assertThat(s3.completions).describedAs("multipart uploads completed for a 16 MiB stream").hasSize(1)
    }

    @Test
    fun `should set the content type of the jar on the multipart upload`() {
        // given: a 20 MiB jar, which goes out as a multipart upload
        val s3 = RecordingS3Client()

        // when: the jar is uploaded
        assertOk(upload(s3, content(20 * mebibyte), location = "com/example/lib/1.0.0/lib-1.0.0.jar"))

        // then: the multipart upload carries the same content type as a single put of the jar
        assertThat(s3.createdUploads.single().contentType())
            .describedAs("content type of the multipart upload")
            .isEqualTo("application/java-archive")
    }

    @Test
    fun `should abort the multipart upload when a part is rejected`() {
        // given: S3 rejects the third part of a 20 MiB upload
        val s3 = RecordingS3Client().apply { rejectedPart = 3 }

        // when: the stream is uploaded
        upload(s3, content(20 * mebibyte))

        // then: the multipart upload is aborted with the upload identifier it was created with
        assertThat(s3.aborts.map { it.uploadId() })
            .describedAs("aborted multipart uploads after a rejected part")
            .containsExactly(RecordingS3Client.UPLOAD_ID)
        assertThat(s3.completions).describedAs("completed multipart uploads after a rejected part").isEmpty()
    }

    @Test
    fun `should answer a server error without the S3 message when a part is rejected`() {
        // given: S3 rejects the third part with a message that names an internal detail
        val s3 = RecordingS3Client().apply { rejectedPart = 3 }

        // when: the stream is uploaded
        val error = assertError(upload(s3, content(20 * mebibyte)))

        // then: the status is a server error, as for a failed write today
        assertThat(error.status).describedAs("status of a rejected part").isEqualTo(500)

        // and: neither the S3 message nor the exception class reach the response
        assertThat(error.message)
            .describedAs("message of a rejected part")
            .doesNotContain("internal-detail-42", "S3Exception")
    }

    @Test
    fun `should abort the multipart upload when completing it fails`() {
        // given: S3 rejects the completion of a 20 MiB upload
        val s3 = RecordingS3Client().apply { rejectCompletion = true }

        // when: the stream is uploaded
        val error = assertError(upload(s3, content(20 * mebibyte)))

        // then: the multipart upload is aborted, and the client gets a server error
        assertThat(s3.aborts.map { it.uploadId() })
            .describedAs("aborted multipart uploads after a failed completion")
            .containsExactly(RecordingS3Client.UPLOAD_ID)
        assertThat(error.status).describedAs("status of a failed completion").isEqualTo(500)
    }

    @Test
    fun `should log a failed abort and still answer a server error`() {
        // given: a part is rejected, and the abort that follows is rejected as well
        val logger = InMemoryLogger()
        val failureFacade = FailureComponents(journalist = logger).failureFacade()
        val s3 = RecordingS3Client().apply {
            rejectedPart = 2
            rejectAbort = true
        }
        val provider = S3StorageProvider(failureFacade = failureFacade, s3 = s3, bucket = "test")

        // when: the stream is uploaded
        val error = assertError(provider.putFile(file.toLocation(), content(20 * mebibyte).inputStream()))

        // then: the failed abort is logged with its exception
        assertThat(logger.messages.map { it.value }.joinToString("\n"))
            .describedAs("logged output of a failed abort")
            .contains("abort-detail-9")

        // and: the client still gets the generic server error of a failed write
        assertThat(error.status).describedAs("status after a failed abort").isEqualTo(500)
    }

    @Test
    fun `should close the stream when a multipart upload fails`() {
        // given: a 20 MiB stream whose close is observed, and a part that S3 rejects
        val s3 = RecordingS3Client().apply { rejectedPart = 2 }
        val stream = ClosingObserver(content(20 * mebibyte))

        // when: the stream is uploaded
        provider(s3).putFile(file.toLocation(), stream)

        // then: the stream is closed even though the upload failed
        assertThat(stream.closed).describedAs("stream closed after a failed multipart upload").isTrue()
    }

    private class ClosingObserver(source: ByteArray) : FilterInputStream(ByteArrayInputStream(source)) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }

}
