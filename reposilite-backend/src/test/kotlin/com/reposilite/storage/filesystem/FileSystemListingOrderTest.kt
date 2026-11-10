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

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.createDirectories

/** A listing of version directories follows the natural order of the versions, in a temporary repository root of its own. */
internal class FileSystemListingOrderTest {

    @TempDir
    lateinit var root: File

    @Test
    fun `a listing orders version directories by their natural order`() {
        // given: version directories whose plain text order differs from their natural order
        listOf("1.10", "1.2", "1.9").forEach { root.toPath().resolve("group/artifact/$it").createDirectories() }
        val provider = FileSystemStorageProviderFactory.of(InMemoryLogger(), root.toPath(), "100%")

        // when: the directory is listed with its details, which is the listing the browser and the index see
        val listing = provider.getFileDetails("group/artifact".toLocation()).get() as DirectoryInfo

        // then: the versions come in natural order, 1.2 before 1.9 before 1.10
        assertThat(listing.files.map { it.name })
            .describedAs("the listing must follow the natural order of the versions")
            .containsExactly("1.2", "1.9", "1.10")
    }

}
