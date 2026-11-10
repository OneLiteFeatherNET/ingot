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

package com.reposilite.storage.filesystem

import com.reposilite.journalist.Channel
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageEntryPage
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import com.reposilite.storage.walkFiles
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import panda.std.asSuccess
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/**
 * An upload is written beside its target under a reserved name, committed by one move, and hidden until it is complete.
 * The clock is fixed and every file that matters gets an explicit modification time, so no test depends on the system time.
 */
internal class FileSystemUploadAtomicityTest {

    @TempDir
    lateinit var workingDirectory: Path

    private val now: Instant = Instant.parse("2026-11-10T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val journalist = InMemoryLogger()

    private val root: Path by lazy { workingDirectory.resolve("storage").createDirectories() }

    private fun provider(): FileSystemStorageProvider =
        FileSystemStorageProviderFactory.of(journalist, root, "1MB", FailureFacade(journalist), clock)

    /** A file last modified [age] before the fixed clock, named like an upload in progress unless the test gives another name. */
    private fun fileModifiedAgo(directory: Path, name: String = ".ingot-1234.part", age: Duration = Duration.ofHours(1)): Path {
        val file = directory.createDirectories().resolve(name)
        file.writeBytes(ByteArray(1000) { 'p'.code.toByte() })
        Files.setLastModifiedTime(file, FileTime.from(now.minus(age)))
        return file
    }

    private fun partialNames(directory: Path): List<String> =
        Files.list(directory).use { entries -> entries.map { it.fileName.toString() }.filter { it.startsWith(".ingot-") }.toList() }

    private fun warnings(): List<String> =
        journalist.messages.filter { it.key == Channel.WARN }.map { it.value }

    /** Records what the provider looks like from outside while the body of an upload is still being read. */
    private inner class ObservingBody(
        private val payload: ByteArray,
        private val onSecondRead: () -> Unit,
    ) : InputStream() {
        private val delegate = payload.inputStream()
        private var reads = 0

        private fun observe() {
            reads++
            if (reads == 2) onSecondRead()
        }

        override fun read(): Int {
            observe()
            return delegate.read()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            observe()
            return delegate.read(buffer, offset, length)
        }
    }

    /** A body that delivers some bytes and then fails, as a client that disconnects part way does. */
    private class BrokenBody(private val prefix: ByteArray) : InputStream() {
        private var position = 0

        override fun read(): Int {
            if (position < prefix.size) return prefix[position++].toInt() and 0xff
            throw IOException("client disconnected")
        }
    }

    /** Commits through [moveIntoPlace] and records the atomic flag of every attempt. */
    private class MoveRecordingProvider(
        journalist: Journalist,
        rootDirectory: Path,
        clock: Clock,
        private val atomicMovesSupported: Boolean,
        val attempts: MutableList<Boolean>,
    ) : FileSystemStorageProvider(journalist, rootDirectory, FailureFacade(journalist), clock) {

        override fun canHold(contentLength: Long): Result<Long, ErrorResponse> =
            Long.MAX_VALUE.asSuccess()

        override fun moveIntoPlace(source: Path, target: Path, atomic: Boolean) {
            attempts.add(atomic)
            if (atomic && !atomicMovesSupported) {
                throw AtomicMoveNotSupportedException(source.toString(), target.toString(), "atomic moves are refused in this test")
            }
            super.moveIntoPlace(source, target, atomic)
        }
    }

    @Test
    fun `an upload in progress is written beside its target and the target is absent until the body is complete`() {
        // given: a repository directory and a body whose second read happens while the upload is running
        val target = "/releases/com/example/a/1.0/a-1.0.jar"
        val directory = root.resolve("releases/com/example/a/1.0")
        val observedTargetExists = mutableListOf<Boolean>()
        val observedPartials = mutableListOf<List<String>>()
        val body = ObservingBody(ByteArray(20_000) { 'a'.code.toByte() }) {
            observedTargetExists.add(Files.exists(directory.resolve("a-1.0.jar")))
            observedPartials.add(partialNames(directory))
        }

        // when: the upload is stored
        assertOk(provider().putFile(target.toLocation(), body))

        // then: while the body was streaming, the target did not exist and one partial file sat beside it
        assertThat(observedTargetExists).describedAs("observations of the target during the upload").containsExactly(false)
        assertThat(observedPartials.single())
            .describedAs("partial files in the target directory during the upload")
            .hasSize(1)
            .allMatch { it.startsWith(".ingot-") && it.endsWith(".part") }
    }

    @Test
    fun `an overwrite keeps the previous bytes readable while the new body is still being received`() {
        // given: an artifact that already exists with old content
        val location = "/releases/a/1.0/a-1.0.jar".toLocation()
        val storage = provider()
        assertOk(storage.putFile(location, "old".byteInputStream()))
        var observedDuringUpload = ""
        val body = ObservingBody(ByteArray(20_000) { 'n'.code.toByte() }) {
            observedDuringUpload = assertOk(storage.getFile(location)).use { String(it.readBytes()) }
        }

        // when: the artifact is overwritten
        assertOk(storage.putFile(location, body))

        // then: the reader observed the previous bytes, and the new bytes are stored afterwards
        assertThat(observedDuringUpload).describedAs("bytes read while the overwrite was streaming").isEqualTo("old")
        assertThat(assertOk(storage.getFile(location)).use { it.readBytes() }.size)
            .describedAs("size of the artifact after the overwrite")
            .isEqualTo(20_000)
    }

    @Test
    fun `a body that fails part way leaves the target unchanged and no partial file behind`() {
        // given: an existing artifact and an upload whose client disconnects after a few bytes
        val location = "/releases/a/1.0/a-1.0.jar".toLocation()
        val storage = provider()
        assertOk(storage.putFile(location, "old".byteInputStream()))

        // when: the broken upload is stored
        val thrown = runCatching { storage.putFile(location, BrokenBody("partial".toByteArray())) }.exceptionOrNull()

        // then: the failure surfaces, the artifact keeps its old bytes, and no partial file is left in the directory
        assertThat(thrown).describedAs("exception of the broken upload").isInstanceOf(IOException::class.java)
        assertThat(assertOk(storage.getFile(location)).use { it.readBytes() }.decodeToString())
            .describedAs("content of the artifact after the failed upload")
            .isEqualTo("old")
        assertThat(partialNames(root.resolve("releases/a/1.0")))
            .describedAs("partial files after the failed upload")
            .isEmpty()
    }

    @Test
    fun `the commit moves the temporary file with an atomic move`() {
        // given: a filesystem that supports atomic moves
        val attempts = mutableListOf<Boolean>()
        val storage = MoveRecordingProvider(journalist, root, clock, atomicMovesSupported = true, attempts = attempts)

        // when: an upload is stored
        assertOk(storage.putFile("/a/a-1.0.jar".toLocation(), "payload".byteInputStream()))

        // then: the single commit was an atomic move
        assertThat(attempts).describedAs("atomic flags of the commit moves").containsExactly(true)
    }

    @Test
    fun `a filesystem that refuses atomic moves gets one plain move and one warning`() {
        // given: a filesystem that refuses atomic moves
        val attempts = mutableListOf<Boolean>()
        val storage = MoveRecordingProvider(journalist, root, clock, atomicMovesSupported = false, attempts = attempts)

        // when: two uploads are stored
        assertOk(storage.putFile("/a/a-1.0.jar".toLocation(), "first".byteInputStream()))
        assertOk(storage.putFile("/a/a-1.1.jar".toLocation(), "second".byteInputStream()))

        // then: each commit tried the atomic move first and then the plain move, and only one warning was logged
        assertThat(attempts).describedAs("atomic flags of the commit moves").containsExactly(true, false, true, false)
        assertThat(warnings())
            .describedAs("warnings after two commits without atomic moves")
            .hasSize(1)
            .allMatch { it.contains("atomic") }
        assertThat(assertOk(storage.getFile("/a/a-1.1.jar".toLocation())).use { it.readBytes() }.decodeToString())
            .describedAs("content of the artifact committed by the plain move")
            .isEqualTo("second")
    }

    @Test
    fun `a directory listing omits a partial file`() {
        // given: a directory with a complete artifact and a partial file of an upload in progress
        val directory = root.resolve("releases/a/1.0").createDirectories()
        directory.resolve("a-1.0.jar").writeBytes(ByteArray(3))
        fileModifiedAgo(directory)

        // when: the directory is listed
        val listing = assertOk(provider().getFileDetails("/releases/a/1.0".toLocation())) as DirectoryInfo

        // then: only the complete artifact is listed
        assertThat(listing.files.map { it.name })
            .describedAs("names in the directory listing")
            .containsExactly("a-1.0.jar")
    }

    @Test
    fun `a search over the storage omits a partial file`() {
        // given: a complete artifact and a partial file in the same directory
        val directory = root.resolve("releases/a/1.0").createDirectories()
        directory.resolve("a-1.0.jar").writeBytes(ByteArray(3))
        fileModifiedAgo(directory)

        // when: the entries of the storage are listed
        val page: StorageEntryPage = assertOk(provider().listEntries(Location.empty(), after = null, limit = 100))

        // then: the partial file is not among the entries
        assertThat(page.entries.map { it.path.toString() })
            .describedAs("entries of the search")
            .containsExactly("releases/a/1.0/a-1.0.jar")
    }

    @Test
    fun `a walk of the storage for usage reports omits a partial file`() {
        // given: a complete artifact and a partial file in the same directory
        val directory = root.resolve("releases/a/1.0").createDirectories()
        directory.resolve("a-1.0.jar").writeBytes(ByteArray(3))
        fileModifiedAgo(directory)

        // when: every file of the storage is walked
        val files = provider().walkFiles()

        // then: only the complete artifact is reported
        assertThat(files.map { it.path })
            .describedAs("files reported by the walk")
            .containsExactly("releases/a/1.0/a-1.0.jar")
    }

    @Test
    fun `the storage usage does not count the bytes of a partial file`() {
        // given: a complete artifact of 3 bytes and a partial file of 1000 bytes
        val directory = root.resolve("releases/a/1.0").createDirectories()
        directory.resolve("a-1.0.jar").writeBytes(ByteArray(3))
        fileModifiedAgo(directory)

        // when: the usage is measured for the first time
        val usage = assertOk(provider().usage())

        // then: the partial bytes are not counted
        assertThat(usage).describedAs("usage in bytes without the partial file").isEqualTo(3L)
    }

    @Test
    fun `a direct read of a partial file answers not found`() {
        // given: a partial file in the repository
        fileModifiedAgo(root.resolve("releases/a/1.0").createDirectories())
        val location = "/releases/a/1.0/.ingot-1234.part".toLocation()

        // when: the partial file is requested
        val error = assertError(provider().getFile(location))

        // then: it answers not found, as for a file that does not exist
        assertThat(error.status).describedAs("status of a direct read of a partial file").isEqualTo(404)
    }

    @Test
    fun `an upload to a reserved partial name is refused with a bad request and writes nothing`() {
        // given: a repository directory
        val location = "/releases/a/1.0/.ingot-x.part".toLocation()

        // when: a client uploads to the reserved name
        val error = assertError(provider().putFile(location, "payload".byteInputStream()))

        // then: the upload is refused and no file is written under that name
        assertThat(error.status).describedAs("status of an upload to a reserved name").isEqualTo(400)
        assertThat(Files.exists(root.resolve("releases/a/1.0/.ingot-x.part")))
            .describedAs("existence of the file after the refused upload")
            .isFalse
    }

    @Test
    fun `the start sweep deletes a partial file last modified more than 24 hours ago`() {
        // given: a partial file last modified 25 hours before the clock
        val file = fileModifiedAgo(root.resolve("releases/a/1.0").createDirectories(), age = Duration.ofHours(25))

        // when: the provider starts
        provider()

        // then: the stale partial file is gone
        assertThat(Files.exists(file)).describedAs("existence of a partial file older than 24 hours").isFalse
    }

    @Test
    fun `the start sweep keeps a partial file that was modified within the last 24 hours`() {
        // given: a partial file last modified one hour before the clock, as an upload on another replica would be
        val file = fileModifiedAgo(root.resolve("releases/a/1.0").createDirectories(), age = Duration.ofHours(1))

        // when: the provider starts
        provider()

        // then: the recent partial file stays
        assertThat(Files.exists(file)).describedAs("existence of a partial file modified one hour ago").isTrue
    }

    @Test
    fun `the start sweep keeps files whose names are not reserved even when they are older than 24 hours`() {
        // given: an old file that ends in .part without the reserved prefix, and an old file with the prefix but no .part suffix
        val directory = root.resolve("releases/a/1.0").createDirectories()
        val suffixOnly = fileModifiedAgo(directory, name = "a-1.0.jar.part", age = Duration.ofHours(25))
        val prefixOnly = fileModifiedAgo(directory, name = ".ingot-x.jar", age = Duration.ofHours(25))

        // when: the provider starts
        provider()

        // then: neither file is deleted
        assertThat(Files.exists(suffixOnly)).describedAs("existence of a file without the reserved prefix").isTrue
        assertThat(Files.exists(prefixOnly)).describedAs("existence of a file without the reserved suffix").isTrue
    }

}
