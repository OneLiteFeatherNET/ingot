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

import com.reposilite.audit.MutableClock
import com.reposilite.cleanup.application.CleanupPolicy
import com.reposilite.cleanup.application.CleanupScope
import com.reposilite.cleanup.infrastructure.InMemoryCleanupRunRepository
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.scheduling.ScheduledJobClaims
import com.reposilite.scheduling.SqliteTestDatabase
import com.reposilite.scheduling.infrastructure.SqlScheduledJobClaims
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import com.reposilite.shared.ErrorResponse
import com.reposilite.token.AccessTokenIdentifier
import io.javalin.http.HttpStatus.CONFLICT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectories

/**
 * The cross-instance guard of a cleanup run: the lease `cleanup:<policy>:active` is held for the whole run, renewed from
 * the deletion loop and released in every case. The clock is a [MutableClock], so no test waits in real time.
 */
internal class CleanupServiceLeaseTest {

    @TempDir
    lateinit var workingDirectory: File

    private val logger = InMemoryLogger()
    private val actor = CleanupActor(by = "manager", accessToken = AccessTokenIdentifier())
    private val activeKey = "cleanup:keep-one:active"
    private val policy = CleanupPolicy(
        id = "keep-one",
        enabled = true,
        repositories = listOf("releases"),
        scope = CleanupScope.RELEASES,
        keepLatest = 1,
    )

    @Test
    fun `refuses a run of a live policy on another instance and deletes nothing`() {
        SqliteTestDatabase().use { shared ->
            // given: two versions of one artifact, and two services that share one claim store
            val clock = MutableClock()
            val storage = storage()
            storage.writeVersion("net/example/app", "1.0")
            storage.writeVersion("net/example/app", "2.0")
            val calls = mutableListOf<String>()
            var secondRun: Result<CleanupRun, ErrorResponse>? = null
            lateinit var second: CleanupService

            val first = service(
                clock = clock,
                claims = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a"),
                storage = storage,
                deleter = VersionDeleter { _, path, _, _ ->
                    calls += "first:$path"
                    if (secondRun == null) {
                        // when: the second instance is asked for a manual run while the first one is still deleting
                        secondRun = second.run("keep-one", actor, CleanupTrigger.MANUAL)
                    }
                    Result.ok(Unit)
                },
            )
            second = service(
                clock = clock,
                claims = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b"),
                storage = storage,
                deleter = VersionDeleter { _, path, _, _ -> calls += "second:$path"; Result.ok(Unit) },
            )

            first.run("keep-one", actor, CleanupTrigger.MANUAL)

            // then: the second run is refused with the conflict of the existing answer, and deletes nothing
            assertThat(secondRun!!.isErr).`as`("second run is refused").isTrue()
            assertThat(secondRun!!.error.status).`as`("status of the refusal").isEqualTo(CONFLICT.code)
            assertThat(secondRun!!.error.message).`as`("message of the refusal").isEqualTo("Cleanup policy keep-one is already running")
            assertThat(calls).`as`("deletions of the two runs").containsExactly("first:net/example/app/1.0")
        }
    }

    @Test
    fun `releases the active lease after a run that throws`() {
        // given: a claim store that records its calls, and a deleter that fails on the first deletion
        val clock = MutableClock()
        val claims = RecordingClaims(clock)
        val storage = storage()
        storage.writeVersion("net/example/app", "1.0")
        storage.writeVersion("net/example/app", "2.0")
        val service = service(clock = clock, claims = claims, storage = storage, deleter = VersionDeleter { _, _, _, _ -> throw RuntimeException("disk full") })

        // when: the run is started and its deletion throws
        assertThrows(RuntimeException::class.java) { service.run("keep-one", actor, CleanupTrigger.MANUAL) }

        // then: the lease was taken for the run and released by it
        assertThat(claims.calls)
            .`as`("calls to the claim store of the run that threw")
            .containsExactly("lease:$activeKey", "complete:$activeKey:false")
    }

    @Test
    fun `records a lease lost when another instance took over during the deletions`() {
        // given: three versions, so two deletions are planned, and a lease that another instance has taken over
        val clock = MutableClock()
        val claims = RecordingClaims(clock, extendGranted = false)
        val storage = storage()
        storage.writeVersion("net/example/app", "1.0")
        storage.writeVersion("net/example/app", "2.0")
        storage.writeVersion("net/example/app", "3.0")
        val deleted = mutableListOf<String>()
        val service = service(
            clock = clock,
            claims = claims,
            storage = storage,
            deleter = VersionDeleter { _, path, _, _ ->
                deleted += path
                // the first deletion takes longer than the renewal interval, so the next check renews the lease
                clock.advance(Duration.ofSeconds(21))
                Result.ok(Unit)
            },
        )

        // when: the run deletes
        val run = service.run("keep-one", actor, CleanupTrigger.MANUAL).orThrow()

        // then: the second deletion never starts, and the run records the lost lease as its reason
        assertThat(deleted).`as`("deletions after the lease was lost").hasSize(1)
        assertThat(run.status).`as`("status of a run that lost its lease").isEqualTo(CleanupRunStatus.PARTIAL)
        assertThat(run.failures).`as`("failures of the run").anyMatch { it.contains("lease lost") }
    }

    @Test
    fun `extends the active lease at most once every 20 seconds of the clock`() {
        // given: five versions, so four deletions, and a clock that moves seven seconds per deletion
        val clock = MutableClock()
        val claims = RecordingClaims(clock)
        val storage = storage()
        (1..5).forEach { storage.writeVersion("net/example/app", "$it.0") }
        val service = service(
            clock = clock,
            claims = claims,
            storage = storage,
            deleter = VersionDeleter { _, _, _, _ ->
                clock.advance(Duration.ofSeconds(7))
                Result.ok(Unit)
            },
        )

        // when: the run deletes for 28 seconds
        service.run("keep-one", actor, CleanupTrigger.MANUAL).orThrow()

        // then: the lease was extended, and no two extensions are closer than 20 seconds
        assertThat(claims.extendTimes).`as`("extensions of the lease during a run of 28 seconds").isNotEmpty()
        claims.extendTimes.zipWithNext().forEach { (earlier, later) ->
            assertThat(Duration.between(earlier, later))
                .`as`("time between two extensions of the lease")
                .isGreaterThanOrEqualTo(Duration.ofSeconds(20))
        }
    }

    @Test
    fun `logs a skip and deletes nothing when a scheduled run finds a manual run holding the lease`() {
        SqliteTestDatabase().use { shared ->
            // given: a manual run that is still deleting, and a scheduled run of the same policy on another instance
            val clock = MutableClock()
            val storage = storage()
            storage.writeVersion("net/example/app", "1.0")
            storage.writeVersion("net/example/app", "2.0")
            val calls = mutableListOf<String>()
            var scheduled: Result<CleanupRun, ErrorResponse>? = null
            lateinit var scheduler: CleanupService

            val manual = service(
                clock = clock,
                claims = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a"),
                storage = storage,
                deleter = VersionDeleter { _, path, _, _ ->
                    calls += "manual:$path"
                    if (scheduled == null) {
                        // when: the scheduled run becomes due while the manual run holds the lease
                        scheduled = scheduler.runScheduled("keep-one")
                    }
                    Result.ok(Unit)
                },
            )
            scheduler = service(
                clock = clock,
                claims = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b"),
                storage = storage,
                deleter = VersionDeleter { _, path, _, _ -> calls += "scheduled:$path"; Result.ok(Unit) },
            )

            manual.run("keep-one", actor, CleanupTrigger.MANUAL).orThrow()

            // then: the scheduled run is refused, deletes nothing, and the log says that it was skipped
            assertThat(scheduled!!.isErr).`as`("scheduled run while the manual run holds the lease").isTrue()
            assertThat(calls).`as`("deletions of both runs").containsExactly("manual:net/example/app/1.0")
            assertThat(logger.messages.map { it.value })
                .`as`("log lines of the skipped scheduled run")
                .anyMatch { it.contains("Scheduled run of keep-one skipped") }
        }
    }

    /** Records the calls to the claim store of a run. The lease is always granted, the extension as configured. */
    private class RecordingClaims(
        private val clock: MutableClock,
        private val extendGranted: Boolean = true,
    ) : ScheduledJobClaims {

        val calls = mutableListOf<String>()
        val extendTimes = mutableListOf<Instant>()

        override fun claim(key: String): Boolean = true

        override fun lease(key: String, ttl: Duration): Boolean {
            calls += "lease:$key"
            return true
        }

        override fun extend(key: String, ttl: Duration): Boolean {
            calls += "extend:$key"
            extendTimes += clock.instant()
            return extendGranted
        }

        override fun complete(key: String, done: Boolean) {
            calls += "complete:$key:$done"
        }

    }

    private fun storage(): StorageProvider =
        FileSystemStorageProviderFactory.of(logger, workingDirectory.toPath().resolve("releases").createDirectories(), "100%")

    /** Writes a version with a pom and a jar, so the inventory sees it as one version. */
    private fun StorageProvider.writeVersion(artifact: String, version: String) {
        putFile(Location.of("$artifact/$version/app-$version.pom"), "pom".byteInputStream())
        putFile(Location.of("$artifact/$version/app-$version.jar"), "jar-content".byteInputStream())
    }

    private fun <T> Result<T, ErrorResponse>.orThrow(): T =
        if (isOk) get() else throw AssertionError("Expected success but got ${error}")

    private fun service(
        clock: MutableClock,
        claims: ScheduledJobClaims,
        storage: StorageProvider,
        deleter: VersionDeleter,
    ) = CleanupService(
        journalist = logger,
        clock = clock,
        policies = { listOf(policy) },
        targets = { listOf(CleanupTarget(name = "releases", storage = storage, storesMirrors = false, cachedVersions = emptyMap())) },
        deleter = deleter,
        runs = InMemoryCleanupRunRepository(),
        claims = claims,
    )

}
