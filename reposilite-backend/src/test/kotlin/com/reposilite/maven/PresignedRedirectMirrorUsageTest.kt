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
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.api.LookupRequest
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageFacade
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.StorageProviderSettings
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * A redirect that serves a mirror copy is a served copy, so the mirror usage record must move forward as it does for a download
 * from Ingot. Without it, a popular copy would look unused and be evicted.
 */
internal class PresignedRedirectMirrorUsageTest : MavenSpecification() {

    override fun clock(): Clock = Clock.fixed(NOW, ZoneOffset.UTC)

    override fun presignedRedirectTtl(): Duration = Duration.ofSeconds(300)

    override fun storageFacade(): StorageFacade = PresigningStorageFacade()

    override fun repositories() = listOf(
        RepositorySettings(
            id = MIRROR,
            proxied = mutableListOf(MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = true)),
        ),
    )

    @Test
    fun `should move the last access of a mirror directory forward when a redirect serves its copy`() {
        // given: a stored mirror copy whose directory was last served two days ago
        val gav = "com/example/lib/1.0/lib-1.0.jar".toLocation()
        mirrorCacheLedger.insertIfAbsent(MIRROR, "com/example/lib/1.0", NOW.minus(Duration.ofDays(2)))
        mavenFacade.getRepository(MIRROR)!!.storageProvider.putFile(gav, "bytes".byteInputStream())

        // when: the copy is downloaded with a redirect
        val redirect = mavenFacade.presignedRedirectOf(lookup(gav), "GET", hasRangeOrConditionalHeaders = false)

        // then: the redirect is made and the usage record of the directory is the moment of the download
        assertThat(redirect).describedAs("redirect of the stored mirror copy").isNotNull()
        assertThat(mirrorCacheLedger.find(MIRROR)["com/example/lib/1.0"])
            .describedAs("last access of the mirror directory after the redirect")
            .isEqualTo(NOW)
    }

    @Test
    fun `should leave the last access alone when the answer is not a redirect`() {
        // given: a stored mirror copy whose directory was last served two days ago
        val gav = "com/example/lib/2.0/lib-2.0.jar".toLocation()
        val lastServed = NOW.minus(Duration.ofDays(2))
        mirrorCacheLedger.insertIfAbsent(MIRROR, "com/example/lib/2.0", lastServed)
        mavenFacade.getRepository(MIRROR)!!.storageProvider.putFile(gav, "bytes".byteInputStream())

        // when: a HEAD request, which is never redirected
        val redirect = mavenFacade.presignedRedirectOf(lookup(gav), "HEAD", hasRangeOrConditionalHeaders = false)

        // then: no redirect, and the record is unchanged
        assertThat(redirect).describedAs("redirect of a HEAD request").isNull()
        assertThat(mirrorCacheLedger.find(MIRROR)["com/example/lib/2.0"])
            .describedAs("last access of the mirror directory after a HEAD request")
            .isEqualTo(lastServed)
    }

    private fun lookup(gav: Location) = LookupRequest(accessToken = null, repository = MIRROR, gav = gav)

    /** Wraps the file system storage of every repository, and presigns any stored file. Presigning is local, so no S3 is needed. */
    private class PresigningStorageFacade : StorageFacade() {
        override fun createStorageProvider(
            journalist: Journalist,
            failureFacade: FailureFacade,
            workingDirectory: Path,
            repository: String,
            storageSettings: StorageProviderSettings,
        ): StorageProvider? =
            super.createStorageProvider(journalist, failureFacade, workingDirectory, repository, storageSettings)
                ?.let { PresigningStorage(it) }
    }

    private class PresigningStorage(private val delegate: StorageProvider) : StorageProvider by delegate {
        override fun presignedGetUrl(location: Location, ttl: Duration): String? =
            if (delegate.exists(location)) "https://bucket.example.test/$location?X-Amz-Expires=${ttl.seconds}" else null
    }

    private companion object {
        const val MIRROR = "presigned-mirror"
        val NOW: Instant = Instant.parse("2026-11-16T10:00:00Z")
    }

}
