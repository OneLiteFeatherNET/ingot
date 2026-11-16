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

@file:OptIn(net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi::class)

package net.onelitefeather.ingot.storage.azure

import com.azure.storage.blob.BlobContainerClientBuilder
import com.azure.storage.common.StorageSharedKeyCredential
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileType.FILE
import com.reposilite.storage.api.toLocation
import io.javalin.http.ContentType.APPLICATION_JAR
import net.onelitefeather.ingot.storage.StorageProviderContext
import net.onelitefeather.ingot.storage.capability.ReadinessOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import panda.std.Result
import java.io.File
import java.nio.file.Path
import java.util.UUID

/**
 * The provider against a real Blob service, the Azurite emulator, started in a container of its own for this class. Every test
 * creates its own container, so no test sees the blobs of another one. The cases are those of the storage contract of the server,
 * with the listing order of the service, which sorts names, and not the order of the file system.
 */
internal class AzureStorageContractIntegrationTest {

    companion object {

        /** Azurite 3.35.0, pinned by digest. */
        private const val AZURITE_IMAGE =
            "mcr.microsoft.com/azure-storage/azurite@sha256:647c63a91102a9d8e8000aab803436e1fc85fbb285e7ce830a82ee5d6661cf37"
        private const val BLOB_PORT = 10000
        private const val ACCOUNT = "devstoreaccount1"

        /** The public development key of Azurite. It authenticates against the local emulator only. */
        private const val ACCOUNT_KEY =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=="

        private lateinit var azurite: GenericContainer<*>

        @JvmStatic
        @BeforeAll
        fun startAzurite() {
            azurite = GenericContainer(DockerImageName.parse(AZURITE_IMAGE))
                .withExposedPorts(BLOB_PORT)
                .withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--skipApiVersionCheck")
                .withLabel("ingot-agent", System.getenv("INGOT_AGENT") ?: "azure-storage-integration")
                .waitingFor(Wait.forListeningPort())
            azurite.start()
        }

        @JvmStatic
        @AfterAll
        fun stopAzurite() {
            azurite.stop()
        }

        private fun endpoint(): String =
            "http://${azurite.host}:${azurite.getMappedPort(BLOB_PORT)}/$ACCOUNT"

    }

    @TempDir
    lateinit var workingDirectory: File

    private fun context(): StorageProviderContext {
        val logger: Journalist = InMemoryLogger()
        val root: Path = workingDirectory.toPath()
        return object : StorageProviderContext {
            override val journalist: Journalist = logger
            override val failureFacade: FailureFacade = FailureFacade(logger)
            override val workingDirectory: Path = root
            override val repositoryName: String = "repository"
        }
    }

    /** A repository in a container of its own, with the prefix and the block size given. */
    private fun repository(prefix: String = "", blockSize: Long = 8L * 1024 * 1024): AzureBlobStorageProvider {
        val container = "c-${UUID.randomUUID()}"
        BlobContainerClientBuilder()
            .endpoint(endpoint())
            .credential(StorageSharedKeyCredential(ACCOUNT, ACCOUNT_KEY))
            .containerName(container)
            .buildClient()
            .create()
        return repositoryIn(container, prefix, blockSize)
    }

    private fun repositoryIn(container: String, prefix: String = "", blockSize: Long = 8L * 1024 * 1024): AzureBlobStorageProvider =
        AzureBlobStorageProviderFactory().create(
            context(),
            AzureBlobStorageProviderSettings(
                endpoint = endpoint(),
                accountName = ACCOUNT,
                container = container,
                prefix = prefix,
                authentication = "account-key",
                accountKey = ACCOUNT_KEY,
                blockSize = blockSize,
            ),
        )

    private fun <T> assertOk(result: Result<T, *>): T {
        assertThat(result.isOk).`as`("the result is ok, error: ${if (result.isErr) result.error else "none"}").isTrue()
        return result.get()
    }

    private fun assertNotFound(result: Result<*, *>) {
        assertThat(result.isErr).`as`("the result is an error").isTrue()
        assertThat((result.error as ErrorResponse).status).`as`("status").isEqualTo(404)
    }

    @Test
    fun `should store and return a stored file`() {
        // given: a repository and a small file
        val provider = repository()
        val resource = "/directory/file.data".toLocation()

        // when: the file is stored and read back
        assertOk(provider.putFile(resource, "content".toByteArray().inputStream()))

        // then: the content is the same
        assertThat(provider.exists(resource)).isTrue()
        assertThat(String(assertOk(provider.getFile(resource)).readBytes())).isEqualTo("content")
    }

    @Test
    fun `should commit a file of several blocks as one blob`() {
        // given: a repository whose blocks are four bytes, and a file of three blocks
        val provider = repository(blockSize = 4)
        val resource = "/directory/blocks.data".toLocation()

        // when: the file is stored and read back
        assertOk(provider.putFile(resource, "0123456789AB".toByteArray().inputStream()))

        // then: the blob holds the whole content in order
        assertThat(String(assertOk(provider.getFile(resource)).readBytes())).isEqualTo("0123456789AB")
    }

    @Test
    fun `should return only the requested window of a stored file`() {
        // given: a stored file of known content
        val provider = repository()
        val resource = "/directory/ranged.data".toLocation()
        assertOk(provider.putFile(resource, "0123456789".toByteArray().inputStream()))

        // when: a window in the middle is read
        val window = String(assertOk(provider.getFile(resource, 3, 4)).readBytes())

        // then: only that window is returned
        assertThat(window).isEqualTo("3456")
    }

    @Test
    fun `should return an empty stream for a window that starts at the end of the file`() {
        // given: a stored file of ten bytes
        val provider = repository()
        val resource = "/directory/end.data".toLocation()
        assertOk(provider.putFile(resource, "0123456789".toByteArray().inputStream()))

        // when: a window starts at the end
        val window = assertOk(provider.getFile(resource, 10, 4)).readBytes()

        // then: the window is empty and it is not an error
        assertThat(window).isEmpty()
    }

    @Test
    fun `should return the bytes up to the end for a window that runs past the end`() {
        // given: a stored file of ten bytes
        val provider = repository()
        val resource = "/directory/past.data".toLocation()
        assertOk(provider.putFile(resource, "0123456789".toByteArray().inputStream()))

        // when: a window starts at 8 and asks for ten bytes
        val window = String(assertOk(provider.getFile(resource, 8, 10)).readBytes())

        // then: the two bytes up to the end are returned
        assertThat(window).isEqualTo("89")
    }

    @Test
    fun `should answer a non-existing file with not found`() {
        // when: a file that was never stored is read
        val response = repository().getFile("/not/found.data".toLocation())

        // then: the answer is not found
        assertNotFound(response)
    }

    @Test
    fun `should return valid file details`() {
        // given: a stored jar
        val provider = repository()
        val resource = "/directory/file.jar".toLocation()
        assertOk(provider.putFile(resource, "content".toByteArray().inputStream()))

        // when: the details are requested
        val details = assertOk(provider.getFileDetails(resource)) as DocumentInfo

        // then: the details carry the type, the name, the length and the content type of the extension
        assertThat(details.type).isEqualTo(FILE)
        assertThat(details.name).isEqualTo("file.jar")
        assertThat(details.contentLength).isEqualTo(7L)
        assertThat(details.contentType).isEqualTo(APPLICATION_JAR)
    }

    @Test
    fun `should list the entries of a directory`() {
        // given: files in two directories, one of them with a subdirectory
        val provider = repository()
        listOf("/a/a.jar", "/a/b.jar", "/a/c/a.jar", "/b/a.jar").forEach { path ->
            assertOk(provider.putFile(path.toLocation(), "content".toByteArray().inputStream()))
        }

        // when: the directory is requested
        val listing = assertOk(provider.getFileDetails("/a".toLocation())) as DirectoryInfo

        // then: its files and its subdirectory are listed, and nothing of the other directory
        assertThat(listing.files.map { it.name }).containsExactlyInAnyOrder("c", "a.jar", "b.jar")
    }

    @Test
    fun `should delete one file and keep its sibling`() {
        // given: two files in one directory
        val provider = repository()
        assertOk(provider.putFile("/test/test1.jar".toLocation(), "content".toByteArray().inputStream()))
        assertOk(provider.putFile("/test/test2.jar".toLocation(), "content".toByteArray().inputStream()))

        // when: one of them is removed
        assertOk(provider.removeFile("/test/test1.jar".toLocation()))

        // then: only the removed file is gone
        assertThat(provider.exists("/test/test1.jar".toLocation())).isFalse()
        assertThat(provider.exists("/test/test2.jar".toLocation())).isTrue()
    }

    @Test
    fun `should delete a directory with its files and subdirectories`() {
        // given: a directory with a nested file and a file outside of it
        val provider = repository()
        assertOk(provider.putFile("/directory/sub/sub/test.jar".toLocation(), "content".toByteArray().inputStream()))
        assertOk(provider.putFile("/directory/sub/test.jar".toLocation(), "content".toByteArray().inputStream()))
        assertOk(provider.putFile("/directory/test.jar".toLocation(), "content".toByteArray().inputStream()))

        // when: the subdirectory is removed
        assertOk(provider.removeFile("/directory/sub".toLocation()))

        // then: everything under it is gone and the file of the parent stays
        assertThat(provider.exists("/directory/sub/sub/test.jar".toLocation())).isFalse()
        assertThat(provider.exists("/directory/sub/test.jar".toLocation())).isFalse()
        assertThat(provider.exists("/directory/test.jar".toLocation())).isTrue()
    }

    @Test
    fun `should refuse to delete the repository root and keep every file`() {
        // given: a file that a successful root removal would delete
        val provider = repository()
        assertOk(provider.putFile("/keep/me.jar".toLocation(), "content".toByteArray().inputStream()))

        // when: locations that collapse to the root are removed
        listOf(".", "/").forEach { rootLike ->
            assertThat(provider.removeFile(rootLike.toLocation()).isErr).`as`("removal of $rootLike").isTrue()
        }

        // then: the file is still there
        assertThat(provider.exists("/keep/me.jar".toLocation())).isTrue()
    }

    @Test
    fun `should not delete a sibling directory that shares the name prefix`() {
        // given: a directory and a sibling whose name starts with the same characters
        val provider = repository()
        assertOk(provider.putFile("/com/example/foo/1.0.0/foo.jar".toLocation(), "content".toByteArray().inputStream()))
        assertOk(provider.putFile("/com/example/foo-bar/1.0.0/foo-bar.jar".toLocation(), "content".toByteArray().inputStream()))

        // when: the first directory is removed
        assertOk(provider.removeFile("/com/example/foo".toLocation()))

        // then: the sibling survives
        assertThat(provider.exists("/com/example/foo/1.0.0/foo.jar".toLocation())).isFalse()
        assertThat(provider.exists("/com/example/foo-bar/1.0.0/foo-bar.jar".toLocation())).isTrue()
    }

    @Test
    fun `should isolate two repositories that share one container by their prefixes`() {
        // given: two repositories in one container, with the prefixes alpha and beta
        val container = "c-${UUID.randomUUID()}"
        BlobContainerClientBuilder()
            .endpoint(endpoint())
            .credential(StorageSharedKeyCredential(ACCOUNT, ACCOUNT_KEY))
            .containerName(container)
            .buildClient()
            .create()
        val alpha = repositoryIn(container, prefix = "alpha")
        val beta = repositoryIn(container, prefix = "beta")
        val artifact = "/com/example/artifact.jar".toLocation()

        // when: each repository writes its own version of the same path
        assertOk(alpha.putFile(artifact, "alpha".toByteArray().inputStream()))

        // then: beta does not see the file of alpha
        assertThat(alpha.exists(artifact)).isTrue()
        assertThat(beta.exists(artifact)).isFalse()

        assertOk(beta.putFile(artifact, "beta".toByteArray().inputStream()))
        assertThat(String(assertOk(alpha.getFile(artifact)).readBytes())).isEqualTo("alpha")
        assertThat(String(assertOk(beta.getFile(artifact)).readBytes())).isEqualTo("beta")
    }

    @Test
    fun `should report a missing location as not existing and an empty container as healthy`() {
        // given: a repository without any blob
        val provider = repository()

        // then: the location does not exist, and the empty container is healthy
        assertThat(provider.exists("/missing".toLocation())).isFalse()
        assertThat(provider.probe()).isEqualTo(ReadinessOutcome.Ok)
    }

}
