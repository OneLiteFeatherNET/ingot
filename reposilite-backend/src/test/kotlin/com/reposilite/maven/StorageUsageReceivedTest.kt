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
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executor
import kotlin.io.path.createDirectories

/** Figures that another instance computed are applied in place of the local ones, and only when they are newer. */
internal class StorageUsageReceivedTest {

    @TempDir
    lateinit var workingDirectory: File

    private val logger = InMemoryLogger()
    private val measuredAt = Instant.parse("2026-11-08T10:00:00Z")
    private val laterAt = Instant.parse("2026-11-08T10:05:00Z")
    private val synchronous = Executor { task -> task.run() }

    private fun storage(name: String): StorageProvider =
        FileSystemStorageProviderFactory.of(logger, workingDirectory.toPath().resolve(name).createDirectories(), "100%")

    /** A service whose local figures are five bytes in one file of "releases", measured at [measuredAt]. */
    private fun measuredService(): StorageUsageService {
        val releases = storage("releases")
        releases.putFile(Location.of("group/app/1.0/app-1.0.jar"), ByteArray(5).inputStream())
        val clock = Clock.fixed(measuredAt, ZoneOffset.UTC)
        return StorageUsageService(logger, clock, { listOf(StorageTarget("releases", releases, quota = null)) }, synchronous)
            .also { it.refresh().join() }
    }

    @Test
    fun `a received snapshot replaces the figures of this instance`() {
        // given: this instance measured five bytes in one file of releases
        val usage = measuredService()

        // when: a newer snapshot from another instance arrives
        usage.applyReceived(
            StorageUsageRefreshedEvent(laterAt, listOf(StorageUsageFigure("releases", usedBytes = 900L, fileCount = 7L)))
        )

        // then: the reported figures are the received ones and carry the received time
        val current = usage.current()
        assertEquals(laterAt, current.computedAt)
        val releases = current.repositories.single()
        assertEquals(900L, releases.usedBytes)
        assertEquals(7L, releases.fileCount)
    }

    @Test
    fun `the figures measured here are kept until a snapshot arrives`() {
        // given: this instance measured five bytes in one file of releases
        val usage = measuredService()

        // when: nothing has been received
        val current = usage.current()

        // then: the local figures are still reported
        assertEquals(measuredAt, current.computedAt)
        assertEquals(5L, current.repositories.single().usedBytes)
    }

    @Test
    fun `a received snapshot older than the local figures is ignored`() {
        // given: this instance measured at measuredAt, and a snapshot from before that time arrives
        val usage = measuredService()
        val older = Instant.parse("2026-11-08T09:00:00Z")

        // when: the older snapshot is applied
        val applied = usage.applyReceived(
            StorageUsageRefreshedEvent(older, listOf(StorageUsageFigure("releases", usedBytes = 900L, fileCount = 7L)))
        )

        // then: the snapshot is refused and the local figures stay
        assertEquals(false, applied)
        assertEquals(5L, usage.current().repositories.single().usedBytes)
    }
}
