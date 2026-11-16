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
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.FileType
import com.reposilite.storage.api.SimpleDirectoryInfo
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.attribute.FileTime

/** The details rules of the provider (design D5): object metadata, directories from prefixes, and the repository root. */
internal class GcsStorageProviderDetailsTest {

    private val client = FakeGcsObjectClient().apply {
        objects["a/data.bin"] = ByteArray(100)
        objects["a/dir/one.jar"] = ByteArray(3)
        objects["a/dir/two.jar"] = ByteArray(4)
        objects["a/dir/sub/three.jar"] = ByteArray(5)
    }

    private val provider = GcsStorageProvider(
        failureFacade = FailureFacade(InMemoryLogger()),
        client = client,
        objectPrefix = "",
        chunkSize = 4,
    )

    private fun directoryNames(details: FileDetails): List<String> =
        (details as DirectoryInfo).files.map { it.name }

    @Test
    fun `should report an object with its size and update time`() {
        // when: the details of an object are read
        val details = provider.getFileDetails("/a/data.bin".toLocation()).get()

        // then: the document carries the size and the update time of the service
        assertThat(details).`as`("details").isInstanceOf(DocumentInfo::class.java)
        assertThat((details as DocumentInfo).contentLength).`as`("size").isEqualTo(100)
        assertThat(details.lastModifiedTime).`as`("update time").isEqualTo(FakeGcsObjectClient.modifiedAt)
    }

    @Test
    fun `should report a location with objects below it as a directory with its files`() {
        // when: a location without an object of its own is read
        val details = provider.getFileDetails("/a/dir".toLocation()).get()

        // then: it is a directory whose entries are the objects and the deeper directories directly below it
        assertThat(details.type).`as`("type").isEqualTo(FileType.DIRECTORY)
        assertThat(directoryNames(details)).`as`("entries in name order").containsExactly("one.jar", "sub", "two.jar")
        assertThat((details as DirectoryInfo).files[1]).`as`("deeper directory").isInstanceOf(SimpleDirectoryInfo::class.java)
    }

    @Test
    fun `should answer a location without objects below it with not found`() {
        // when: a location that holds nothing is read
        val result = provider.getFileDetails("/a/none".toLocation())

        // then: the answer is not found, and the location is not reported as an empty directory
        assertThat(result.isErr).`as`("the read fails").isTrue()
        assertThat(result.error.status).`as`("status").isEqualTo(404)
    }

    @Test
    fun `should report the repository root as an empty directory in an empty bucket`() {
        // when: the root of an empty bucket is read
        val details = GcsStorageProvider(
            failureFacade = FailureFacade(InMemoryLogger()),
            client = FakeGcsObjectClient(),
            objectPrefix = "",
            chunkSize = 4,
        ).getFileDetails("".toLocation()).get()

        // then: the root is a directory with no entries, and not a file
        assertThat(details.type).`as`("type").isEqualTo(FileType.DIRECTORY)
        assertThat(directoryNames(details)).`as`("entries").isEmpty()
    }

    @Test
    fun `should report the size of an object`() {
        // then: the size comes from the object metadata
        assertThat(provider.getFileSize("/a/data.bin".toLocation()).get()).`as`("size").isEqualTo(100L)
    }

    @Test
    fun `should report the update time of an object as its last modification`() {
        // then: the time comes from the object metadata
        assertThat(provider.getLastModifiedTime("/a/data.bin".toLocation()).get())
            .`as`("last modification")
            .isEqualTo(FileTime.from(FakeGcsObjectClient.modifiedAt))
    }

    @Test
    fun `should report a directory prefix with objects below it as existing`() {
        // then: an existing object and a directory with objects below it both exist, a location with nothing does not
        assertThat(provider.exists("/a/data.bin".toLocation())).`as`("object").isTrue()
        assertThat(provider.exists("/a/dir".toLocation())).`as`("directory").isTrue()
        assertThat(provider.exists("/a/none".toLocation())).`as`("missing").isFalse()
    }

}
