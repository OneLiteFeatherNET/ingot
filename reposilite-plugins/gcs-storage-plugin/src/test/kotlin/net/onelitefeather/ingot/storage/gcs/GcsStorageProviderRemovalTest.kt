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
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The removal rules of the provider (design D7): the root is refused, a file and a directory are deleted object by object. */
internal class GcsStorageProviderRemovalTest {

    private val client = FakeGcsObjectClient().apply {
        objects["a/data.bin"] = ByteArray(1)
        objects["a/dir/one.jar"] = ByteArray(1)
        objects["a/dir/two.jar"] = ByteArray(1)
        objects["a/dir/sub/three.jar"] = ByteArray(1)
        objects["a/keep.bin"] = ByteArray(1)
    }

    private val provider = GcsStorageProvider(
        failureFacade = FailureFacade(InMemoryLogger()),
        client = client,
        objectPrefix = "",
        chunkSize = 4,
    )

    @Test
    fun `should refuse the repository root before any request`() {
        // when: the root is removed
        val result = provider.removeFile("".toLocation())

        // then: a bad request is answered, and no object was deleted
        assertThat(result.isErr).`as`("the removal fails").isTrue()
        assertThat(result.error.status).`as`("status").isEqualTo(400)
        assertThat(client.deletes).`as`("delete requests").isEmpty()
        assertThat(client.objects).`as`("objects").hasSize(5)
    }

    @Test
    fun `should delete a file object by object`() {
        // when: a file is removed
        val result = provider.removeFile("/a/data.bin".toLocation())

        // then: the file is gone and nothing else is
        assertThat(result.isOk).`as`("the removal succeeds").isTrue()
        assertThat(client.objects.keys).`as`("objects").doesNotContain("a/data.bin").contains("a/keep.bin")
    }

    @Test
    fun `should delete a directory with everything below it, including nested directories`() {
        // when: a directory is removed
        val result = provider.removeFile("/a/dir".toLocation())

        // then: every object below it is gone, and the objects beside it stay
        assertThat(result.isOk).`as`("the removal succeeds").isTrue()
        assertThat(client.objects.keys).`as`("objects").containsExactlyInAnyOrder("a/data.bin", "a/keep.bin")
    }

    @Test
    fun `should succeed when nothing is stored at the location`() {
        // when: a location without objects is removed
        val result = provider.removeFile("/a/none".toLocation())

        // then: the removal succeeds, as nothing is left at the location
        assertThat(result.isOk).`as`("the removal succeeds").isTrue()
    }

    @Test
    fun `should list the objects of a directory to remove in pages of at most a thousand`() {
        // when: a directory is removed
        provider.removeFile("/a/dir".toLocation())

        // then: every listing asks for at most a thousand objects
        assertThat(client.listings).`as`("listings").isNotEmpty()
        assertThat(client.listings.map { it.third }).`as`("page sizes").allMatch { it <= 1000 }
    }

    @Test
    fun `should report a failed delete and finish the removal on a later call`() {
        // given: the delete of one object of the directory fails once
        client.failingDeletes += "a/dir/two.jar"

        // when: the directory is removed, and then removed again
        val first = provider.removeFile("/a/dir".toLocation())
        val second = provider.removeFile("/a/dir".toLocation())

        // then: the first call reports the failure, and the second call removes what was left
        assertThat(first.isErr).`as`("the first removal fails").isTrue()
        assertThat(second.isOk).`as`("the second removal succeeds").isTrue()
        assertThat(client.objects.keys).`as`("objects").containsExactlyInAnyOrder("a/data.bin", "a/keep.bin")
    }

}
