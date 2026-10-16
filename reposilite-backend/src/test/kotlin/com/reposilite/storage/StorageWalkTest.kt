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
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

internal class StorageWalkTest {

    @TempDir
    lateinit var root: File

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

}
