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

package com.reposilite.maven

import com.reposilite.journalist.Journalist
import com.reposilite.maven.api.LookupRequest
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.shared.ErrorResponse
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageFacade
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.StorageProviderSettings
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import java.io.InputStream
import java.nio.file.Path
import java.util.Collections

/**
 * The entity tag of a cached artifact is read from its stored checksum. Every storage call is a network round trip on S3, and a
 * probe for a missing key there costs a HEAD and a listing, so a checksum is read directly: one call per checksum file.
 */
internal class EntityTagStorageReadsTest : MavenSpecification() {

    private val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun storageFacade(): StorageFacade = CountingStorageFacade(calls)

    override fun repositories() = listOf(RepositorySettings(id = REPOSITORY))

    @Test
    fun `should read each checksum sibling with one storage call and never probe or list`() {
        // given: an artifact with a stored sha1 checksum and no sha256
        val details = store(JAR, "jar bytes")
        store(JAR.resolveSibling("lib-1.0.jar.sha1"), SHA1)
        calls.clear()

        // when: the entity tag is resolved
        val etag = mavenFacade.findEntityTag(LookupRequest(null, REPOSITORY, JAR), details)

        // then: the sha1 is the tag, read after one call for the missing sha256 and one for the sha1
        assertThat(etag).describedAs("entity tag of the artifact").isEqualTo("\"$SHA1\"")
        assertThat(calls).describedAs("storage calls made for the entity tag").containsExactly(
            "getFile com/example/lib/1.0/lib-1.0.jar.sha256",
            "getFile com/example/lib/1.0/lib-1.0.jar.sha1",
        )
    }

    @Test
    fun `should fall back to the size and time tag after one storage call per checksum when none is stored`() {
        // given: an artifact without any stored checksum
        val details = store(JAR, "jar bytes")
        calls.clear()

        // when: the entity tag is resolved
        val etag = mavenFacade.findEntityTag(LookupRequest(null, REPOSITORY, JAR), details)

        // then: the size and time tag, after exactly one read per checksum kind
        assertThat(etag).describedAs("entity tag without a checksum")
            .isEqualTo("\"${details.contentLength.toString(16)}-${details.lastModifiedTime!!.toEpochMilli().toString(16)}\"")
        assertThat(calls).describedAs("storage calls made for the entity tag").containsExactly(
            "getFile com/example/lib/1.0/lib-1.0.jar.sha256",
            "getFile com/example/lib/1.0/lib-1.0.jar.sha1",
            "getFile com/example/lib/1.0/lib-1.0.jar.md5",
        )
    }

    private fun store(location: Location, content: String): DocumentInfo {
        val storage = mavenFacade.getRepository(REPOSITORY)!!.storageProvider
        storage.putFile(location, content.byteInputStream())
        return storage.getFileDetails(location).get() as DocumentInfo
    }

    /** Wraps the file system storage of every repository and records the reads made through it. */
    private class CountingStorageFacade(private val calls: MutableList<String>) : StorageFacade() {
        override fun createStorageProvider(
            journalist: Journalist,
            failureFacade: FailureFacade,
            workingDirectory: Path,
            repository: String,
            storageSettings: StorageProviderSettings,
        ): StorageProvider? =
            super.createStorageProvider(journalist, failureFacade, workingDirectory, repository, storageSettings)
                ?.let { CountingStorage(it, calls) }
    }

    private class CountingStorage(private val delegate: StorageProvider, private val calls: MutableList<String>) : StorageProvider by delegate {
        override fun exists(location: Location): Boolean {
            calls += "exists $location"
            return delegate.exists(location)
        }

        override fun getFile(location: Location): Result<InputStream, ErrorResponse> {
            calls += "getFile $location"
            return delegate.getFile(location)
        }

        override fun getFiles(location: Location): Result<List<Location>, ErrorResponse> {
            calls += "getFiles $location"
            return delegate.getFiles(location)
        }

        override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> {
            calls += "getFileDetails $location"
            return delegate.getFileDetails(location)
        }
    }

    private companion object {
        const val REPOSITORY = "releases"
        const val SHA1 = "0123456789abcdef0123456789abcdef01234567"
        val JAR = "com/example/lib/1.0/lib-1.0.jar".toLocation()
    }

}
