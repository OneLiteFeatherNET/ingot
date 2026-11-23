/*
 * Copyright (c) 2023 dzikoysk
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

@file:OptIn(ExperimentalIngotPluginApi::class)

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
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.storage.StorageEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import java.nio.file.Path
import java.util.Collections

/**
 * A whole-file download reads the entry in one call, so that a remote storage such as S3 answers it with one request. The reads are
 * recorded on the storage, and the test asserts them, never a duration.
 */
internal class FileReadsInOneRequestTest : MavenSpecification() {

    private val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun storageFacade(): StorageFacade = CountingStorageFacade(calls)

    override fun repositories() = listOf(RepositorySettings(id = REPOSITORY))

    @Test
    fun `should read a stored file with one readEntry call and no separate details or content call`() {
        // given: a stored artifact
        mavenFacade.getRepository(REPOSITORY)!!.storageProvider.putFile(JAR, "jar bytes".byteInputStream())
        calls.clear()

        // when: the file is found for a whole-file download
        val document = assertOk(mavenFacade.findFileInOneRead(LookupRequest(null, REPOSITORY, JAR))!!)

        // then: the content and the details come from the one read
        assertThat(document.content.use { it.readBytes().decodeToString() }).describedAs("content").isEqualTo("jar bytes")
        assertThat(document.document.contentLength).describedAs("size of the document").isEqualTo(9)
        assertThat(document.document.name).describedAs("name of the document").isEqualTo("lib-1.0.jar")
        assertThat(calls).describedAs("storage calls made for the download").containsExactly("readEntry $JAR")
    }

    @Test
    fun `should leave a missing file to the regular lookup`() {
        // when: a file that is not stored is found
        val outcome = mavenFacade.findFileInOneRead(LookupRequest(null, REPOSITORY, JAR))

        // then: the one read failed, and the caller falls back to the regular lookup
        assertThat(assertError(outcome!!).status).describedAs("status of the missing file").isEqualTo(404)
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
        override fun readEntry(location: Location): Result<StorageEntry, ErrorResponse> {
            calls += "readEntry $location"
            return delegate.readEntry(location)
        }

        override fun getFile(location: Location): Result<java.io.InputStream, ErrorResponse> {
            calls += "getFile $location"
            return delegate.getFile(location)
        }

        override fun getFileDetails(location: Location): Result<out com.reposilite.storage.api.FileDetails, ErrorResponse> {
            calls += "getFileDetails $location"
            return delegate.getFileDetails(location)
        }
    }

    private companion object {
        const val REPOSITORY = "releases"
        val JAR = "com/example/lib/1.0/lib-1.0.jar".toLocation()
    }

}
