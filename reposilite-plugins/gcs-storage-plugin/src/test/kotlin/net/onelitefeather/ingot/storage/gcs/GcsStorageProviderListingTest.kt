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
import com.reposilite.storage.StorageEntrySource
import com.reposilite.storage.StorageEntryPage
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The listing rules of the provider (design D6): directory entries with the delimiter, and a native flat listing with a path cursor. */
internal class GcsStorageProviderListingTest {

    private val client = FakeGcsObjectClient().apply {
        objects["a/dir/one.jar"] = ByteArray(3)
        objects["a/dir/two.jar"] = ByteArray(4)
        objects["a/dir/sub/three.jar"] = ByteArray(5)
        for (index in 1..5) {
            objects["a/p/$index.jar"] = ByteArray(index)
        }
        objects["b/other.jar"] = ByteArray(1)
    }

    private val provider = GcsStorageProvider(
        failureFacade = FailureFacade(InMemoryLogger()),
        client = client,
        objectPrefix = "",
        chunkSize = 4,
    )

    private fun pageOf(after: String?, limit: Int): StorageEntryPage =
        (provider as StorageEntrySource).listEntries("a/p".toLocation(), after, limit).get()

    @Test
    fun `should list the entries of a directory as files and directories`() {
        // when: a directory with files and a deeper directory is listed
        val entries = provider.getFiles("/a/dir".toLocation()).get()

        // then: the entries are the files and the deeper directory, in name order
        assertThat(entries.map(Location::toString)).`as`("entries").containsExactly("a/dir/one.jar", "a/dir/sub", "a/dir/two.jar")
    }

    @Test
    fun `should answer an empty directory with not found`() {
        // when: a location without any object below it is listed
        val result = provider.getFiles("/a/none".toLocation())

        // then: the answer is not found
        assertThat(result.isErr).`as`("the listing fails").isTrue()
        assertThat(result.error.status).`as`("status").isEqualTo(404)
    }

    @Test
    fun `should list the objects of a directory only once across pages`() {
        // when: the flat listing is read in pages of two
        val first = pageOf(after = null, limit = 2)
        val second = pageOf(after = first.next, limit = 2)
        val third = pageOf(after = second.next, limit = 2)

        // then: the pages hold the objects in name order, each once, and the last page has no cursor
        val names = (first.entries + second.entries + third.entries).map { it.path.toString() }
        assertThat(names).`as`("names in order").containsExactly("a/p/1.jar", "a/p/2.jar", "a/p/3.jar", "a/p/4.jar", "a/p/5.jar")
        assertThat(third.next).`as`("cursor of the last page").isNull()
    }

    @Test
    fun `should give a full page the path of its last entry as the cursor`() {
        // when: the first page of two entries is read
        val page = pageOf(after = null, limit = 2)

        // then: the cursor is the root-relative path of the last entry
        assertThat(page.next).`as`("cursor").isEqualTo("a/p/2.jar")
    }

    @Test
    fun `should not return the entry that equals the cursor`() {
        // when: the listing continues after the path of an existing object
        val page = pageOf(after = "a/p/2.jar", limit = 10)

        // then: the object of the cursor is not part of the page
        assertThat(page.entries.map { it.path.toString() }).`as`("entries after the cursor").containsExactly("a/p/3.jar", "a/p/4.jar", "a/p/5.jar")
    }

    @Test
    fun `should start the service listing at the cursor with the prefix of the repository`() {
        // when: a page is read after a cursor
        pageOf(after = "a/p/2.jar", limit = 10)

        // then: the service starts its listing at the cursor, below the directory and without a delimiter
        assertThat(client.listings.last().first).`as`("prefix").isEqualTo("a/p/")
        assertThat(client.listings.last().second).`as`("start offset").isEqualTo("a/p/2.jar")
    }

    @Test
    fun `should list only the objects below the requested location`() {
        // when: the flat listing of a directory is read
        val page = pageOf(after = null, limit = 100)

        // then: no object of another directory is part of it
        assertThat(page.entries.map { it.path.toString() }).`as`("entries").noneMatch { it.startsWith("b/") }
    }

    @Test
    fun `should offer the provider itself as the native entry source`() {
        // then: the provider lists natively, so the walking fallback of the core is not used
        assertThat(provider).`as`("entry source").isInstanceOf(StorageEntrySource::class.java)
    }

}
