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
import com.reposilite.storage.StorageProviderSettings
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import com.reposilite.storage.filesystem.FileSystemStorageProviderSettings
import net.onelitefeather.ingot.storage.capability.UsageCapability
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executor

/**
 * A file system storage that declares its usage capability with the given capacity and quota support.
 */
private class CapacityStorage(
    delegate: StorageProvider,
    private val capacity: Long?,
    override val supportsQuota: Boolean,
) : StorageProvider by delegate, UsageCapability {

    override val reportsUsage: Boolean = true

    override fun totalCapacityBytes(): Long? = capacity

}

private data class ExampleSettings(override val type: String = "example") : StorageProviderSettings

/**
 * The quota and the storage type of a repository are read through the capabilities of its storage and the settings it was built from.
 */
internal class StorageUsageCapabilityTest {

    @TempDir
    lateinit var workingDirectory: File

    private val logger = InMemoryLogger()
    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val synchronous = Executor { task -> task.run() }

    private fun fileSystem(name: String): StorageProvider =
        FileSystemStorageProviderFactory.of(logger, workingDirectory.toPath().resolve(name), "100%")

    @Test
    fun `a storage that does not support quota reports no quota even when its settings carry one`() {
        // given: a storage without quota support and settings with a 50% quota
        val storage = CapacityStorage(fileSystem("unsupported"), capacity = 1_000L, supportsQuota = false)

        // when: the quota of the repository is read
        val quota = configuredQuota(storage, FileSystemStorageProviderSettings(quota = "50%"))

        // then: no quota is reported
        assertThat(quota).`as`("quota of a storage without quota support").isNull()
    }

    @Test
    fun `a storage without the usage capability reports no quota`() {
        // given: a storage that declares no capability at all, with settings that carry a quota
        val storage = fileSystem("plain")

        // when: the quota of the repository is read
        val quota = configuredQuota(storage, FileSystemStorageProviderSettings(quota = "50%"))

        // then: no quota is reported, as it was never read for a storage core does not know
        assertThat(quota).`as`("quota of a storage without the capability").isNull()
    }

    @Test
    fun `a storage that supports quota reports the quota of its settings`() {
        // given: a storage with quota support and settings with a 50% quota
        val storage = CapacityStorage(fileSystem("supported"), capacity = 1_000L, supportsQuota = true)

        // when: the quota of the repository is read
        val quota = configuredQuota(storage, FileSystemStorageProviderSettings(quota = "50%"))

        // then: the quota is the configured value
        assertThat(quota).`as`("quota of a supporting storage").isEqualTo("50%")
    }

    @Test
    fun `a percentage quota is measured against the total capacity of the storage`() {
        // given: a storage with a capacity of 1000 bytes and a 50% quota
        val storage = CapacityStorage(fileSystem("capacity"), capacity = 1_000L, supportsQuota = true)
        val service = StorageUsageService(logger, clock, { listOf(StorageTarget("capacity", storage, quota = "50%")) }, synchronous)

        // when: usage is measured
        val usage = service.refresh().get()

        // then: the quota bytes are half of the capacity
        assertThat(usage.repositories.single().quotaBytes).`as`("quota bytes of a 50% quota").isEqualTo(500L)
    }

    @Test
    fun `a percentage quota is unknown when the storage reports no capacity`() {
        // given: a storage that cannot report its capacity, with a 50% quota
        val storage = CapacityStorage(fileSystem("unknown"), capacity = null, supportsQuota = true)
        val service = StorageUsageService(logger, clock, { listOf(StorageTarget("unknown", storage, quota = "50%")) }, synchronous)

        // when: usage is measured
        val usage = service.refresh().get()

        // then: the quota bytes are unknown
        assertThat(usage.repositories.single().quotaBytes).`as`("quota bytes without a known capacity").isNull()
    }

    @Test
    fun `a fixed quota is read as written and does not depend on the capacity`() {
        // given: a storage with a capacity, and a fixed quota of 10 MB
        val storage = CapacityStorage(fileSystem("fixed"), capacity = 1_000L, supportsQuota = true)
        val service = StorageUsageService(logger, clock, { listOf(StorageTarget("fixed", storage, quota = "10MB")) }, synchronous)

        // when: usage is measured
        val usage = service.refresh().get()

        // then: the quota bytes are 10 MB
        assertThat(usage.repositories.single().quotaBytes).`as`("quota bytes of a 10MB quota").isEqualTo(10L * 1024 * 1024)
    }

    @Test
    fun `the storage type of a plugin is the type of its settings`() {
        // when: the storage type is read from the settings of a plugin
        val type = storageTypeOf(ExampleSettings())

        // then: the type is the one the plugin configured
        assertThat(type).`as`("storage type of a plugin").isEqualTo("example")
    }

    @Test
    fun `a repository without settings reports the storage type unknown`() {
        // when: the storage type is read for a repository without settings
        val type = storageTypeOf(null)

        // then: the type is unknown, not fs
        assertThat(type).`as`("storage type without settings").isEqualTo("unknown")
    }

}
