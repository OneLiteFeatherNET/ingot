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

package net.onelitefeather.ingot.storage.gcs

import com.google.cloud.storage.BucketInfo
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageEntrySource
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.toLocation
import net.onelitefeather.ingot.storage.capability.ReadinessOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.InputStream
import java.util.UUID

/**
 * The provider against the Google Cloud Storage emulator `fake-gcs-server`, started in a container for this class. Every test writes into a
 * bucket of its own, so no test sees the objects of another one. The client talks to the emulator over HTTP with no credentials, as the
 * endpoint of the settings does. The image is pinned by tag and digest.
 */
internal class GcsStorageContractIntegrationTest {

    companion object {

        /** fake-gcs-server 1.56.1 (BSD-2-Clause), pinned by digest. */
        private const val FAKE_GCS_IMAGE =
            "fsouza/fake-gcs-server:1.56.1@sha256:797ce226d62f947c009dc40246b30cfb456b8473d8241407f9d6f2c04e4d69ef"
        private const val PORT = 4443
        private const val PROJECT = "test-project"
        private const val MIB = 1024 * 1024

        private lateinit var emulator: GenericContainer<*>
        private lateinit var endpoint: String

        @JvmStatic
        @BeforeAll
        fun startEmulator() {
            emulator = GenericContainer(DockerImageName.parse(FAKE_GCS_IMAGE))
                .withExposedPorts(PORT)
                .withCommand("-scheme", "http", "-port", PORT.toString())
                .withLabel("ingot-agent", System.getenv("INGOT_AGENT") ?: "gcs-storage-integration")
                .waitingFor(Wait.forListeningPort())
            emulator.start()
            endpoint = "http://${emulator.host}:${emulator.getMappedPort(PORT)}"
        }

        @JvmStatic
        @AfterAll
        fun stopEmulator() {
            if (::emulator.isInitialized) {
                emulator.stop()
            }
        }

    }

    /** A new bucket and a provider for it, so that the test sees no object of another test. */
    private fun providerInNewBucket(chunkSize: Int = 8 * MIB): Pair<GcsStorageProvider, String> {
        val bucket = "repositories-${UUID.randomUUID()}"
        val options = storageOptionsOf(GcsStorageProviderSettings(bucket = bucket, endpoint = endpoint, projectId = PROJECT)).build()
        options.service.create(BucketInfo.of(bucket))
        val provider = GcsStorageProvider(
            failureFacade = FailureFacade(InMemoryLogger()),
            client = GcsSdkObjectClient(options.service, bucket),
            objectPrefix = "",
            chunkSize = chunkSize,
            bucket = bucket,
        )
        return provider to bucket
    }

    private fun bytesOf(content: InputStream): ByteArray = content.use { it.readBytes() }

    @Test
    fun `should write a small file and read the same bytes back`() {
        // given: a provider in a new bucket
        val (provider, _) = providerInNewBucket()

        // when: a small file is written and read
        provider.putFile("/a/small.jar".toLocation(), "hello".byteInputStream())
        val content = bytesOf(provider.getFile("/a/small.jar".toLocation()).get())

        // then: the bytes are the same
        assertThat(String(content)).`as`("content").isEqualTo("hello")
    }

    @Test
    fun `should write a file of twenty MiB as resumable chunks and read the same bytes back`() {
        // given: a provider with the default chunk size of 8 MiB
        val (provider, _) = providerInNewBucket()
        val payload = ByteArray(20 * MIB) { (it % 251).toByte() }

        // when: the file is written and read
        provider.putFile("/a/large.jar".toLocation(), payload.inputStream())
        val content = bytesOf(provider.getFile("/a/large.jar".toLocation()).get())

        // then: every byte is the one that was written
        assertThat(content).`as`("content").isEqualTo(payload)
    }

    @Test
    fun `should show no object while a resumable upload is still running`() {
        // given: a provider and a stream of twenty MiB that asks the provider in its middle
        val (provider, _) = providerInNewBucket()
        val location = "/a/running.jar".toLocation()
        val payload = ByteArray(20 * MIB) { (it % 251).toByte() }
        var visibleDuringUpload: Boolean? = null
        val stream = object : InputStream() {
            private var position = 0

            override fun read(): Int {
                val single = ByteArray(1)
                return if (read(single, 0, 1) == -1) -1 else single[0].toInt() and 0xff
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                // The first read past twelve MiB runs while the upload of the first chunk is open and the second one is not finished
                if (visibleDuringUpload == null && position >= 12 * MIB) {
                    visibleDuringUpload = provider.exists(location)
                }
                if (position >= payload.size) return -1
                val count = minOf(length, payload.size - position)
                payload.copyInto(buffer, offset, position, position + count)
                position += count
                return count
            }
        }

        // when: the upload runs to its end
        provider.putFile(location, stream)

        // then: the object was not visible in the middle, and it is complete afterwards
        assertThat(visibleDuringUpload).`as`("visible during the upload").isFalse()
        assertThat(bytesOf(provider.getFile(location).get())).`as`("content after the upload").hasSize(20 * MIB)
    }

    @Test
    fun `should read a byte range with the service`() {
        // given: a file of a hundred bytes
        val (provider, _) = providerInNewBucket()
        provider.putFile("/a/range.bin".toLocation(), ByteArray(100) { it.toByte() }.inputStream())

        // when: ten bytes from offset ten are read
        val content = bytesOf(provider.getFile("/a/range.bin".toLocation(), offset = 10, length = 10).get())

        // then: exactly those bytes are returned
        assertThat(content.map { it.toInt() }).`as`("range").containsExactly(10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
    }

    @Test
    fun `should list a directory with its files and its deeper directories`() {
        // given: two files and a file in a deeper directory
        val (provider, _) = providerInNewBucket()
        provider.putFile("/d/one.jar".toLocation(), "1".byteInputStream())
        provider.putFile("/d/two.jar".toLocation(), "2".byteInputStream())
        provider.putFile("/d/sub/three.jar".toLocation(), "3".byteInputStream())

        // when: the directory is listed
        val entries = provider.getFiles("/d".toLocation()).get().map { it.toString() }

        // then: the entries are the files and the deeper directory, in name order
        assertThat(entries).`as`("entries").containsExactly("d/one.jar", "d/sub", "d/two.jar")
    }

    @Test
    fun `should page the flat listing with the path cursor and return each file once`() {
        // given: five files
        val (provider, _) = providerInNewBucket()
        for (index in 1..5) {
            provider.putFile("/p/$index.jar".toLocation(), "$index".byteInputStream())
        }
        val source = provider as StorageEntrySource

        // when: the listing is read in pages of two
        val first = source.listEntries("/p".toLocation(), after = null, limit = 2).get()
        val second = source.listEntries("/p".toLocation(), after = first.next, limit = 2).get()
        val third = source.listEntries("/p".toLocation(), after = second.next, limit = 2).get()

        // then: each file appears once, in name order, and the last page has no cursor
        val names = (first.entries + second.entries + third.entries).map { it.path.toString() }
        assertThat(names).`as`("names").containsExactly("p/1.jar", "p/2.jar", "p/3.jar", "p/4.jar", "p/5.jar")
        assertThat(third.next).`as`("cursor of the last page").isNull()
    }

    @Test
    fun `should report the details of a file and of a directory`() {
        // given: a file in a directory
        val (provider, _) = providerInNewBucket()
        provider.putFile("/d/file.jar".toLocation(), "four".byteInputStream())

        // then: the file reports its size, and the directory reports its entries
        assertThat((provider.getFileDetails("/d/file.jar".toLocation()).get() as DocumentInfo).contentLength).`as`("size").isEqualTo(4L)
        assertThat((provider.getFileDetails("/d".toLocation()).get() as DirectoryInfo).files.map { it.name })
            .`as`("directory entries").containsExactly("file.jar")
    }

    @Test
    fun `should remove a directory and keep the objects beside it`() {
        // given: a directory and a neighbour
        val (provider, _) = providerInNewBucket()
        provider.putFile("/d/one.jar".toLocation(), "1".byteInputStream())
        provider.putFile("/d/sub/two.jar".toLocation(), "2".byteInputStream())
        provider.putFile("/keep.jar".toLocation(), "3".byteInputStream())

        // when: the directory is removed
        val removal = provider.removeFile("/d".toLocation())

        // then: the directory is gone and the neighbour is not
        assertThat(removal.isOk).`as`("removal").isTrue()
        assertThat(provider.exists("/d".toLocation())).`as`("directory exists").isFalse()
        assertThat(provider.exists("/keep.jar".toLocation())).`as`("neighbour exists").isTrue()
    }

    @Test
    fun `should answer a missing object with not found`() {
        // given: an empty bucket
        val (provider, _) = providerInNewBucket()

        // when: a missing object is read
        val result = provider.getFile("/missing.jar".toLocation())

        // then: the answer is not found
        assertThat(result.error.status).`as`("status").isEqualTo(404)
    }

    @Test
    fun `should report readiness for a bucket that answers`() {
        // given: a bucket that exists
        val (provider, _) = providerInNewBucket()

        // then: the probe is ok
        assertThat(provider.probe()).`as`("readiness").isEqualTo(ReadinessOutcome.Ok)
    }

    @Test
    fun `should report a missing bucket as not ready`() {
        // given: a provider for a bucket that was never created
        val bucket = "never-created-${UUID.randomUUID()}"
        val options = storageOptionsOf(GcsStorageProviderSettings(bucket = bucket, endpoint = endpoint, projectId = PROJECT)).build()
        val provider = GcsStorageProvider(
            failureFacade = FailureFacade(InMemoryLogger()),
            client = GcsSdkObjectClient(options.service, bucket),
            objectPrefix = "",
            chunkSize = 8 * MIB,
            bucket = bucket,
        )

        // then: the probe names the missing bucket
        assertThat((provider.probe() as ReadinessOutcome.Failed).reason).`as`("reason").contains("missing")
    }

}
