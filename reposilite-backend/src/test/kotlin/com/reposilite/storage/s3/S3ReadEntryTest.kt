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

@file:OptIn(ExperimentalIngotPluginApi::class)

package com.reposilite.storage.s3

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.application.FailureComponents
import com.reposilite.storage.api.toLocation
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.io.ByteArrayInputStream
import java.time.Instant

internal class S3ReadEntryTest {

    private val failureFacade = FailureComponents(journalist = InMemoryLogger()).failureFacade()
    private val stored = Instant.parse("2026-10-15T12:00:00Z")
    private val file = "com/example/lib/1.0.0/lib-1.0.0.jar"

    /** Records every object request, so that the number of requests is asserted instead of timed. */
    private class RecordingS3Client(private val content: ByteArray?, private val modified: Instant) : S3Client {

        val gets = mutableListOf<GetObjectRequest>()
        val heads = mutableListOf<HeadObjectRequest>()

        override fun serviceName(): String = S3Client.SERVICE_NAME

        override fun close() {}

        override fun headBucket(request: HeadBucketRequest): HeadBucketResponse =
            HeadBucketResponse.builder().build()

        override fun headObject(request: HeadObjectRequest): HeadObjectResponse {
            heads += request
            throw IllegalStateException("readEntry must not send a HEAD")
        }

        override fun getObject(request: GetObjectRequest): ResponseInputStream<GetObjectResponse> {
            gets += request
            val bytes = content ?: throw NoSuchKeyException.builder().statusCode(404).message("No such key").build()
            val response = GetObjectResponse.builder().contentLength(bytes.size.toLong()).lastModified(modified).build()
            return ResponseInputStream(response, AbortableInputStream.create(ByteArrayInputStream(bytes)))
        }

    }

    private fun providerOver(client: RecordingS3Client) =
        S3StorageProvider(failureFacade = failureFacade, s3 = client, bucket = "test")

    @Test
    fun `readEntry should read content, size and modification time with one GET and no HEAD`() {
        val client = RecordingS3Client(byteArrayOf(1, 2, 3), stored)

        val entry = assertOk(providerOver(client).readEntry(file.toLocation()))

        assertThat(client.gets).describedAs("GET requests").hasSize(1)
        assertThat(client.heads).describedAs("HEAD requests").isEmpty()
        assertThat(entry.content.use { it.readBytes() }).describedAs("content").containsExactly(1, 2, 3)
        assertThat(entry.size).describedAs("size from the response headers").isEqualTo(3)
        assertThat(entry.lastModified).describedAs("modification time from the response headers").isEqualTo(stored)
    }

    @Test
    fun `readEntry should answer not found for a missing object after one GET`() {
        val client = RecordingS3Client(content = null, modified = stored)

        val error = assertError(providerOver(client).readEntry(file.toLocation()))

        assertThat(error.status).describedAs("status of a missing object").isEqualTo(404)
        assertThat(client.heads).describedAs("HEAD requests").isEmpty()
    }

}
