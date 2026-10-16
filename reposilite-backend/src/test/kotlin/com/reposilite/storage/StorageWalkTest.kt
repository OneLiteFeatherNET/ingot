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

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.SimpleDirectoryInfo
import io.javalin.http.ContentType
import io.javalin.http.HttpStatus
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

internal class StorageWalkTest {

    @TempDir
    lateinit var root: File

    /** A storage whose listings are scripted per path; a path without an entry is reported as not found. */
    private class FakeStorage(private val details: Map<String, Result<out FileDetails, ErrorResponse>>) :
        StorageProvider by Unsupported {

        override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> =
            details[location.toString()] ?: Result.error(ErrorResponse(HttpStatus.NOT_FOUND, "no listing scripted for '$location'"))

    }

    private object Unsupported : StorageProvider {
        override fun putFile(location: Location, inputStream: java.io.InputStream) = throw UnsupportedOperationException()
        override fun getFile(location: Location) = throw UnsupportedOperationException()
        override fun getFileDetails(location: Location) = throw UnsupportedOperationException()
        override fun removeFile(location: Location) = throw UnsupportedOperationException()
        override fun getFiles(location: Location) = throw UnsupportedOperationException()
        override fun getLastModifiedTime(location: Location) = throw UnsupportedOperationException()
        override fun getFileSize(location: Location) = throw UnsupportedOperationException()
        override fun exists(location: Location) = throw UnsupportedOperationException()
        override fun usage() = throw UnsupportedOperationException()
        override fun canHold(contentLength: Long) = throw UnsupportedOperationException()
    }

    private fun document(name: String, size: Long) = DocumentInfo(name, ContentType.APPLICATION_OCTET_STREAM, size)

    private fun listing(name: String, vararg entries: FileDetails): Result<out FileDetails, ErrorResponse> =
        Result.ok(DirectoryInfo(name, entries.toList()))

    private fun failure(message: String): Result<out FileDetails, ErrorResponse> =
        Result.error(ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message))

    private fun storage() =
        FileSystemStorageProviderFactory.of(InMemoryLogger(), root.toPath(), "100%")

    @Test
    fun `should list every file below the root with its path and size`() {
        // given: nested files in a repository root
        root.toPath().resolve("group/artifact/1.0").createDirectories()
        root.toPath().resolve("group/artifact/1.0/app-1.0.jar").writeBytes(ByteArray(5))
        root.toPath().resolve("group/artifact/1.0/app-1.0.pom").writeBytes(ByteArray(3))
        root.toPath().resolve("top.txt").writeBytes(ByteArray(2))

        // when: the tree is walked
        val files = storage().walkFiles().associateBy { it.path }

        // then: every file is listed once with its size, paths use forward slashes and are relative
        assertEquals(setOf("group/artifact/1.0/app-1.0.jar", "group/artifact/1.0/app-1.0.pom", "top.txt"), files.keys)
        assertEquals(5L, files.getValue("group/artifact/1.0/app-1.0.jar").sizeBytes)
        assertEquals(3L, files.getValue("group/artifact/1.0/app-1.0.pom").sizeBytes)
        assertEquals(2L, files.getValue("top.txt").sizeBytes)
    }

    @Test
    fun `should return no files for an empty repository`() {
        // given: an empty repository root
        // when: the tree is walked
        val files = storage().walkFiles()

        // then: nothing is listed
        assertEquals(emptyList<StoredFile>(), files)
    }

    @Test
    fun `should report a modification time for every listed file`() {
        // given: one file
        root.toPath().resolve("a.bin").writeBytes(ByteArray(1))

        // when: the tree is walked
        val file = storage().walkFiles().single()

        // then: the modification time is known
        assertEquals(true, file.lastModified != null)
    }

    @Test
    fun `should report the root and the error message when the root cannot be listed`() {
        // given: a storage whose root listing fails
        val storage = FakeStorage(mapOf("" to failure("bucket listing refused")))
        val unreadable = mutableListOf<Pair<String, String>>()

        // when: the tree is walked
        val files = storage.walkFiles { path, reason -> unreadable += path to reason }

        // then: nothing is listed and the callback names the root with the error message
        assertEquals(emptyList<StoredFile>(), files)
        assertEquals(listOf("" to "bucket listing refused"), unreadable)
    }

    @Test
    fun `should report the root and its type when the root is not a directory`() {
        // given: a storage whose root is a single document
        val storage = FakeStorage(mapOf("" to Result.ok(document("root", 1))))
        val unreadable = mutableListOf<Pair<String, String>>()

        // when: the tree is walked
        val files = storage.walkFiles { path, reason -> unreadable += path to reason }

        // then: nothing is listed and the callback names the root with the actual type
        assertEquals(emptyList<StoredFile>(), files)
        assertEquals(listOf("" to "not a directory: DocumentInfo"), unreadable)
    }

    @Test
    fun `should report a nested directory that cannot be listed and still measure the rest`() {
        // given: a root with a readable file, a readable directory and a directory whose listing fails
        val storage = FakeStorage(
            mapOf(
                "" to listing("", document("top.txt", 2), SimpleDirectoryInfo("com"), SimpleDirectoryInfo("org")),
                "com" to failure("access denied"),
                "org" to listing("org", document("a.bin", 7)),
            )
        )
        val unreadable = mutableListOf<Pair<String, String>>()

        // when: the tree is walked
        val files = storage.walkFiles { path, reason -> unreadable += path to reason }

        // then: the broken directory is reported by path and the other files are still measured
        assertEquals(listOf("com" to "access denied"), unreadable)
        assertEquals(mapOf("top.txt" to 2L, "org/a.bin" to 7L), files.associate { it.path to it.sizeBytes })
    }

}
