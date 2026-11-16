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
import com.reposilite.storage.api.toLocation
import io.javalin.http.HttpStatus.BAD_REQUEST
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result

/** The removal rules of the provider (design D14): the root is refused, a prefix is removed child by child, and a retry completes it. */
internal class AzureBlobStorageProviderRemovalTest {

    private val client = FakeAzureBlobClient()
    private val provider = AzureBlobStorageProvider(
        failureFacade = FailureFacade(InMemoryLogger()),
        client = client,
        blobPrefix = "",
        blockSize = 8,
    )

    private fun storeFiles(vararg paths: String) {
        paths.forEach { path -> client.committed[path] = path.toByteArray() }
    }

    private fun assertOk(result: Result<*, *>) {
        assertThat(result.isOk).`as`("the result is ok").isTrue()
    }

    private fun assertError(result: Result<*, *>) {
        assertThat(result.isErr).`as`("the result is an error").isTrue()
        assertThat((result.error as ErrorResponse).status).`as`("status of the error").isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `should refuse to remove the repository root without any call to the service`() {
        // given: a file that a successful root removal would delete
        storeFiles("keep/me.jar")

        // when: a client asks to remove locations that collapse to the root
        listOf("/", ".").forEach { rootLike -> assertError(provider.removeFile(rootLike.toLocation())) }

        // then: the service was not asked to delete anything
        assertThat(client.deletes).`as`("delete requests").isEmpty()
        assertThat(client.committed).`as`("stored blobs").containsKey("keep/me.jar")
    }

    @Test
    fun `should delete a stored blob and keep its sibling`() {
        // given: two blobs in one directory
        storeFiles("test/test1.jar", "test/test2.jar")

        // when: one of them is removed
        assertOk(provider.removeFile("/test/test1.jar".toLocation()))

        // then: only the removed blob is gone
        assertThat(provider.exists("/test/test1.jar".toLocation())).`as`("removed blob exists").isFalse()
        assertThat(provider.exists("/test/test2.jar".toLocation())).`as`("sibling exists").isTrue()
    }

    @Test
    fun `should delete a directory with its files and its subdirectories`() {
        // given: a directory with a file, a subdirectory and a nested file, and a file outside of it
        storeFiles("directory/sub/sub/test.jar", "directory/sub/test.jar", "directory/test.jar")

        // when: the subdirectory is removed
        assertOk(provider.removeFile("/directory/sub".toLocation()))

        // then: everything under it is gone, and the file of the parent directory stays
        assertThat(client.committed.keys).`as`("remaining blobs").containsExactly("directory/test.jar")
    }

    @Test
    fun `should not delete a sibling directory that shares the name prefix`() {
        // given: a directory and a sibling whose name starts with the same characters
        storeFiles("com/example/foo/1.0.0/foo.jar", "com/example/foo-bar/1.0.0/foo-bar.jar")

        // when: the first directory is removed
        assertOk(provider.removeFile("/com/example/foo".toLocation()))

        // then: the sibling survives
        assertThat(client.committed.keys).`as`("remaining blobs").containsExactly("com/example/foo-bar/1.0.0/foo-bar.jar")
    }

    @Test
    fun `should report an interrupted directory removal and complete it with a repeated call`() {
        // given: a directory of three entries, where the second delete fails once
        storeFiles("x/a.jar", "x/b.jar", "x/sub/c.jar")
        client.failingDeletes += "x/b.jar"

        // when: the directory is removed, and then removed again
        val interrupted = provider.removeFile("/x".toLocation())

        // then: the failure is reported and the entries after it remain
        assertThat(interrupted.isErr).`as`("the interrupted removal is reported").isTrue()
        assertThat(client.committed.keys).`as`("blobs after the failure").contains("x/b.jar", "x/sub/c.jar")

        assertOk(provider.removeFile("/x".toLocation()))
        assertThat(client.committed.keys).`as`("blobs after the repeated call").isEmpty()
    }

    @Test
    fun `should treat the removal of a location that is already gone as done`() {
        // when: a location without any blob is removed
        val response = provider.removeFile("/missing/file.jar".toLocation())

        // then: the removal succeeds, as a repeated removal must
        assertOk(response)
    }

}
