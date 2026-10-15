/*
 * Copyright (c) 2023 dzikoysk
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
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

internal class RemoveFileTest {

    @TempDir
    lateinit var root: File

    private fun storage() =
        FileSystemStorageProviderFactory.of(InMemoryLogger(), root.toPath(), "100%")

    @Test
    fun `removing a file that is not there succeeds, as it does upstream`() {
        // given: a repository without the file
        root.toPath().resolve("group/artifact/1.0").createDirectories()

        // when: the file is removed
        val result = storage().removeFile(Location.of("group/artifact/1.0/missing.jar"))

        // then: the removal is idempotent instead of failing with an internal error
        assertThat(result.isOk).describedAs("removing a file that is not there").isTrue()
    }

    @Test
    fun `removing a file twice succeeds the second time`() {
        // given: one file that is removed once
        root.toPath().resolve("group").createDirectories()
        root.toPath().resolve("group/app.jar").writeBytes(ByteArray(1))
        storage().removeFile(Location.of("group/app.jar"))

        // when: the same file is removed again
        val result = storage().removeFile(Location.of("group/app.jar"))

        // then: the second call is a no-op and the file stays gone
        assertThat(result.isOk).describedAs("removing the same file again").isTrue()
        assertThat(root.toPath().resolve("group/app.jar").exists()).isFalse()
    }
}
