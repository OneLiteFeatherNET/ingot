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

package com.reposilite.cleanup

import com.reposilite.cleanup.application.CleanupPolicy
import com.reposilite.cleanup.application.CleanupScope
import com.reposilite.cleanup.infrastructure.InMemoryCleanupRunRepository
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import com.reposilite.token.AccessTokenIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createDirectories

/** A read-only repository is skipped by every cleanup run, so the files of a frozen repository survive a policy. */
internal class CleanupReadOnlyTest {

    @TempDir
    lateinit var workingDirectory: File

    private val logger = InMemoryLogger()
    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private val actor = CleanupActor(by = "manager", accessToken = AccessTokenIdentifier())

    private fun storage(name: String): StorageProvider =
        FileSystemStorageProviderFactory.of(logger, workingDirectory.toPath().resolve(name).createDirectories(), "100%")

    /** Writes a version with a pom and a jar, so the inventory sees it as one version. */
    private fun StorageProvider.writeVersion(artifact: String, version: String) {
        putFile(Location.of("$artifact/$version/app-$version.pom"), "pom".byteInputStream())
        putFile(Location.of("$artifact/$version/app-$version.jar"), "jar-content".byteInputStream())
    }

    private val policy = CleanupPolicy(
        id = "keep-one",
        enabled = true,
        repositories = listOf("frozen", "open"),
        scope = CleanupScope.RELEASES,
        keepLatest = 1,
    )

    private fun <T> Result<T, ErrorResponse>.orThrow(): T =
        if (isOk) get() else throw IllegalStateException(error.message)

    private fun service(targets: List<CleanupTarget>, deletedPaths: MutableList<String>): CleanupService =
        CleanupService(
            journalist = logger,
            clock = clock,
            policies = { listOf(policy) },
            targets = { targets },
            deleter = VersionDeleter { repository, path, _, _ ->
                deletedPaths += "$repository:$path"
                Result.ok<Unit, ErrorResponse>(Unit)
            },
            runs = InMemoryCleanupRunRepository(),
            extensions = Extensions(logger),
        )

    @Test
    fun `should not delete any version of a read-only repository while another repository is cleaned`() {
        // given: a read-only repository and an open one, each holding two releases
        val frozen = storage("frozen")
        frozen.writeVersion("net/example/app", "1.0")
        frozen.writeVersion("net/example/app", "1.1")
        val open = storage("open")
        open.writeVersion("net/example/app", "1.0")
        open.writeVersion("net/example/app", "1.1")
        val deleted = mutableListOf<String>()
        val cleanup = service(
            targets = listOf(
                CleanupTarget("frozen", frozen, storesMirrors = false, cachedVersions = emptyMap(), readOnly = true),
                CleanupTarget("open", open, storesMirrors = false, cachedVersions = emptyMap()),
            ),
            deletedPaths = deleted,
        )

        // when: the policy runs
        val run = cleanup.run("keep-one", actor, CleanupTrigger.SCHEDULED).orThrow()

        // then: only the open repository loses its older version
        assertEquals(listOf("open:net/example/app/1.0"), deleted, "deletions of the run")
        assertEquals(1, run.deletedCount, "deleted count of the run")
    }

    @Test
    fun `should list a read-only repository as skipped in the preview`() {
        // given: the same two repositories
        val frozen = storage("frozen")
        frozen.writeVersion("net/example/app", "1.0")
        frozen.writeVersion("net/example/app", "1.1")
        val cleanup = service(
            targets = listOf(CleanupTarget("frozen", frozen, storesMirrors = false, cachedVersions = emptyMap(), readOnly = true)),
            deletedPaths = mutableListOf(),
        )

        // when: the policy is previewed
        val preview = cleanup.preview("keep-one").orThrow()

        // then: nothing is planned there, and the preview names the repository as skipped
        assertEquals(0, preview.count, "planned deletions of a read-only repository")
        assertTrue(preview.warnings.any { it.contains("'frozen'") && it.contains("read-only") && it.contains("skips") }, "warnings: ${preview.warnings}")
    }

}
