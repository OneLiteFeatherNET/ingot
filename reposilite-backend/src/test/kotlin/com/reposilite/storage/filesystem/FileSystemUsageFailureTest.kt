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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.ResultAssertions.assertError
import java.nio.file.Path
import kotlin.io.path.createDirectories

internal class FileSystemUsageFailureTest {

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `usage should not expose the file system path when the storage directory cannot be measured`() {
        // given: a file system storage whose root directory disappears after the provider is created
        val root = workingDirectory.resolve("storage").createDirectories()
        val provider = FileSystemStorageProviderFactory.of(InMemoryLogger(), root, "100%")
        root.toFile().deleteRecursively()

        // when: the storage usage is measured
        val error = assertError(provider.usage())

        // then: the status is still a server error
        assertThat(error.status).describedAs("status of a failed measurement").isEqualTo(500)

        // and: neither the file system path nor the exception class reach the response
        assertThat(error.message)
            .describedAs("message of a failed measurement")
            .doesNotContain(root.toString(), "NoSuchFileException")
    }

}
