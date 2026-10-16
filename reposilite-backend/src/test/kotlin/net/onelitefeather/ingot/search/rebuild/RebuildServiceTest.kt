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

package net.onelitefeather.ingot.search.rebuild

import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.jar
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.withHarness
import net.onelitefeather.ingot.search.runs.RunKind
import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/** The rebuild of one or all repositories against fake storage and a SQLite index (artifact search spec, 3.6 and 10.2). */
internal class RebuildServiceTest {

    @TempDir
    lateinit var directory: Path

    private val oldTime = Instant.parse("2026-10-01T00:00:00Z")

    @Test
    fun `a rebuild stores a row with size and modification time for every parsed file`() {
        withHarness(directory, "rows") {
            entries.put("releases", jar(1), size = 2048, lastModified = oldTime)

            val run = service().rebuild(RunTrigger.MANUAL)

            val row = indexRow("releases", jar(1))!!
            assertThat(row[tables.artifact.sizeBytes]).`as`("size").isEqualTo(2048L)
            assertThat(row[tables.artifact.lastModified]).`as`("modification time").isEqualTo(oldTime.toEpochMilli())
            assertThat(row[tables.artifact.origin]).`as`("origin of a hosted file").isEqualTo("HOSTED")
            assertThat(run?.status).`as`("status").isEqualTo(RunStatus.COMPLETED)
        }
    }

    @Test
    fun `a rebuild counts files that do not parse and does not store them`() {
        withHarness(directory, "unparsed") {
            entries.put("releases", jar(1))
            entries.put("releases", "com/example/app1/1.0/other-1.0.jar")
            entries.put("releases", "com/example/app1/maven-metadata.xml")

            val run = service().rebuild(RunTrigger.MANUAL)!!

            assertThat(run.counters.unparsed).`as`("unparsed files").isEqualTo(1L)
            assertThat(run.counters.processed).`as`("entries read").isEqualTo(3L)
            assertThat(indexedPaths("releases")).`as`("stored paths").containsExactly(jar(1))
        }
    }

    @Test
    fun `a rebuild removes rows whose file is no longer stored`() {
        withHarness(directory, "stale") {
            seedRow("releases", jar(1), size = 1, indexedAt = clock.instant())
            seedRow("releases", jar(2), size = 1, indexedAt = clock.instant())
            entries.put("releases", jar(2))
            clock.advance(Duration.ofMinutes(5))

            val run = service().rebuild(RunTrigger.MANUAL)!!

            assertThat(indexedPaths("releases")).`as`("rows after the rebuild").containsExactly(jar(2))
            assertThat(run.counters.deleted).`as`("rows deleted as stale").isEqualTo(1L)
        }
    }

    @Test
    fun `a rebuild keeps the rows of a repository it could not read`() {
        withHarness(directory, "failing") {
            seedRow("broken", jar(1), size = 1, indexedAt = clock.instant())
            entries.addRepository("broken")
            entries.failing += "broken"
            entries.put("working", jar(1))
            clock.advance(Duration.ofMinutes(5))

            val run = service().rebuild(RunTrigger.MANUAL)!!

            assertThat(run.status).`as`("status with one unreadable repository").isEqualTo(RunStatus.FAILED)
            assertThat(run.counters.errors).`as`("errors").isEqualTo(1L)
            assertThat(run.lastError).`as`("last error").contains("storage unavailable")
            assertThat(indexedPaths("broken")).`as`("rows of the unreadable repository").containsExactly(jar(1))
            assertThat(indexedPaths("working")).`as`("rows of the readable repository").containsExactly(jar(1))
        }
    }

    @Test
    fun `a full rebuild removes the rows of a repository that no longer exists`() {
        withHarness(directory, "removed-repository") {
            seedRow("gone", jar(1), size = 1, indexedAt = clock.instant())
            entries.put("kept", jar(1))

            service().rebuild(RunTrigger.MANUAL)

            assertThat(indexedPaths("gone")).`as`("rows of the removed repository").isEmpty()
            assertThat(indexedPaths("kept")).`as`("rows of the existing repository").containsExactly(jar(1))
        }
    }

    @Test
    fun `a rebuild of named repositories leaves the other repositories alone`() {
        withHarness(directory, "named") {
            seedRow("gone", jar(1), size = 1, indexedAt = clock.instant())
            entries.put("kept", jar(1))
            entries.put("other", jar(1))

            service().rebuild(RunTrigger.MANUAL, repositories = listOf("kept"))

            assertThat(indexedPaths("kept")).`as`("rebuilt repository").containsExactly(jar(1))
            assertThat(indexedPaths("other")).`as`("repository that was not named").isEmpty()
            assertThat(indexedPaths("gone")).`as`("rows of a removed repository are left to a full run").containsExactly(jar(1))
        }
    }

    @Test
    fun `a cached mirror copy is indexed with the origin mirror and the upstream host`() {
        withHarness(directory, "mirror") {
            entries.put("proxy", jar(1))
            entries.put("proxy", jar(2))
            ledger.insertIfAbsent("proxy", "com/example/app1/1.0", clock.instant(), "repo.example.org")

            service().rebuild(RunTrigger.MANUAL)

            val cached = indexRow("proxy", jar(1))!!
            assertThat(cached[tables.artifact.origin]).`as`("origin of the cached copy").isEqualTo("MIRROR")
            assertThat(cached[tables.artifact.sourceHost]).`as`("host of the cached copy").isEqualTo("repo.example.org")
            val hosted = indexRow("proxy", jar(2))!!
            assertThat(hosted[tables.artifact.origin]).`as`("origin of a file in a directory without a ledger row").isEqualTo("HOSTED")
            assertThat(hosted[tables.artifact.sourceHost]).`as`("host of a hosted file").isNull()
        }
    }

    @Test
    fun `a cached copy whose host is unknown is a mirror row without a host`() {
        withHarness(directory, "mirror-unknown") {
            entries.put("proxy", jar(1))
            ledger.insertIfAbsent("proxy", "com/example/app1/1.0", clock.instant(), null)

            service().rebuild(RunTrigger.MANUAL)

            val row = indexRow("proxy", jar(1))!!
            assertThat(row[tables.artifact.origin]).`as`("origin").isEqualTo("MIRROR")
            assertThat(row[tables.artifact.sourceHost]).`as`("host").isNull()
        }
    }

    @Test
    fun `a rebuild keeps the checksums of a file whose size and modification time are unchanged`() {
        withHarness(directory, "checksum-kept") {
            val sha1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709"
            seedRow("releases", jar(1), size = 10, indexedAt = clock.instant(), sha1 = sha1)
            entries.put("releases", jar(1), size = 10, lastModified = oldTime)
            clock.advance(Duration.ofMinutes(5))

            service().rebuild(RunTrigger.MANUAL)

            assertThat(indexRow("releases", jar(1))!![tables.artifact.sha1]).`as`("sha1").isEqualTo(sha1)
        }
    }

    @Test
    fun `a rebuild drops the checksums of a file whose size changed`() {
        withHarness(directory, "checksum-dropped") {
            seedRow("releases", jar(1), size = 10, indexedAt = clock.instant(), sha1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709")
            entries.put("releases", jar(1), size = 11, lastModified = oldTime)
            clock.advance(Duration.ofMinutes(5))

            service().rebuild(RunTrigger.MANUAL)

            assertThat(indexRow("releases", jar(1))!![tables.artifact.sha1]).`as`("sha1 of a changed file").isNull()
        }
    }

    @Test
    fun `a completed full rebuild records its time as the last full rebuild`() {
        withHarness(directory, "last-full") {
            entries.put("releases", jar(1))

            service().rebuild(RunTrigger.MANUAL)

            assertThat(state.get(SearchStateKey.LAST_FULL_REBUILD))
                .`as`("last full rebuild")
                .isEqualTo(clock.millis().toString())
        }
    }

    @Test
    fun `a rebuild of named repositories does not count as a full rebuild`() {
        withHarness(directory, "last-full-named") {
            entries.put("releases", jar(1))

            service().rebuild(RunTrigger.MANUAL, repositories = listOf("releases"))

            assertThat(state.get(SearchStateKey.LAST_FULL_REBUILD)).`as`("last full rebuild").isNull()
        }
    }

    @Test
    fun `a second rebuild is refused while the lease of the first is live`() {
        withHarness(directory, "locked") {
            entries.put("releases", jar(1))
            val first = service("instance-a").start(RunTrigger.MANUAL)

            val second = service("instance-b").start(RunTrigger.MANUAL)

            assertThat(first).`as`("first start").isNotNull()
            assertThat(second).`as`("start while the lease is live").isNull()
        }
    }

    @Test
    fun `a rebuild can start once the lease of an abandoned run has expired and the abandoned run is failed`() {
        withHarness(directory, "expired") {
            entries.put("releases", jar(1))
            val abandoned = service("instance-a").start(RunTrigger.MANUAL)!!
            clock.advance(Duration.ofMinutes(3))

            val next = service("instance-b").start(RunTrigger.MANUAL)

            assertThat(next).`as`("start after the lease expired").isNotNull()
            assertThat(runs.get(abandoned.runId)?.status).`as`("status of the abandoned run").isEqualTo(RunStatus.FAILED)
        }
    }

    @Test
    fun `the lock is released when the run completes`() {
        withHarness(directory, "released") {
            entries.put("releases", jar(1))
            service("instance-a").rebuild(RunTrigger.MANUAL)

            assertThat(service("instance-b").start(RunTrigger.MANUAL)).`as`("start after a completed run").isNotNull()
        }
    }

    @Test
    fun `the status shows the cursor, the counters and the lease holder while a run is live`() {
        withHarness(directory, "status") {
            (1..4).forEach { entries.put("releases", jar(it)) }
            val seen = mutableListOf<IndexRunStatusProbe>()
            entries.onRequest = { number ->
                if (number == 2) {
                    val active = service("probe").status().activeRun!!
                    seen += IndexRunStatusProbe(active.cursorRepository, active.cursorPath, active.counters.processed, active.leaseOwner, active.kind)
                }
            }

            service("instance-a").rebuild(RunTrigger.MANUAL)

            val probe = seen.single()
            assertThat(probe.cursorRepository).`as`("cursor repository after one page").isEqualTo("releases")
            assertThat(probe.cursorPath).`as`("cursor path after one page").isEqualTo(jar(2))
            assertThat(probe.processed).`as`("entries read after one page").isEqualTo(2L)
            assertThat(probe.leaseOwner).`as`("lease holder").startsWith("instance-a")
            assertThat(probe.kind).`as`("kind").isEqualTo(RunKind.REBUILD)
        }
    }

    @Test
    fun `the status lists finished runs and no active run once the run completed`() {
        withHarness(directory, "history") {
            entries.put("releases", jar(1))
            service().rebuild(RunTrigger.MANUAL)

            val status = service().status()

            assertThat(status.activeRun).`as`("active run").isNull()
            assertThat(status.runs.single().status).`as`("last run").isEqualTo(RunStatus.COMPLETED)
            assertThat(status.lastFullRebuild).`as`("last full rebuild").isEqualTo(clock.instant())
        }
    }

    @Test
    fun `a cancelled run stops after the batch in flight and keeps what it wrote`() {
        withHarness(directory, "cancel") {
            (1..6).forEach { entries.put("releases", jar(it)) }
            val canceller = service()
            val handle = canceller.start(RunTrigger.MANUAL)!!
            entries.onRequest = { number -> if (number == 2) canceller.cancel(handle.runId) }

            val finished = canceller.execute(handle)

            assertThat(finished.status).`as`("status").isEqualTo(RunStatus.CANCELLED)
            assertThat(entries.requests).`as`("listing requests after the cancel").hasSize(2)
            assertThat(indexedPaths("releases")).`as`("rows written before the cancel").containsExactly(jar(1), jar(2))
            assertThat(service("instance-b").start(RunTrigger.MANUAL)).`as`("start after a cancelled run").isNotNull()
        }
    }

    private data class IndexRunStatusProbe(
        val cursorRepository: String?,
        val cursorPath: String?,
        val processed: Long,
        val leaseOwner: String?,
        val kind: RunKind,
    )

}
