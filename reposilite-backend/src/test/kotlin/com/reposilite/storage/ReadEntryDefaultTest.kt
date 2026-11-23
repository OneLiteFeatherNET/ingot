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

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.SimpleDirectoryInfo
import io.javalin.http.ContentType
import io.javalin.http.HttpStatus
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import panda.std.Result.ok
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.attribute.FileTime
import java.time.Instant

private val STORED_AT: Instant = Instant.parse("2026-10-15T12:00:00Z")

/** A provider written against the interface before readEntry existed: it implements the older methods only. */
private class OlderProvider(private val files: Map<String, ByteArray>) : StorageProvider {

    override fun putFile(location: Location, inputStream: InputStream): Result<Unit, ErrorResponse> = ok(Unit)

    override fun getFile(location: Location): Result<InputStream, ErrorResponse> =
        files[location.toString()]?.let { ok<InputStream, ErrorResponse>(ByteArrayInputStream(it)) }
            ?: notFoundError("missing $location")

    override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> =
        if (location.toString() == "dir") {
            ok(SimpleDirectoryInfo("dir"))
        } else {
            files[location.toString()]
                ?.let { ok(DocumentInfo("app.jar", ContentType.APPLICATION_OCTET_STREAM, it.size.toLong(), STORED_AT)) }
                ?: notFoundError("missing $location")
        }

    override fun removeFile(location: Location): Result<Unit, ErrorResponse> = ok(Unit)
    override fun getFiles(location: Location): Result<List<Location>, ErrorResponse> = ok(emptyList())
    override fun getLastModifiedTime(location: Location): Result<FileTime, ErrorResponse> = notFoundError()
    override fun getFileSize(location: Location): Result<Long, ErrorResponse> = notFoundError()
    override fun exists(location: Location): Boolean = files.containsKey(location.toString())
    override fun usage(): Result<Long, ErrorResponse> = ok(0L)
    override fun canHold(contentLength: Long): Result<Long, ErrorResponse> = ok(contentLength)

}

@OptIn(ExperimentalIngotPluginApi::class)
internal class ReadEntryDefaultTest {

    private val provider = OlderProvider(mapOf("a/app.jar" to byteArrayOf(1, 2, 3)))

    @Test
    fun `should return the content of a provider without an override`() {
        val entry = assertOk(provider.readEntry(Location.of("a/app.jar")))

        assertThat(entry.content.use { it.readBytes() })
            .`as`("content equals what getFile returns")
            .containsExactly(1, 2, 3)
    }

    @Test
    fun `should return the size and modification time of getFileDetails`() {
        val entry = assertOk(provider.readEntry(Location.of("a/app.jar")))
        entry.content.close()

        assertThat(entry.size).`as`("size").isEqualTo(3L)
        assertThat(entry.lastModified).`as`("modification time").isEqualTo(STORED_AT)
    }

    @Test
    fun `should return the error of getFile for a missing file`() {
        val location = Location.of("a/missing.jar")

        val error = assertError(provider.readEntry(location))

        assertThat(error.status).`as`("status of getFile").isEqualTo(assertError(provider.getFile(location)).status)
        assertThat(error.status).`as`("status").isEqualTo(HttpStatus.NOT_FOUND.code)
    }

    @Test
    fun `should refuse a directory`() {
        val error = assertError(provider.readEntry(Location.of("dir")))

        assertThat(error.status).`as`("a directory has no content to read").isNotEqualTo(HttpStatus.OK.code)
    }

}
