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

package com.reposilite.storage

import com.reposilite.IsolatedResources
import com.reposilite.SharedContainers
import com.reposilite.s3Endpoint
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.toLocation
import com.reposilite.storage.s3.S3StorageProvider
import com.reposilite.storage.s3.S3StorageProviderSettings
import org.assertj.core.api.Assertions.assertThat
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.DelegatingS3Client
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.ResultAssertions.assertOk
import java.io.File
import java.net.URI

internal class S3StorageProviderIntegrationTest : StorageProviderIntegrationTest() {

    @TempDir
    lateinit var rootDirectory: File

    // JUnit creates a new instance per test method, so every test gets a bucket of its own on the shared Floci.
    private val bucket = IsolatedResources.newBucketName("storage-test")

    @BeforeEach
    fun setup() {
        val logger = InMemoryLogger()
        val failureFacade = FailureFacade(logger)
        val storageFacade = StorageFacade()

        this.storageProvider = storageFacade.createStorageProvider(
            journalist = logger,
            failureFacade = failureFacade,
            workingDirectory = rootDirectory.toPath(),
            repository = "test-repository",
            storageSettings = S3StorageProviderSettings(
                bucketName = bucket,
                endpoint = SharedContainers.floci.s3Endpoint(),
                accessKey = "test",
                secretKey = "test",
                region = "us-east-1"
            )
        )!!
    }

    // GH-2612: ListObjectsV2 caps a single response at 1000 keys; without continuation-token
    // pagination, directory listings and bulk deletes silently truncate beyond that.
    @Test
    fun `should list and remove all entries when directory holds more than a single S3 page`() {
        val entryCount = 1001
        val content = "x".toByteArray()

        repeat(entryCount) { i ->
            storageProvider.putFile("/snapshots/file-$i.jar".toLocation(), content.inputStream())
        }

        val listing = assertOk(storageProvider.getFileDetails("/snapshots".toLocation())) as DirectoryInfo
        assertThat(listing.files).hasSize(entryCount)

        assertOk(storageProvider.removeFile("/snapshots".toLocation()))
        assertThat(storageProvider.exists("/snapshots/file-0.jar".toLocation())).isFalse
        assertThat(storageProvider.exists("/snapshots/file-${entryCount - 1}.jar".toLocation())).isFalse
    }

    // GH-2623: multiple repositories may share one bucket in single-bucket mode, which namespaces
    // keys under '<prefix>/<repository>/' so they neither see nor overwrite each other.
    @Test
    fun `should isolate repositories sharing one bucket in single-bucket mode`() {
        val alpha = storageProvider(repository = "alpha")
        val beta = storageProvider(repository = "beta")
        val artifact = "/com/example/artifact.jar".toLocation()

        assertOk(alpha.putFile(artifact, "alpha".toByteArray().inputStream()))

        assertThat(alpha.exists(artifact)).isTrue
        assertThat(beta.exists(artifact)).isFalse

        assertOk(beta.putFile(artifact, "beta".toByteArray().inputStream()))
        assertThat(String(assertOk(alpha.getFile(artifact)).readBytes())).isEqualTo("alpha")
        assertThat(String(assertOk(beta.getFile(artifact)).readBytes())).isEqualTo("beta")
    }

    @Test
    fun `should scope removals to the deleting repository in single-bucket mode`() {
        val alpha = storageProvider(repository = "alpha")
        val beta = storageProvider(repository = "beta")
        val artifact = "/com/example/artifact.jar".toLocation()

        assertOk(alpha.putFile(artifact, "alpha".toByteArray().inputStream()))
        assertOk(beta.putFile(artifact, "beta".toByteArray().inputStream()))

        assertOk(alpha.removeFile("/com/example".toLocation()))

        assertThat(alpha.exists(artifact)).isFalse
        assertThat(beta.exists(artifact)).isTrue
    }

    @Test
    fun `should isolate a prefixed repository from the bucket root without single-bucket mode`() {
        val prefixed = storageProvider(repository = "prefixed", prefix = "reposilite", sharedBucket = false)
        val root = storageProvider(repository = "root", prefix = "", sharedBucket = false)
        val artifact = "/com/example/artifact.jar".toLocation()

        assertOk(prefixed.putFile(artifact, "data".toByteArray().inputStream()))

        assertThat(String(assertOk(prefixed.getFile(artifact)).readBytes())).isEqualTo("data")
        assertThat(root.exists(artifact)).isFalse
    }

    // The storage usage figures are computed by walking the repository, so the walk has to see every stored
    // object and its size, both for a repository at the bucket root and for one scoped under a prefix.
    @Test
    fun `should walk every object with its size when the repository sits at the bucket root`() {
        assertOk(storageProvider.putFile("/com/example/app.jar".toLocation(), ByteArray(5).inputStream()))
        assertOk(storageProvider.putFile("/com/example/1.0/app.pom".toLocation(), ByteArray(3).inputStream()))
        assertOk(storageProvider.putFile("/top.txt".toLocation(), ByteArray(2).inputStream()))

        val files = storageProvider.walkFiles().associateBy { it.path }

        assertThat(files.keys).containsExactlyInAnyOrder("com/example/app.jar", "com/example/1.0/app.pom", "top.txt")
        assertThat(files.values.sumOf { it.sizeBytes }).isEqualTo(10L)
    }

    @Test
    fun `should walk only the objects of a repository scoped under a prefix in a shared bucket`() {
        val alpha = storageProvider(repository = "alpha")
        val beta = storageProvider(repository = "beta")

        assertOk(alpha.putFile("/com/example/app.jar".toLocation(), ByteArray(5).inputStream()))
        assertOk(alpha.putFile("/top.txt".toLocation(), ByteArray(2).inputStream()))
        assertOk(beta.putFile("/com/example/other.jar".toLocation(), ByteArray(100).inputStream()))

        val files = alpha.walkFiles()

        assertThat(files.map { it.path }).containsExactlyInAnyOrder("com/example/app.jar", "top.txt")
        assertThat(files.sumOf { it.sizeBytes }).isEqualTo(7L)
    }

    // Object counts above one ListObjectsV2 page (1000 keys) must still be walked completely.
    @Test
    fun `should count every object and byte when a directory holds more than a single S3 page`() {
        val entryCount = 1001

        repeat(entryCount) { i ->
            assertOk(storageProvider.putFile("/snapshots/file-$i.bin".toLocation(), ByteArray(2).inputStream()))
        }

        val files = storageProvider.walkFiles()

        assertThat(files).hasSize(entryCount)
        assertThat(files.sumOf { it.sizeBytes }).isEqualTo(entryCount * 2L)
    }

    // A zero-byte folder object at the repository prefix answers HEAD with 200 on real S3. The walk must not
    // classify the repository root by HEAD, or the whole repository reads as one empty file. Floci does not
    // reproduce that answer, so the client here reports it for the folder key and delegates everything else.
    @Test
    fun `should walk every object of a prefixed repository when a folder object exists at its prefix`() {
        val folderKey = "reposilite/alpha/"
        val folderAwareClient = object : DelegatingS3Client(
            S3Client.builder()
                .endpointOverride(URI.create(SharedContainers.floci.s3Endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(Region.of("us-east-1"))
                .build()
        ) {
            override fun headObject(headObjectRequest: HeadObjectRequest): HeadObjectResponse =
                when (headObjectRequest.key()) {
                    folderKey -> HeadObjectResponse.builder().contentLength(0L).build()
                    else -> super.headObject(headObjectRequest)
                }

            // Real S3 lists the folder object itself in the contents of its prefix; Floci leaves it out.
            override fun listObjectsV2(listObjectsV2Request: ListObjectsV2Request): ListObjectsV2Response {
                val response = super.listObjectsV2(listObjectsV2Request)
                if (listObjectsV2Request.prefix() != folderKey) return response
                return response.toBuilder()
                    .contents(response.contents() + S3Object.builder().key(folderKey).size(0L).build())
                    .build()
            }

            override fun listObjectsV2Paginator(listObjectsV2Request: ListObjectsV2Request): ListObjectsV2Iterable =
                ListObjectsV2Iterable(this, listObjectsV2Request)
        }
        val alpha = S3StorageProvider(
            failureFacade = FailureFacade(InMemoryLogger()),
            s3 = folderAwareClient,
            bucket = bucket,
            keyPrefix = folderKey,
        )
        assertOk(alpha.putFile("/com/example/app.jar".toLocation(), ByteArray(5).inputStream()))
        assertOk(alpha.putFile("/top.txt".toLocation(), ByteArray(2).inputStream()))

        val files = alpha.walkFiles()

        assertThat(files.map { it.path }).containsExactlyInAnyOrder("com/example/app.jar", "top.txt")
        assertThat(files.sumOf { it.sizeBytes }).isEqualTo(7L)
    }

    private fun storageProvider(repository: String, prefix: String = "reposilite", sharedBucket: Boolean = true): StorageProvider {
        val logger = InMemoryLogger()
        return StorageFacade().createStorageProvider(
            journalist = logger,
            failureFacade = FailureFacade(logger),
            workingDirectory = rootDirectory.toPath(),
            repository = repository,
            storageSettings = S3StorageProviderSettings(
                bucketName = bucket,
                endpoint = SharedContainers.floci.s3Endpoint(),
                accessKey = "test",
                secretKey = "test",
                region = "us-east-1",
                prefix = prefix,
                sharedBucket = sharedBucket
            )
        )!!
    }

}
