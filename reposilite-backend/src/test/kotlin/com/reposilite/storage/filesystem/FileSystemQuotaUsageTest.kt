/*
 * Copyright (c) 2023 dzikoysk
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

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.storage.api.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

internal class FileSystemQuotaUsageTest {

    @TempDir
    lateinit var root: File

    private fun storage(maxSize: Long) =
        FileSystemStorageProviderFactory.of(InMemoryLogger(), root.toPath(), maxSize)

    @Test
    fun `should report zero usage for an empty repository`() {
        // given: a repository with a fixed quota and no files
        val storage = storage(maxSize = 10)

        // when: the usage is measured
        val usage = storage.usage()

        // then: nothing is used
        assertEquals(0L, usage.get(), "an empty repository should use no bytes")
    }

    @Test
    fun `should count the bytes of files that are already stored`() {
        // given: files stored below nested directories before the storage is opened
        root.toPath().resolve("group/artifact").createDirectories()
        root.toPath().resolve("group/artifact/app.jar").writeBytes(ByteArray(5))
        root.toPath().resolve("top.txt").writeBytes(ByteArray(3))

        // when: the usage is measured
        val usage = storage(maxSize = 100).usage()

        // then: the usage is the sum of all stored files
        assertEquals(8L, usage.get(), "usage should sum every stored file below the root")
    }

    @Test
    fun `should reject a file that does not fit beside the stored files`() {
        // given: a quota of 10 bytes with 8 bytes already stored
        root.toPath().resolve("stored.bin").writeBytes(ByteArray(8))
        val storage = storage(maxSize = 10)

        // when: a 5 byte file is checked against the quota
        val result = storage.canHold(5)

        // then: the file is rejected because only 2 bytes are left
        assertTrue(result.isErr, "a 5 byte file should not fit beside 8 stored bytes in a 10 byte quota")
    }

    @Test
    fun `should accept a file that fits beside the stored files`() {
        // given: a quota of 10 bytes with 8 bytes already stored
        root.toPath().resolve("stored.bin").writeBytes(ByteArray(8))
        val storage = storage(maxSize = 10)

        // when: a 2 byte file is checked against the quota
        val result = storage.canHold(2)

        // then: the file fits exactly into the remaining space
        assertTrue(result.isOk, "a 2 byte file should fit into the 2 bytes left of a 10 byte quota")
    }

    @Test
    fun `should reject an upload once the stored files fill the quota`() {
        // given: a quota of 10 bytes and an 8 byte file already uploaded
        val storage = storage(maxSize = 10)
        assertTrue(storage.putFile(Location.of("first.bin"), ByteArray(8).inputStream()).isOk, "the first 8 byte upload should be accepted")

        // when: a second 5 byte upload is attempted
        val result = storage.putFile(Location.of("second.bin"), ByteArray(5).inputStream())

        // then: the upload is rejected and nothing is written
        assertTrue(result.isErr, "a 5 byte upload should be rejected when only 2 bytes of a 10 byte quota are left")
        assertTrue(!root.toPath().resolve("second.bin").exists(), "a rejected upload should not be written to the repository")
    }

    @Test
    fun `should free quota when a stored file is removed`() {
        // given: a quota of 10 bytes filled by an 8 byte file
        val storage = storage(maxSize = 10)
        assertTrue(storage.putFile(Location.of("first.bin"), ByteArray(8).inputStream()).isOk, "the first 8 byte upload should be accepted")
        assertTrue(storage.removeFile(Location.of("first.bin")).isOk, "the stored file should be removed")

        // when: the usage is measured again
        val usage = storage.usage()

        // then: the removed bytes are no longer counted
        assertEquals(0L, usage.get(), "usage should drop back to zero once the only file is removed")
    }

    @Test
    fun `should free the bytes of every file in a removed directory`() {
        // given: a directory holding 8 bytes that has already been measured
        root.toPath().resolve("group/artifact").createDirectories()
        root.toPath().resolve("group/artifact/app.jar").writeBytes(ByteArray(5))
        root.toPath().resolve("group/artifact/app.pom").writeBytes(ByteArray(3))
        val storage = storage(maxSize = 100)
        assertEquals(8L, storage.usage().get(), "the 8 bytes should be measured before the removal")

        // when: the directory is removed
        assertTrue(storage.removeFile(Location.of("group")).isOk, "the directory should be removed")

        // then: all of its bytes are no longer counted
        assertEquals(0L, storage.usage().get(), "removing a directory should free the bytes of all files below it")
    }

    @Test
    fun `should count a replaced file only once`() {
        // given: a quota of 100 bytes with a 60 byte file that is then replaced by a 30 byte file
        val storage = storage(maxSize = 100)
        assertTrue(storage.putFile(Location.of("replaced.bin"), ByteArray(60).inputStream()).isOk, "the first upload should be accepted")
        assertTrue(storage.putFile(Location.of("replaced.bin"), ByteArray(30).inputStream()).isOk, "the replacement should be accepted")

        // when: the usage is measured
        val usage = storage.usage()

        // then: only the replacing file is counted
        assertEquals(30L, usage.get(), "a replaced file should not be counted next to its replacement")
    }

}
