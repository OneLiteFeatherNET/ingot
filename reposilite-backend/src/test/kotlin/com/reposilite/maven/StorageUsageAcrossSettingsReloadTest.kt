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
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.internalServerError
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageFacade
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.StorageProviderSettings
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import panda.std.Result
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

private const val TIMEOUT_SECONDS = 10L

/**
 * Holds the first listing that runs while it is armed, so a test can reload the settings in the middle of a usage walk.
 */
private class ListingGate {

    private val armed = AtomicBoolean(false)
    private val entered = CountDownLatch(1)
    private val released = CountDownLatch(1)

    fun arm() {
        armed.set(true)
    }

    fun pass() {
        if (!armed.getAndSet(false)) return
        entered.countDown()
        check(released.await(TIMEOUT_SECONDS, SECONDS)) { "the held listing was never released" }
    }

    fun awaitEntered() {
        check(entered.await(TIMEOUT_SECONDS, SECONDS)) { "no listing reached the gate" }
    }

    fun release() {
        released.countDown()
    }

}

/**
 * Storage that fails every listing once it is shut down, as an S3 repository does after its client has been closed.
 */
private class ClosingStorageProvider(
    private val delegate: StorageProvider,
    private val gate: ListingGate,
) : StorageProvider by delegate {

    @Volatile
    private var shutDown = false

    override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> {
        gate.pass()
        if (shutDown) {
            return internalServerError("Connection pool shut down")
        }
        return delegate.getFileDetails(location)
    }

    override fun shutdown() {
        shutDown = true
    }

}

private class ClosingStorageFacade(private val gate: ListingGate) : StorageFacade() {

    override fun createStorageProvider(
        journalist: Journalist,
        failureFacade: FailureFacade,
        workingDirectory: Path,
        repository: String,
        storageSettings: StorageProviderSettings,
    ): StorageProvider? =
        super.createStorageProvider(journalist, failureFacade, workingDirectory, repository, storageSettings)
            ?.let { ClosingStorageProvider(it, gate) }

}

internal class StorageUsageAcrossSettingsReloadTest : MavenSpecification() {

    private val gate = ListingGate()

    override fun repositories(): List<RepositorySettings> =
        listOf(
            RepositorySettings(id = "releases"),
            // A mirror without a reference: the factory skips it and warns every time the repository is built
            RepositorySettings(id = "mirror", proxied = listOf(MirroredRepositorySettings(reference = ""))),
        )

    override fun storageFacade(): StorageFacade =
        ClosingStorageFacade(gate)

    @Test
    fun `should count the files of an unchanged repository when settings are reloaded during the walk`() {
        // given: a file in releases, and a walk that is held at its first listing
        addFileToRepository(FileSpec("releases", "com/app/1.0/app-1.0.jar", "12345"))
        gate.arm()
        val measurement = mavenFacade.refreshStorageUsage()
        gate.awaitEntered()

        // when: the settings are reloaded with the same repositories while the walk is held, and then the walk goes on
        mavenSettings.update(MavenSettings(repositories = repositories()))
        gate.release()
        val usage = measurement.get(TIMEOUT_SECONDS, SECONDS)

        // then: the file is counted and no listing of any repository failed
        val releases = usage.repositories.single { it.id == "releases" }
        assertEquals(5L, releases.usedBytes, "bytes of releases after the reload")
        assertEquals(1L, releases.fileCount, "files of releases after the reload")
        assertEquals(emptyList<String>(), unreadableListings(), "listings that failed during the walk")
    }

    @Test
    fun `should keep serving the same repository instance when settings are reloaded without a change`() {
        // given: the repository as it is served before the reload
        val releases = mavenFacade.getRepository("releases")

        // when: the settings are reloaded with the same repositories
        mavenSettings.update(MavenSettings(repositories = repositories()))

        // then: the repository is still the same live instance, so its storage was not closed under it
        assertSame(releases, mavenFacade.getRepository("releases"), "instance of releases after the reload")
    }

    @Test
    fun `should not build the repositories again when a usage refresh runs`() {
        // given: the mirror warning logged once while the repositories were built
        val warningsBefore = mirrorWarnings()

        // when: usage is refreshed
        mavenFacade.refreshStorageUsage().get(TIMEOUT_SECONDS, SECONDS)

        // then: no repository was built again, so no warning was logged again
        assertEquals(warningsBefore, mirrorWarnings(), "mirror warnings after a usage refresh")
    }

    @Test
    fun `should not build the repositories again when settings are reloaded without a change`() {
        // given: the mirror warning logged once while the repositories were built
        val warningsBefore = mirrorWarnings()

        // when: the settings are reloaded with the same repositories
        mavenSettings.update(MavenSettings(repositories = repositories()))

        // then: unchanged repositories are kept, so no warning was logged again
        assertEquals(warningsBefore, mirrorWarnings(), "mirror warnings after a reload without a change")
    }

    @Test
    fun `should measure the repositories that exist after a settings change`() {
        // given: a file in releases
        addFileToRepository(FileSpec("releases", "a/a.jar", "123"))

        // when: a repository is added by a settings reload, a file is stored in it, and usage is refreshed
        mavenSettings.update(MavenSettings(repositories = repositories() + RepositorySettings(id = "snapshots")))
        addFileToRepository(FileSpec("snapshots", "b/b.jar", "12"))
        val usage = mavenFacade.refreshStorageUsage().get(TIMEOUT_SECONDS, SECONDS)

        // then: both repositories are measured with their own bytes
        val byId = usage.repositories.associateBy { it.id }
        assertEquals(3L, byId.getValue("releases").usedBytes, "bytes of releases")
        assertEquals(2L, byId.getValue("snapshots").usedBytes, "bytes of snapshots")
        assertEquals(emptyList<String>(), unreadableListings(), "listings that failed")
    }

    private fun mirrorWarnings(): Int =
        logger.messages.count { "Empty reference value of mirrored repository" in it.value }

    private fun unreadableListings(): List<String> =
        logger.messages.map { it.value }.filter { "Cannot read" in it }

}
