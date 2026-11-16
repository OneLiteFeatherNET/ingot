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
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.toLocation
import io.javalin.http.ContentType.APPLICATION_OCTET_STREAM
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.NOT_FOUND
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import java.io.InputStream
import java.time.Instant

/** The read rules of the provider (design D5 and D6 for blobs): ranged reads, their edge cases, and the properties of a blob. */
internal class AzureBlobStorageProviderReadTest {

    private val client = FakeAzureBlobClient()
    private val provider = AzureBlobStorageProvider(
        failureFacade = FailureFacade(InMemoryLogger()),
        client = client,
        blobPrefix = "",
        blockSize = 8,
    )

    private val resource = "/directory/ranged.data".toLocation()

    init {
        // given: a stored blob of ten bytes, which the tests read in its different ranges
        provider.putFile(resource, "0123456789".byteInputStream())
    }

    private fun <T> assertOk(result: Result<T, *>): T {
        assertThat(result.isOk).`as`("the result is ok").isTrue()
        return result.get()
    }

    private fun assertFailsWithStatus(result: Result<*, *>, status: Int) {
        assertThat(result.isErr).`as`("the result is an error").isTrue()
        assertThat((result.error as com.reposilite.shared.ErrorResponse).status)
            .`as`("status of the error")
            .isEqualTo(status)
    }

    private fun InputStream.text(): String = String(readBytes())

    @Test
    fun `should read a stored blob in full`() {
        // when: the blob is read without a range
        val content = assertOk(provider.getFile(resource)).text()

        // then: the whole content is returned
        assertThat(content).isEqualTo("0123456789")
    }

    @Test
    fun `should transfer only the requested window with a ranged read`() {
        // when: a window in the middle of the blob is read
        val window = assertOk(provider.getFile(resource, 3, 4)).text()

        // then: only that window is returned, and the service was asked for that range alone
        assertThat(window).isEqualTo("3456")
        assertThat(client.downloads.last()).isEqualTo(Triple("directory/ranged.data", 3L, 4L))
    }

    @Test
    fun `should return an empty stream for a range of length zero without reading the content`() {
        // when: a range of length zero is read
        val window = assertOk(provider.getFile(resource, 2, 0)).text()

        // then: the stream is empty and no content was downloaded
        assertThat(window).isEmpty()
        assertThat(client.downloads).`as`("content downloads").isEmpty()
    }

    @Test
    fun `should return an empty stream when the offset is at the end of the blob`() {
        // when: the offset equals the size of the blob
        val window = provider.getFile(resource, 10, 4)

        // then: an empty stream is returned, not an error
        assertThat(assertOk(window).text()).isEmpty()
    }

    @Test
    fun `should return the bytes up to the end when the range extends past the end`() {
        // when: a range starts at 8 and asks for ten bytes
        val window = assertOk(provider.getFile(resource, 8, 10)).text()

        // then: the two bytes up to the end are returned
        assertThat(window).isEqualTo("89")
    }

    @Test
    fun `should answer a range refused by the service with an empty stream and not an error`() {
        // given: the service refuses every range, as it does for a range past a size that changed
        client.refuseEveryRange = true

        // when: a range is read
        val window = provider.getFile(resource, 2, 3)

        // then: the refusal reaches the caller as an empty stream
        assertThat(assertOk(window).text()).isEmpty()
    }

    @Test
    fun `should refuse a negative offset before any call to the service`() {
        // when: a negative offset is requested
        val window = provider.getFile(resource, -1, 3)

        // then: the request is an argument error, and the service was not called
        assertFailsWithStatus(window, BAD_REQUEST.code)
        assertThat(client.downloads).`as`("content downloads").isEmpty()
    }

    @Test
    fun `should answer a missing blob read with a range as not found`() {
        // when: a range of a blob that does not exist is read
        val window = provider.getFile("/missing.data".toLocation(), 0, 2)

        // then: the answer is not found
        assertFailsWithStatus(window, NOT_FOUND.code)
    }

    @Test
    fun `should report the size of a stored blob`() {
        // when: the size is requested
        val size = assertOk(provider.getFileSize(resource))

        // then: the size is the length of the content
        assertThat(size).isEqualTo(10L)
    }

    @Test
    fun `should report the last modification of a stored blob`() {
        // when: the last modification is requested
        val modified = assertOk(provider.getLastModifiedTime(resource))

        // then: the time comes from the service properties
        assertThat(modified.toInstant()).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"))
    }

    @Test
    fun `should describe a stored blob with its name, length and content type`() {
        // when: the details of the blob are requested
        val details = assertOk(provider.getFileDetails(resource)) as DocumentInfo

        // then: the details carry the name, the length and the content type of the extension
        assertThat(details.name).isEqualTo("ranged.data")
        assertThat(details.contentLength).isEqualTo(10L)
        assertThat(details.contentType).isEqualTo(APPLICATION_OCTET_STREAM)
    }

    @Test
    fun `should report a missing blob as not found and as not existing`() {
        // when: a blob that was never stored is looked up
        val missing = "/missing.data".toLocation()

        // then: the details are not found and the blob does not exist
        assertFailsWithStatus(provider.getFileDetails(missing), NOT_FOUND.code)
        assertThat(provider.exists(missing)).`as`("exists").isFalse()
        assertThat(provider.exists(resource)).`as`("exists for the stored blob").isTrue()
    }

}
