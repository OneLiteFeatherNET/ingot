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
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.errorResponse
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import com.reposilite.token.AccessTokenIdentifier
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.FORBIDDEN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createDirectories

internal class CleanupServiceTest {

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

    private fun keepOne(repository: String = "releases") = CleanupPolicy(
        id = "keep-one",
        enabled = true,
        repositories = listOf(repository),
        scope = CleanupScope.RELEASES,
        keepLatest = 1,
    )

    /** A deleter that removes the version from the storage, like the real delete service does. */
    private fun removingDeleter(storages: Map<String, StorageProvider>, calls: MutableList<String> = mutableListOf()) =
        VersionDeleter { repository, path, _ ->
            calls += "$repository:$path"
            storages.getValue(repository).removeFile(Location.of(path))
        }

    private fun service(
        policies: List<CleanupPolicy>,
        targets: List<CleanupTarget>,
        deleter: VersionDeleter,
        runs: CleanupRunRepository = InMemoryCleanupRunRepository(),
    ) = CleanupService(
        journalist = logger,
        clock = clock,
        policies = { policies },
        targets = { targets },
        deleter = deleter,
        runs = runs,
    )

    @Test
    fun `should plan the deletions of a dry run without deleting anything`() {
        // given: two releases of one artifact and a policy that keeps the newest one
        val releases = storage("releases")
        releases.writeVersion("net/example/app", "1.0")
        releases.writeVersion("net/example/app", "1.1")
        val calls = mutableListOf<String>()
        val cleanup = service(listOf(keepOne()), listOf(CleanupTarget("releases", releases, cached = false)), removingDeleter(mapOf("releases" to releases), calls))

        // when: the policy is previewed
        val preview = cleanup.preview("keep-one").orThrow()

        // then: the older release is listed, and nothing was deleted
        assertEquals(listOf("net/example/app/1.0"), preview.deletions.map { it.path })
        assertEquals(1, preview.count)
        assertTrue(preview.totalBytes > 0)
        assertEquals(emptyList<String>(), calls)
        assertTrue(releases.exists(Location.of("net/example/app/1.0/app-1.0.pom")))
    }

    @Test
    fun `should delete the planned versions and record the run`() {
        // given: two releases and a policy that keeps the newest one
        val releases = storage("releases")
        releases.writeVersion("net/example/app", "1.0")
        releases.writeVersion("net/example/app", "1.1")
        val calls = mutableListOf<String>()
        val cleanup = service(listOf(keepOne()), listOf(CleanupTarget("releases", releases, cached = false)), removingDeleter(mapOf("releases" to releases), calls))

        // when: the policy is run
        val run = cleanup.run("keep-one", actor, CleanupTrigger.MANUAL).orThrow()

        // then: the older version went through the deleter and is gone from the storage
        assertEquals(listOf("releases:net/example/app/1.0"), calls)
        assertFalse(releases.exists(Location.of("net/example/app/1.0/app-1.0.pom")))
        assertTrue(releases.exists(Location.of("net/example/app/1.1/app-1.1.pom")))

        // and: the run record says what happened
        assertEquals(CleanupRunStatus.SUCCEEDED, run.status)
        assertEquals(1, run.deletedCount)
        assertTrue(run.deletedBytes > 0)
        assertEquals(emptyList<String>(), run.failures)
        assertEquals(listOf(run), cleanup.runs("keep-one"))
    }

    @Test
    fun `should pass the actor of the run to the deleter`() {
        // given: one release to delete
        val releases = storage("releases")
        releases.writeVersion("net/example/app", "1.0")
        releases.writeVersion("net/example/app", "1.1")
        val actors = mutableListOf<CleanupActor>()
        val cleanup = service(
            listOf(keepOne()),
            listOf(CleanupTarget("releases", releases, cached = false)),
            VersionDeleter { _, _, deleterActor -> actors += deleterActor; Result.ok(Unit) },
        )

        // when: the policy is run by a manager
        cleanup.run("keep-one", actor, CleanupTrigger.MANUAL).orThrow()

        // then: the deleter receives the manager as the actor
        assertEquals(listOf(actor), actors)
    }

    @Test
    fun `should report a partial run when some deletions fail`() {
        // given: two old releases to delete, where the deletion of one of them fails
        val releases = storage("releases")
        releases.writeVersion("net/example/app", "1.0")
        releases.writeVersion("net/example/app", "1.1")
        releases.writeVersion("net/example/app", "1.2")
        val cleanup = service(
            listOf(keepOne()),
            listOf(CleanupTarget("releases", releases, cached = false)),
            VersionDeleter { _, path, _ ->
                if (path.endsWith("1.0")) errorResponse(FORBIDDEN, "denied") else releases.removeFile(Location.of(path))
            },
        )

        // when: the policy is run
        val run = cleanup.run("keep-one", actor, CleanupTrigger.MANUAL).orThrow()

        // then: the run is partial, names the failed version and counts only the successful deletion
        assertEquals(CleanupRunStatus.PARTIAL, run.status)
        assertEquals(1, run.deletedCount)
        assertEquals(1, run.failures.size)
        assertTrue(run.failures.single().contains("net/example/app/1.0"), run.failures.single())
    }

    @Test
    fun `should report a failed run when every deletion fails`() {
        // given: one old release whose deletion always fails
        val releases = storage("releases")
        releases.writeVersion("net/example/app", "1.0")
        releases.writeVersion("net/example/app", "1.1")
        val cleanup = service(
            listOf(keepOne()),
            listOf(CleanupTarget("releases", releases, cached = false)),
            VersionDeleter { _, _, _ -> errorResponse(FORBIDDEN, "denied") },
        )

        // when: the policy is run
        val run = cleanup.run("keep-one", actor, CleanupTrigger.SCHEDULED).orThrow()

        // then: the run failed and deleted nothing
        assertEquals(CleanupRunStatus.FAILED, run.status)
        assertEquals(0, run.deletedCount)
        assertEquals(0L, run.deletedBytes)
    }

    @Test
    fun `should refuse to run a policy that does not exist`() {
        // given: no policies
        val cleanup = service(emptyList(), emptyList(), VersionDeleter { _, _, _ -> Result.ok(Unit) })

        // when: a run is requested for an unknown id
        val result = cleanup.run("missing", actor, CleanupTrigger.MANUAL)

        // then: the request is refused as not found
        assertEquals(NOT_FOUND.code, result.error.status)
    }

    @Test
    fun `should refuse a second run of a policy while the first one is still running`() {
        // given: a deleter that starts the same policy again while it is deleting
        val releases = storage("releases")
        releases.writeVersion("net/example/app", "1.0")
        releases.writeVersion("net/example/app", "1.1")
        var nested: Result<CleanupRun, ErrorResponse>? = null
        lateinit var cleanup: CleanupService
        cleanup = service(
            listOf(keepOne()),
            listOf(CleanupTarget("releases", releases, cached = false)),
            VersionDeleter { _, path, _ ->
                nested = cleanup.run("keep-one", actor, CleanupTrigger.MANUAL)
                releases.removeFile(Location.of(path))
            },
        )

        // when: the outer run deletes one version
        cleanup.run("keep-one", actor, CleanupTrigger.MANUAL).orThrow()

        // then: the nested request was refused as a conflict
        assertEquals(CONFLICT.code, nested!!.error.status)
    }

    @Test
    fun `should keep the twenty most recent runs of each policy`() {
        // given: a policy that has nothing to delete
        val cleanup = service(listOf(keepOne()), emptyList(), VersionDeleter { _, _, _ -> Result.ok(Unit) })

        // when: the policy is run 25 times
        repeat(25) { cleanup.run("keep-one", actor, CleanupTrigger.MANUAL).orThrow() }

        // then: only the last 20 runs are kept
        assertEquals(20, cleanup.runs("keep-one").size)
    }

    @Test
    fun `should warn that a mirror cache policy finds nothing in a repository without mirror copies`() {
        // given: a mirror cache policy on a repository that stores no mirrored artifacts
        val releases = storage("releases")
        val policy = CleanupPolicy(id = "cache", enabled = true, repositories = listOf("releases"), scope = CleanupScope.MIRROR_CACHE, notDownloadedForDays = 30)
        val cleanup = service(listOf(policy), listOf(CleanupTarget("releases", releases, cached = false)), VersionDeleter { _, _, _ -> Result.ok(Unit) })

        // when: the policy is previewed
        val preview = cleanup.preview("cache").orThrow()

        // then: the preview carries a warning that names the repository
        assertEquals(1, preview.warnings.size)
        assertTrue(preview.warnings.single().contains("releases"), preview.warnings.single())
    }

    @Test
    fun `should warn about a listed repository that does not exist`() {
        // given: a policy that names a repository which is not configured
        val policy = keepOne(repository = "missing")
        val cleanup = service(listOf(policy), emptyList(), VersionDeleter { _, _, _ -> Result.ok(Unit) })

        // when: the policy is previewed
        val preview = cleanup.preview("keep-one").orThrow()

        // then: the missing repository is reported
        assertEquals(listOf("repository 'missing' does not exist"), preview.warnings)
    }

    private fun <T> Result<T, ErrorResponse>.orThrow(): T =
        if (isOk) get() else throw AssertionError("Expected success but got ${error}")

}
