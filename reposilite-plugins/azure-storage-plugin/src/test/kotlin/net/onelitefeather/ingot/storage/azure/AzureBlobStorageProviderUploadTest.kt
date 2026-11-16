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

package net.onelitefeather.ingot.storage.azure

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import java.io.InputStream

/** The upload rules of the provider (design D4): one put for a small file, staged blocks committed once for a larger one. */
internal class AzureBlobStorageProviderUploadTest {

    private val client = FakeAzureBlobClient()

    private fun assertOk(result: Result<*, *>) {
        assertThat(result.isOk).`as`("the result is ok").isTrue()
    }

    private fun assertError(result: Result<*, *>) {
        assertThat(result.isErr).`as`("the result is an error").isTrue()
    }

    private fun provider(blockSize: Int = 4, blobPrefix: String = "", newUploadId: () -> String = { "upload-1" }) =
        AzureBlobStorageProvider(
            failureFacade = FailureFacade(InMemoryLogger()),
            client = client,
            blobPrefix = blobPrefix,
            blockSize = blockSize,
            newUploadId = newUploadId,
        )

    @Test
    fun `should write a file that fits one block with one put blob`() {
        // when: a file shorter than one block is uploaded
        assertOk(provider(blockSize = 8).putFile("/a/small.jar".toLocation(), "12345".byteInputStream()))

        // then: one blob is put, and no block is staged or committed
        assertThat(client.putNames).`as`("put blob requests").containsExactly("a/small.jar")
        assertThat(client.stagedBlocks).`as`("staged blocks").isEmpty()
        assertThat(client.commitCalls).`as`("block list commits").isEmpty()
    }

    @Test
    fun `should stage three blocks and commit them once for a three block file`() {
        // when: a file of three blocks is uploaded
        assertOk(provider(blockSize = 4).putFile("/a/large.jar".toLocation(), "0123456789AB".byteInputStream()))

        // then: three blocks are staged and one block list commits them in order
        assertThat(client.stagedBlocks.getValue("a/large.jar")).`as`("staged blocks").hasSize(3)
        assertThat(client.commitCalls).`as`("block list commits").hasSize(1)
        assertThat(client.commitCalls.single().second).`as`("committed blocks in order")
            .containsExactlyElementsOf(client.stagedBlocks.getValue("a/large.jar").keys.toList())
        assertThat(String(client.committed.getValue("a/large.jar"))).`as`("committed content").isEqualTo("0123456789AB")
    }

    @Test
    fun `should not make any blob visible when the upload fails before the commit`() {
        // given: staging fails for the second block
        client.stageFailure = IllegalStateException("network lost")

        // when: a multi-block file is uploaded
        val response = provider(blockSize = 4).putFile("/a/broken.jar".toLocation(), "0123456789AB".byteInputStream())

        // then: the upload is an error, nothing is committed and no blob is visible
        assertError(response)
        assertThat(client.commitCalls).`as`("block list commits").isEmpty()
        assertThat(client.committed).`as`("visible blobs").doesNotContainKey("a/broken.jar")
    }

    @Test
    fun `should hold at most one block of the stream when it stages a block`() {
        // given: a stream that counts the bytes read from it
        val stream = CountingInputStream("0123456789ABCDEF".byteInputStream())
        var readAtFirstStage = -1L
        client.beforeStage = { _, _ -> if (readAtFirstStage < 0) readAtFirstStage = stream.count }

        // when: a stream of four blocks is uploaded with a block size of four
        assertOk(provider(blockSize = 4).putFile("/a/streamed.jar".toLocation(), stream))

        // then: the first block was staged before the stream was read to its end, and it held no more than one block and one look-ahead byte
        assertThat(readAtFirstStage).`as`("bytes read before the first stage").isBetween(4L, 5L)
    }

    @Test
    fun `should use block ids of equal length that differ between two uploads of one blob`() {
        // given: two uploads of the same blob with two different upload identifiers of equal length
        val first = provider(blockSize = 2, newUploadId = { "upload-A" })
        val second = provider(blockSize = 2, newUploadId = { "upload-B" })

        // when: both upload the same blob
        assertOk(first.putFile("/a/shared.jar".toLocation(), "abcd".byteInputStream()))
        val firstIds = client.commitCalls.last().second
        assertOk(second.putFile("/a/shared.jar".toLocation(), "wxyz".byteInputStream()))
        val secondIds = client.commitCalls.last().second

        // then: the block ids of the two uploads never coincide and have the same length
        assertThat(firstIds.intersect(secondIds.toSet())).`as`("shared block ids").isEmpty()
        assertThat(firstIds.map { it.length }.toSet() + secondIds.map { it.length }.toSet())
            .`as`("block id lengths").hasSize(1)
    }

    @Test
    fun `should store the blob under the configured prefix`() {
        // when: a file is uploaded to a provider with a blob prefix
        assertOk(provider(blobPrefix = "repos/").putFile("/com/example/a.jar".toLocation(), "x".byteInputStream()))

        // then: the blob name starts with the prefix
        assertThat(client.putNames).`as`("blob names").containsExactly("repos/com/example/a.jar")
    }

    @Test
    fun `should send the content type of the file extension`() {
        // when: a jar is uploaded
        assertOk(provider().putFile("/a/app.jar".toLocation(), "x".byteInputStream()))

        // then: the blob carries the jar content type
        assertThat(client.contentTypes["a/app.jar"]).`as`("content type").isEqualTo("application/java-archive")
    }

    /** Counts the bytes that were read from the wrapped stream. */
    private class CountingInputStream(private val delegate: InputStream) : InputStream() {

        var count = 0L
            private set

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) count++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = delegate.read(buffer, offset, length)
            if (read > 0) count += read
            return read
        }

    }

}
