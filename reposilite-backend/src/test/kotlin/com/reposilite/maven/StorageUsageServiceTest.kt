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

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executor
import kotlin.io.path.createDirectories
import kotlin.io.path.fileStore
import kotlin.io.path.writeBytes

internal class StorageUsageServiceTest {

    @TempDir
    lateinit var workingDirectory: File

    private val logger = InMemoryLogger()
    private val now = Instant.parse("2026-10-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val synchronous = Executor { task -> task.run() }

    private fun storage(name: String, quota: String = "100%"): StorageProvider =
        FileSystemStorageProviderFactory.of(logger, workingDirectory.toPath().resolve(name).createDirectories(), quota)

    private fun write(storage: StorageProvider, path: String, size: Int) {
        storage.putFile(Location.of(path), ByteArray(size).inputStream())
    }

    private fun service(vararg targets: StorageTarget, executor: Executor = synchronous) =
        StorageUsageService(logger, clock, { targets.toList() }, executor)

    @Test
    fun `should sum the sizes and count the files of each repository`() {
        // given: two files in one repository and nothing in another
        val releases = storage("releases")
        write(releases, "group/app/1.0/app-1.0.jar", 5)
        write(releases, "group/app/1.0/app-1.0.pom", 3)
        val snapshots = storage("snapshots")

        // when: usage is computed
        val usage = service(
            StorageTarget("releases", releases, quota = null),
            StorageTarget("snapshots", snapshots, quota = null),
        ).refresh().join()

        // then: each repository reports its own bytes and files, and the total adds them up
        val byId = usage.repositories.associateBy { it.id }
        assertEquals(8L, byId.getValue("releases").usedBytes)
        assertEquals(2L, byId.getValue("releases").fileCount)
        assertEquals(0L, byId.getValue("snapshots").usedBytes)
        assertEquals(0L, byId.getValue("snapshots").fileCount)
        assertEquals(8L, usage.totalBytes)
    }

    @Test
    fun `should report a fixed quota as configured and in bytes`() {
        // given: a repository with a fixed quota of 10 GB
        val releases = storage("releases", quota = "10GB")

        // when: usage is computed
        val usage = service(StorageTarget("releases", releases, quota = "10GB")).refresh().join()

        // then: the configured text and its byte value are both reported
        val repository = usage.repositories.single()
        assertEquals("10GB", repository.quota)
        assertEquals(10L * 1024 * 1024 * 1024, repository.quotaBytes)
    }

    @Test
    fun `should resolve a percentage quota against the capacity of the disk`() {
        // given: a repository limited to half of the disk
        val releases = storage("releases", quota = "50%")
        val capacity = workingDirectory.toPath().fileStore().totalSpace

        // when: usage is computed
        val usage = service(StorageTarget("releases", releases, quota = "50%")).refresh().join()

        // then: the quota in bytes is half of the disk capacity and the configured text is kept
        val repository = usage.repositories.single()
        assertEquals("50%", repository.quota)
        assertEquals((capacity * 0.5).toLong(), repository.quotaBytes)
    }

    @Test
    fun `should leave the quota unknown when the storage declares none`() {
        // given: a repository without a configured quota, as on object storage
        val releases = storage("releases")

        // when: usage is computed
        val repository = service(StorageTarget("releases", releases, quota = null)).refresh().join().repositories.single()

        // then: neither the text nor the bytes are reported
        assertNull(repository.quota)
        assertNull(repository.quotaBytes)
    }

    @Test
    fun `should report the storage type of each repository`() {
        // given: a repository on object storage, which declares no quota
        val releases = storage("releases")

        // when: usage is computed
        val repository = service(StorageTarget("releases", releases, quota = null, storageType = "s3")).refresh().join().repositories.single()

        // then: the storage type is reported next to the figures
        assertEquals("s3", repository.storageType)
    }

    @Test
    fun `should mark a loopback group as having no own storage and no quota`() {
        // given: a group repository whose own file system storage holds nothing, with a quota configured on it
        val group = storage("onelitefeather")

        // when: usage is computed for the group
        val repository = service(StorageTarget("onelitefeather", group, quota = null, storageType = "fs", group = true))
            .refresh().join().repositories.single()

        // then: the group is flagged, and no quota or bar is reported for it
        assertTrue(repository.group)
        assertNull(repository.quota)
        assertNull(repository.quotaBytes)
    }

    @Test
    fun `should report nothing computed before the first computation`() {
        // given: a service that has never computed
        val usage = service(StorageTarget("releases", storage("releases"), quota = null), executor = Executor { })

        // when: the current usage is read
        val current = usage.current()

        // then: there is no timestamp and no repository figures yet
        assertNull(current.computedAt)
        assertTrue(current.repositories.isEmpty())
    }

    @Test
    fun `should stamp the snapshot with the injected clock`() {
        // given: a service with a fixed clock
        val usage = service(StorageTarget("releases", storage("releases"), quota = null))

        // when: usage is computed
        usage.refresh().join()

        // then: the computation time is the clock's instant
        assertEquals(now, usage.current().computedAt)
    }

    @Test
    fun `should share one computation between overlapping refreshes`() {
        // given: an executor that only runs tasks when the test says so
        val pending = mutableListOf<Runnable>()
        val usage = service(StorageTarget("releases", storage("releases"), quota = null), executor = Executor { pending += it })

        // when: two refreshes are requested before the first one runs
        val first = usage.refresh()
        val second = usage.refresh()

        // then: both calls share one computation, which is reported as running
        assertSame(first, second)
        assertTrue(usage.current().computing)

        // when: the computation runs
        pending.forEach { it.run() }

        // then: it is finished and no longer reported as running
        assertTrue(first.isDone)
        assertEquals(false, usage.current().computing)
    }

    @Test
    fun `should keep the previous figures visible while a new computation runs`() {
        // given: a computed snapshot showing 3 bytes, and a later file of 10 bytes that is not yet measured
        val releases = storage("releases")
        write(releases, "a.bin", 3)
        val pending = mutableListOf<Runnable>()
        val usage = service(StorageTarget("releases", releases, quota = null), executor = Executor { pending += it })
        usage.refresh()
        pending.removeAt(0).run()
        write(releases, "b.bin", 10)

        // when: a new computation is started and has not run yet
        usage.refresh()

        // then: the old figures are still served, flagged as computing
        val current = usage.current()
        assertEquals(3L, current.totalBytes)
        assertTrue(current.computing)
    }

}
