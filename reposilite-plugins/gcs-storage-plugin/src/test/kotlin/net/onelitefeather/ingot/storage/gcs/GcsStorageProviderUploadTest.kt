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

package net.onelitefeather.ingot.storage.gcs

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream

/** The upload rules of the provider (design D4): one request for a file that fits one chunk, resumable chunks for a longer one. */
internal class GcsStorageProviderUploadTest {

    private val client = FakeGcsObjectClient()

    private fun provider(chunkSize: Int = 4, objectPrefix: String = "") =
        GcsStorageProvider(
            failureFacade = FailureFacade(InMemoryLogger()),
            client = client,
            objectPrefix = objectPrefix,
            chunkSize = chunkSize,
        )

    private fun assertOk(result: Result<*, *>) {
        assertThat(result.isOk).`as`("the result is ok").isTrue()
    }

    @Test
    fun `should write a small file in one request with its content`() {
        // when: a file shorter than one chunk is written
        assertOk(provider(chunkSize = 8).putFile("/a/small.jar".toLocation(), "12345".byteInputStream()))

        // then: one request creates the object, and no resumable upload is opened
        assertThat(client.creates).`as`("single requests").containsExactly("a/small.jar")
        assertThat(client.openedUploads).`as`("resumable uploads").isEmpty()
        assertThat(String(client.objects.getValue("a/small.jar"))).`as`("stored content").isEqualTo("12345")
    }

    @Test
    fun `should write a file of exactly one chunk in one request`() {
        // when: the content is as long as one chunk and ends there
        assertOk(provider(chunkSize = 4).putFile("/a/exact.jar".toLocation(), "0123".byteInputStream()))

        // then: the boundary does not open a resumable upload
        assertThat(client.creates).`as`("single requests").containsExactly("a/exact.jar")
        assertThat(client.openedUploads).`as`("resumable uploads").isEmpty()
    }

    @Test
    fun `should write a longer file as resumable chunks of the configured size`() {
        // when: fourteen bytes are written with a chunk size of four
        assertOk(provider(chunkSize = 4).putFile("/a/large.jar".toLocation(), "0123456789ABCD".byteInputStream()))

        // then: one resumable upload is opened with that chunk size, the writes are four bytes each except the last one
        assertThat(client.openedUploads).`as`("chunk sizes of the uploads").containsExactly(4)
        assertThat(client.uploadWrites).`as`("writes of the upload").containsExactly(listOf(4, 4, 4, 2))
        assertThat(String(client.objects.getValue("a/large.jar"))).`as`("stored content").isEqualTo("0123456789ABCD")
        assertThat(client.creates).`as`("single requests").isEmpty()
    }

    @Test
    fun `should not make an object visible when the input fails during a resumable upload`() {
        // given: the input fails after its first two chunks
        val failing = SequenceInputStream(
            "012345678".byteInputStream(),
            object : InputStream() {
                override fun read(): Int = throw IOException("network lost")
            },
        )

        // when: the file is written
        val result = provider(chunkSize = 4).putFile("/a/broken.jar".toLocation(), failing)

        // then: the write reports a failure and no object exists at the location
        assertThat(result.isErr).`as`("the write fails").isTrue()
        assertThat(client.objects).`as`("visible objects").doesNotContainKey("a/broken.jar")
    }

    @Test
    fun `should place the object under the prefix of the repository`() {
        // when: a repository with the prefix team is written
        assertOk(provider(chunkSize = 8, objectPrefix = "team/").putFile("/a/small.jar".toLocation(), "1".byteInputStream()))

        // then: the object name carries the prefix
        assertThat(client.creates).`as`("single requests").containsExactly("team/a/small.jar")
    }

    @Test
    fun `should send the content type that the extension of the file names`() {
        // when: an HTML file is written
        assertOk(provider(chunkSize = 8).putFile("/a/index.html".toLocation(), "<p/>".byteInputStream()))

        // then: the content type follows the extension, as the S3 provider sets it
        assertThat(client.contentTypes["a/index.html"]).`as`("content type").isEqualTo("text/html")
    }

}
