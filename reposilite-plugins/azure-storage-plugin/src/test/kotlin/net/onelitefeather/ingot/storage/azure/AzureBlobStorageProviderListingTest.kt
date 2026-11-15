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

package net.onelitefeather.ingot.storage.azure

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.status.FailureFacade
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import io.javalin.http.HttpStatus.NOT_FOUND
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result

/** The listing rules of the provider (design D6): a prefix is a directory, it is probed with one result, and folder markers are skipped. */
internal class AzureBlobStorageProviderListingTest {

    private val client = FakeAzureBlobClient()

    private fun provider(blobPrefix: String = "") = AzureBlobStorageProvider(
        failureFacade = FailureFacade(InMemoryLogger()),
        client = client,
        blobPrefix = blobPrefix,
        blockSize = 8,
    )

    private fun <T> assertOk(result: Result<T, *>): T {
        assertThat(result.isOk).`as`("the result is ok").isTrue()
        return result.get()
    }

    private fun assertFailsWithStatus(result: Result<*, *>, status: Int) {
        assertThat(result.isErr).`as`("the result is an error").isTrue()
        assertThat((result.error as ErrorResponse).status).`as`("status of the error").isEqualTo(status)
    }

    private fun storeFiles(vararg paths: String) {
        paths.forEach { path -> client.committed[path] = path.toByteArray() }
    }

    @Test
    fun `should list a directory with its files and its subdirectories`() {
        // given: files in a directory, one of them in a subdirectory, and a sibling directory
        storeFiles("a/a.jar", "a/b.jar", "a/c/a.jar", "b/a.jar")

        // when: the directory is requested
        val listing = assertOk(provider().getFileDetails("/a".toLocation())) as DirectoryInfo

        // then: the directory holds its files and its subdirectory, and no entry of the sibling
        assertThat(listing.files.map { it.name }).`as`("entries").containsExactlyInAnyOrder("c", "a.jar", "b.jar")
    }

    @Test
    fun `should probe a location without a blob with one result before it lists the directory`() {
        // given: a directory without a blob of its own
        storeFiles("a/a.jar", "a/b.jar")

        // when: the directory is requested
        assertOk(provider().getFileDetails("/a".toLocation()))

        // then: the first request asks for one result only
        assertThat(client.listCalls.first().second).`as`("maximum results of the probe").isEqualTo(1)
    }

    @Test
    fun `should answer an empty location as not found`() {
        // given: a repository without anything under the location
        storeFiles("a/a.jar")

        // when: the location is listed and inspected
        val listing = provider().getFiles("/nothing".toLocation())
        val details = provider().getFileDetails("/nothing".toLocation())

        // then: both answers are not found, and the location does not exist
        assertFailsWithStatus(listing, NOT_FOUND.code)
        assertFailsWithStatus(details, NOT_FOUND.code)
        assertThat(provider().exists("/nothing".toLocation())).`as`("exists").isFalse()
    }

    @Test
    fun `should skip the folder marker of a directory when the directory is listed`() {
        // given: a folder marker that a console wrote for the directory, and nothing else in it
        storeFiles("a/marker/")

        // when: the directory is listed
        val listing = provider().getFiles("/a/marker".toLocation())

        // then: the marker is not an entry, so the directory is empty and not found
        assertFailsWithStatus(listing, NOT_FOUND.code)
    }

    @Test
    fun `should list the repository root as a directory`() {
        // given: two entries at the top of the repository
        storeFiles("a/a.jar", "root.jar")

        // when: the root is requested
        val listing = assertOk(provider().getFileDetails("/".toLocation())) as DirectoryInfo

        // then: both top level entries are listed
        assertThat(listing.files.map { it.name }).`as`("entries").containsExactlyInAnyOrder("a", "root.jar")
    }

    @Test
    fun `should list only the entries under the blob prefix of the repository`() {
        // given: two repositories in one container, with the prefixes repo1/ and repo2/
        storeFiles("repo1/a.jar", "repo2/b.jar")

        // when: the root of the first repository is listed
        val names = assertOk(provider(blobPrefix = "repo1/").getFiles("/".toLocation())).map(Location::toString)

        // then: the entry of the second repository is not visible
        assertThat(names).`as`("locations").containsExactly("a.jar")
    }

    @Test
    fun `should list the files of a directory as locations relative to the repository`() {
        // given: files and a subdirectory under a directory
        storeFiles("a/a.jar", "a/b.jar", "a/c/a.jar")

        // when: the files of the directory are listed
        val locations = assertOk(provider().getFiles("/a".toLocation())).map(Location::toString)

        // then: every entry is a location relative to the repository, without a trailing slash
        assertThat(locations).`as`("locations").containsExactlyInAnyOrder("a/a.jar", "a/b.jar", "a/c")
    }

    @Test
    fun `should report a directory as existing and a missing location as not existing`() {
        // given: a file in a directory
        storeFiles("a/a.jar")

        // then: the directory exists, and a location without any entry does not
        assertThat(provider().exists("/a".toLocation())).`as`("exists for the directory").isTrue()
        assertThat(provider().exists("/b".toLocation())).`as`("exists for the missing location").isFalse()
    }

}
