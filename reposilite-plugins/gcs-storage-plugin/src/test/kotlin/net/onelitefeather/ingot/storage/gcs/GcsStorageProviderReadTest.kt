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
import java.io.InputStream

/** The read rules of the provider (design D5): the whole object, a ranged read, empty ranges and a missing object. */
internal class GcsStorageProviderReadTest {

    private val client = FakeGcsObjectClient().apply {
        objects["a/data.bin"] = ByteArray(100) { it.toByte() }
    }

    private val provider = GcsStorageProvider(
        failureFacade = FailureFacade(InMemoryLogger()),
        client = client,
        objectPrefix = "",
        chunkSize = 4,
    )

    private fun bytesOf(result: Result<InputStream, *>): ByteArray {
        assertThat(result.isOk).`as`("the read is ok").isTrue()
        return result.get().readBytes()
    }

    @Test
    fun `should return the whole object when no range is given`() {
        // when: the object is read without a range
        val content = bytesOf(provider.getFile("/a/data.bin".toLocation()))

        // then: all of its bytes are returned
        assertThat(content).`as`("content").hasSize(100)
    }

    @Test
    fun `should read exactly the requested range with a ranged request`() {
        // when: ten bytes from offset ten are read
        val content = bytesOf(provider.getFile("/a/data.bin".toLocation(), offset = 10, length = 10))

        // then: exactly those bytes are returned, and the service was asked for that range only
        assertThat(content.map { it.toInt() }).`as`("content").containsExactly(10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
        assertThat(client.reads).`as`("ranged requests").containsExactly(Triple("a/data.bin", 10L, 10L))
    }

    @Test
    fun `should return an empty stream without a request for a zero length`() {
        // when: a range of length zero is read
        val content = bytesOf(provider.getFile("/a/data.bin".toLocation(), offset = 10, length = 0))

        // then: nothing is returned and no request reached the service
        assertThat(content).`as`("content").isEmpty()
        assertThat(client.reads).`as`("requests").isEmpty()
    }

    @Test
    fun `should answer an offset at the end of the object with an empty stream and no error`() {
        // when: the offset is the size of the object
        val result = provider.getFile("/a/data.bin".toLocation(), offset = 100, length = 1)

        // then: the answer is an empty stream, not an error
        assertThat(bytesOf(result)).`as`("content").isEmpty()
    }

    @Test
    fun `should truncate a range that ends past the end of the object`() {
        // when: fifty bytes are requested from offset eighty of a hundred bytes
        val content = bytesOf(provider.getFile("/a/data.bin".toLocation(), offset = 80, length = 50))

        // then: only the twenty bytes that exist are returned, and the service was asked for those
        assertThat(content).`as`("content").hasSize(20)
        assertThat(client.reads).`as`("ranged requests").containsExactly(Triple("a/data.bin", 80L, 20L))
    }

    @Test
    fun `should answer a missing object with not found`() {
        // when: an object that does not exist is read
        val result = provider.getFile("/a/missing.bin".toLocation())

        // then: the answer is a not found error for that location
        assertThat(result.isErr).`as`("the read fails").isTrue()
        assertThat(result.error.status).`as`("status").isEqualTo(404)
    }

}
