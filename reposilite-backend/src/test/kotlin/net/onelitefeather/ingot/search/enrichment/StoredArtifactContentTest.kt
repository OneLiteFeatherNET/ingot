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

package net.onelitefeather.ingot.search.enrichment

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.stream.BoundedInputStreamLimitExceededException
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import io.javalin.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import panda.std.Result
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.attribute.FileTime

internal class StoredArtifactContentTest {

    private val content = ByteArray(32) { it.toByte() }
    private val location = Location.of("releases/org/example/app/1.0/app-1.0.jar")

    @Test
    fun `a range inside the byte budget is served from the stored file`() {
        val storage = FakeStorage(content)
        val handle = StoredArtifactContent(storage, location, sizeBytes = content.size.toLong(), budgetBytes = 10)

        val bytes = handle.range(offset = 2, length = 4).readBytes()

        assertThat(bytes).`as`("bytes of the range 2 to 6").isEqualTo(content.copyOfRange(2, 6))
    }

    @Test
    fun `a range beyond the byte budget throws and reads nothing from the storage`() {
        val storage = FakeStorage(content)
        val handle = StoredArtifactContent(storage, location, sizeBytes = content.size.toLong(), budgetBytes = 10)

        assertThatThrownBy { handle.range(offset = 0, length = 11) }
            .`as`("a range of 11 bytes against a budget of 10")
            .isInstanceOf(ArtifactContentBudgetExceededException::class.java)
        assertThat(storage.reads).`as`("storage reads of the refused range").isEmpty()
    }

    @Test
    fun `the budget counts every range read of one call`() {
        val storage = FakeStorage(content)
        val handle = StoredArtifactContent(storage, location, sizeBytes = content.size.toLong(), budgetBytes = 10)
        handle.range(offset = 0, length = 6)

        assertThatThrownBy { handle.range(offset = 6, length = 5) }
            .`as`("the second range that brings the call to 11 bytes against a budget of 10")
            .isInstanceOf(ArtifactContentBudgetExceededException::class.java)
        assertThat(storage.reads).`as`("storage reads after the refused range").hasSize(1)
    }

    @Test
    fun `every read goes to the location the handle was created for`() {
        val storage = FakeStorage(content)
        val handle = StoredArtifactContent(storage, location, sizeBytes = content.size.toLong(), budgetBytes = 32)

        handle.range(offset = 0, length = 4).readBytes()
        handle.range(offset = 8, length = 4).readBytes()

        assertThat(storage.reads.map { it.location }).`as`("locations of the storage reads").containsOnly(location)
    }

    @Test
    fun `a stream never yields more bytes than its range asked for`() {
        val storage = FakeStorage(content, ignoreRange = true)
        val handle = StoredArtifactContent(storage, location, sizeBytes = content.size.toLong(), budgetBytes = 32)

        assertThatThrownBy { handle.range(offset = 0, length = 4).readBytes() }
            .`as`("reading a storage that returns more than the requested 4 bytes")
            .isInstanceOf(BoundedInputStreamLimitExceededException::class.java)
    }

    @Test
    fun `a storage failure is an io error that carries no storage message`() {
        val storage = FakeStorage(content, failure = ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "secret path /var/data"))
        val handle = StoredArtifactContent(storage, location, sizeBytes = content.size.toLong(), budgetBytes = 32)

        assertThatThrownBy { handle.range(offset = 0, length = 4) }
            .`as`("a range of a storage that failed")
            .isInstanceOf(IOException::class.java)
            .hasMessageNotContaining("secret path")
    }

    private class FakeStorage(
        private val content: ByteArray,
        private val ignoreRange: Boolean = false,
        private val failure: ErrorResponse? = null,
    ) : StorageProvider by Unsupported {

        val reads = mutableListOf<Read>()

        data class Read(val location: Location, val offset: Long, val length: Long)

        override fun getFile(location: Location, offset: Long, length: Long): Result<InputStream, ErrorResponse> {
            reads += Read(location, offset, length)
            failure?.let { return Result.error(it) }
            if (ignoreRange) return Result.ok(ByteArrayInputStream(ByteArray(100)))
            return Result.ok(ByteArrayInputStream(content.copyOfRange(offset.toInt(), (offset + length).toInt())))
        }

    }

    private object Unsupported : StorageProvider {
        override fun putFile(location: Location, inputStream: InputStream): Result<Unit, ErrorResponse> = throw UnsupportedOperationException()
        override fun getFile(location: Location): Result<InputStream, ErrorResponse> = throw UnsupportedOperationException()
        override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> = throw UnsupportedOperationException()
        override fun removeFile(location: Location): Result<Unit, ErrorResponse> = throw UnsupportedOperationException()
        override fun getFiles(location: Location): Result<List<Location>, ErrorResponse> = throw UnsupportedOperationException()
        override fun getLastModifiedTime(location: Location): Result<FileTime, ErrorResponse> = throw UnsupportedOperationException()
        override fun getFileSize(location: Location): Result<Long, ErrorResponse> = throw UnsupportedOperationException()
        override fun exists(location: Location): Boolean = throw UnsupportedOperationException()
        override fun usage(): Result<Long, ErrorResponse> = throw UnsupportedOperationException()
        override fun canHold(contentLength: Long): Result<Long, ErrorResponse> = throw UnsupportedOperationException()
    }

}
