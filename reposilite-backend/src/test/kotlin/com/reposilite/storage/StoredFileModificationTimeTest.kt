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

package com.reposilite.storage

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import com.reposilite.storage.s3.S3StorageProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.ResultAssertions.assertOk
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Object
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

// A fixed instant rather than the clock: every expectation holds at any time of day and on any machine.
private val STORED_AT: Instant = Instant.parse("2026-10-15T12:00:00.123Z")

/**
 * The modification time of a stored file has to reach the API as the instant it was stored at.
 * The API sends it as Unix epoch seconds (Jackson's default for java.time.Instant), which the
 * frontend has to multiply by 1000 before it builds a Date.
 */
internal class StoredFileModificationTimeTest {

    @TempDir
    lateinit var root: File

    @Test
    fun `should report the modification time of a file on disk`() {
        // given: a file on disk whose modification time is the stored instant
        root.toPath().resolve("group").createDirectories()
        val file = root.toPath().resolve("group/app-1.0.jar")
        file.writeBytes(ByteArray(3))
        Files.setLastModifiedTime(file, FileTime.from(STORED_AT))

        // when: the details of the file are requested
        val storage = FileSystemStorageProviderFactory.of(InMemoryLogger(), root.toPath(), "100%")
        val details = assertOk(storage.getFileDetails(Location.of("group/app-1.0.jar"))) as DocumentInfo

        // then: the instant is reported unchanged
        assertThat(details.lastModifiedTime).isEqualTo(STORED_AT)
    }

    @Test
    fun `should send the modification time of a file on disk over the API as epoch seconds`() {
        // given: a file on disk whose modification time is the stored instant
        root.toPath().resolve("group").createDirectories()
        val file = root.toPath().resolve("group/app-1.0.jar")
        file.writeBytes(ByteArray(3))
        Files.setLastModifiedTime(file, FileTime.from(STORED_AT))
        val storage = FileSystemStorageProviderFactory.of(InMemoryLogger(), root.toPath(), "100%")
        val details = assertOk(storage.getFileDetails(Location.of("group/app-1.0.jar")))

        // when: the details are serialized the way the API responds
        // then: the value is the epoch-second representation of the stored instant
        assertThat(wireLastModified(details)).describedAs("lastModifiedTime on the wire")
            .isEqualByComparingTo(epochSeconds(STORED_AT))
    }

    @Test
    fun `should report the upload time of an object in S3`() {
        // given: an S3 object whose LastModified is the stored instant
        val s3 = StoredObjectS3Client(key = "group/app-1.0.jar", lastModified = STORED_AT)
        val storage = S3StorageProvider(FailureFacade(InMemoryLogger()), s3, bucket = "ingot")

        // when: the details and the modification time of the object are requested
        val details = assertOk(storage.getFileDetails(Location.of("group/app-1.0.jar"))) as DocumentInfo
        val modified = assertOk(storage.getLastModifiedTime(Location.of("group/app-1.0.jar")))

        // then: both report the stored instant
        assertThat(details.lastModifiedTime).isEqualTo(STORED_AT)
        assertThat(modified.toInstant()).isEqualTo(STORED_AT)
    }

    @Test
    fun `should list the upload time of each object in an S3 directory`() {
        // given: a directory that holds one S3 object stored at the instant
        val s3 = StoredObjectS3Client(key = "group/app-1.0.jar", lastModified = STORED_AT)
        val storage = S3StorageProvider(FailureFacade(InMemoryLogger()), s3, bucket = "ingot")

        // when: the directory is listed
        val listing = assertOk(storage.getFileDetails(Location.of("group"))) as DirectoryInfo

        // then: the listed file carries the stored instant
        val file = listing.files.single() as DocumentInfo
        assertThat(file.lastModifiedTime).isEqualTo(STORED_AT)
    }

    @Test
    fun `should send the upload time of an S3 object over the API as epoch seconds`() {
        // given: an S3 object whose LastModified is the stored instant
        val s3 = StoredObjectS3Client(key = "group/app-1.0.jar", lastModified = STORED_AT)
        val storage = S3StorageProvider(FailureFacade(InMemoryLogger()), s3, bucket = "ingot")
        val details = assertOk(storage.getFileDetails(Location.of("group/app-1.0.jar")))

        // when: the details are serialized the way the API responds
        // then: the value is the epoch-second representation of the stored instant
        assertThat(wireLastModified(details)).describedAs("lastModifiedTime on the wire")
            .isEqualByComparingTo(epochSeconds(STORED_AT))
    }

    private fun wireLastModified(details: FileDetails): BigDecimal =
        DEFAULT_OBJECT_MAPPER.readTree(DEFAULT_OBJECT_MAPPER.writeValueAsString(details))
            .get("lastModifiedTime")
            .decimalValue()

    private fun epochSeconds(instant: Instant): BigDecimal =
        BigDecimal.valueOf(instant.epochSecond).add(BigDecimal.valueOf(instant.nano.toLong(), 9))

}

/**
 * Serves one object at [key] with a fixed LastModified. Every other key is missing, and the bucket exists.
 * Only the operations the storage provider calls for a file lookup or a one-level listing are answered.
 */
private class StoredObjectS3Client(
    private val key: String,
    private val lastModified: Instant,
) : S3Client {

    override fun serviceName(): String = S3Client.SERVICE_NAME

    override fun close() {}

    override fun headBucket(request: HeadBucketRequest): HeadBucketResponse =
        HeadBucketResponse.builder().build()

    override fun headObject(request: HeadObjectRequest): HeadObjectResponse =
        if (request.key() == key) {
            HeadObjectResponse.builder().contentLength(3L).lastModified(lastModified).build()
        } else {
            throw NoSuchKeyException.builder().message("No such key: ${request.key()}").build()
        }

    override fun listObjectsV2(request: ListObjectsV2Request): ListObjectsV2Response =
        ListObjectsV2Response.builder()
            .contents(S3Object.builder().key(key).size(3L).lastModified(lastModified).build())
            .build()

}
