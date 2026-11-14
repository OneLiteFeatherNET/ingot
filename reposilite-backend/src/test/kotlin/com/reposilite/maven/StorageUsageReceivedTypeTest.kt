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
import com.reposilite.plugin.api.StorageUsageFigure
import com.reposilite.plugin.api.StorageUsageRefreshedEvent
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executor
import kotlin.io.path.createDirectories

/**
 * The storage type of a figure received from another replica. A repository this replica does not configure is unknown, and a
 * configured one keeps the type it was measured with, or the type of its own target when the figure does not carry one.
 */
internal class StorageUsageReceivedTypeTest {

    @TempDir
    lateinit var workingDirectory: File

    private val logger = InMemoryLogger()
    private val receivedAt = Instant.parse("2026-11-08T10:05:00Z")
    private val clock = Clock.fixed(receivedAt, ZoneOffset.UTC)
    private val synchronous = Executor { task -> task.run() }

    private fun storage(name: String): StorageProvider =
        FileSystemStorageProviderFactory.of(logger, workingDirectory.toPath().resolve(name).createDirectories(), "100%")

    private fun receive(service: StorageUsageService, figure: StorageUsageFigure): String {
        service.applyReceived(StorageUsageRefreshedEvent(receivedAt, listOf(figure)))
        return service.current().repositories.single().storageType
    }

    @Test
    fun `a received figure of a repository this replica does not configure reports unknown`() {
        // given: a service that configures no repository
        val service = StorageUsageService(logger, clock, { emptyList() }, synchronous)

        // when: a figure of a repository of type s3 arrives
        val type = receive(service, StorageUsageFigure("archive", usedBytes = 900L, fileCount = 7L, storageType = "s3"))

        // then: this replica does not know the type, so it is not reported as fs or s3
        assertThat(type).`as`("storage type of an unconfigured repository").isEqualTo("unknown")
    }

    @Test
    fun `a received figure of a configured repository keeps the storage type it was measured with`() {
        // given: a configured repository of type fs
        val releases = storage("releases")
        val service = StorageUsageService(
            logger,
            clock,
            { listOf(StorageTarget("releases", releases, quota = null, storageType = "fs")) },
            synchronous,
        )

        // when: a figure measured as s3 arrives for that repository
        val type = receive(service, StorageUsageFigure("releases", usedBytes = 900L, fileCount = 7L, storageType = "s3"))

        // then: the figure reports the type it was measured with
        assertThat(type).`as`("storage type of a configured repository with a measured type").isEqualTo("s3")
    }

    @Test
    fun `a received figure without a storage type keeps the type of the configured target`() {
        // given: a configured repository of type fs, and a figure from a replica that does not send the type
        val releases = storage("releases")
        val service = StorageUsageService(
            logger,
            clock,
            { listOf(StorageTarget("releases", releases, quota = null, storageType = "fs")) },
            synchronous,
        )

        // when: the figure arrives without a storage type
        val type = receive(service, StorageUsageFigure("releases", usedBytes = 900L, fileCount = 7L))

        // then: the configured type is reported, as it was before the type was sent
        assertThat(type).`as`("storage type of a figure without a type").isEqualTo("fs")
    }

}
