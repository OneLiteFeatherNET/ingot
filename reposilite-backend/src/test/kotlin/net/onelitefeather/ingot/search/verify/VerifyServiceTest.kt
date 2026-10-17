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

package net.onelitefeather.ingot.search.verify

import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.rebuild.RebuildHarness
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.jar
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.withHarness
import net.onelitefeather.ingot.search.runs.RunKind
import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.random.Random

/**
 * The sampled check of index rows against storage (artifact search spec, 3.8). The sample start is injected, the clock is
 * fixed and the entry source is a fake, so every run of a test sees the same sample.
 */
internal class VerifyServiceTest {

    @TempDir
    lateinit var directory: Path

    private val sameTime = Instant.parse("2026-10-01T00:00:00Z")

    /** A random source that always starts the sample at [start]. */
    private class FixedStart(private val start: Long) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextLong(from: Long, until: Long): Long = start
    }

    private fun RebuildHarness.verify(start: Long = 1, sampleSize: Int = 100) =
        verifyService(random = FixedStart(start), sampleSize = { sampleSize })

    @Test
    fun `a seeded sample produces the expected drift report`() {
        withHarness(directory, "report") {
            // given: six rows in storage terms: matching, missing, resized, touched, matching, and one the ledger calls a mirror copy
            (1..6).forEach { seedRow("releases", jar(it), size = 1, indexedAt = clock.instant()) }
            entries.put("releases", jar(1))
            entries.put("releases", jar(3), size = 99)
            entries.put("releases", jar(4), lastModified = Instant.parse("2026-10-05T00:00:00Z"))
            entries.put("releases", jar(5))
            entries.put("releases", jar(6))
            ledger.insertIfAbsent("releases", "com/example/app6/1.0", clock.instant(), "repo.example.org")
            // and: two rows of a repository that no longer exists, and a file in storage that has no row
            seedRow("gone", jar(1), size = 1, indexedAt = clock.instant())
            seedRow("gone", jar(2), size = 1, indexedAt = clock.instant())
            entries.put("releases", jar(7))

            // when
            val outcome = verify(start = 1).verify(RunTrigger.MANUAL)!!
            val report = outcome.report!!

            // then: each kind of drift is reported once, in sample order
            assertThat(report.missing.map { it.path }).`as`("rows whose file is missing").containsExactly(jar(2))
            assertThat(report.changed.map { it.path }).`as`("rows whose size or modification time differ").containsExactly(jar(3), jar(4))
            assertThat(report.reorigined.map { it.path }).`as`("rows whose origin differs from the ledger").containsExactly(jar(6))
            assertThat(report.removedRepositoryRows).`as`("rows of the removed repository").isEqualTo(2L)
            assertThat(report.sampled).`as`("rows checked").isEqualTo(6)
            assertThat(report.driftRatio).`as`("(missing + changed) / sampled").isEqualTo(3.0 / 6.0)

            // and: the repairs happened as the spec says
            assertThat(indexRow("releases", jar(2))).`as`("row of the missing file").isNull()
            assertThat(indexedPaths("gone")).`as`("rows of the removed repository").isEmpty()
            assertThat(indexRow("releases", jar(6))!![tables.artifact.origin]).`as`("origin after the fix").isEqualTo("MIRROR")
            assertThat(indexRow("releases", jar(6))!![tables.artifact.sourceHost]).`as`("host after the fix").isEqualTo("repo.example.org")
            assertThat(outbox.pending()).`as`("changed paths queued for the worker").isEqualTo(2L)
            assertThat(indexRow("releases", jar(3))!![tables.artifact.sizeBytes]).`as`("a changed row is left to the worker").isEqualTo(1L)
            assertThat(indexedPaths("releases")).`as`("verify never indexes a file that has no row").doesNotContain(jar(7))
        }
    }

    @Test
    fun `a verify run records its counters and its status`() {
        withHarness(directory, "counters") {
            (1..3).forEach { seedRow("releases", jar(it), size = 1, indexedAt = clock.instant()) }
            entries.put("releases", jar(1))
            entries.put("releases", jar(2), size = 5)

            val run = verify().verify(RunTrigger.SCHEDULED)!!.run

            assertThat(run.kind).`as`("kind").isEqualTo(RunKind.VERIFY)
            assertThat(run.status).`as`("status").isEqualTo(RunStatus.COMPLETED)
            assertThat(run.counters.processed).`as`("rows checked").isEqualTo(3L)
            assertThat(run.counters.deleted).`as`("rows deleted").isEqualTo(1L)
            assertThat(run.counters.upserted).`as`("rows fixed or queued for repair").isEqualTo(1L)
        }
    }

    @Test
    fun `the sample starts at the chosen id, takes consecutive ids and wraps around the table`() {
        withHarness(directory, "wrap") {
            (1..5).forEach { seedRow("releases", jar(it), size = 1, indexedAt = clock.instant()) }
            entries.addRepository("releases")

            val report = verify(start = 4, sampleSize = 3).verify(RunTrigger.MANUAL)!!.report!!

            assertThat(report.missing.map { it.path })
                .`as`("sampled rows (all missing from the empty storage), in sample order")
                .containsExactly(jar(4), jar(5), jar(1))
        }
    }

    @Test
    fun `a sample larger than the table checks each row once`() {
        withHarness(directory, "small-table") {
            (1..2).forEach { seedRow("releases", jar(it), size = 1, indexedAt = clock.instant()) }
            entries.addRepository("releases")

            val report = verify(start = 2, sampleSize = 50).verify(RunTrigger.MANUAL)!!.report!!

            assertThat(report.missing.map { it.path }).`as`("sampled rows").containsExactly(jar(2), jar(1))
        }
    }

    @Test
    fun `an unchanged index has no drift and is not degraded`() {
        withHarness(directory, "clean") {
            (1..3).forEach {
                seedRow("releases", jar(it), size = 1, indexedAt = clock.instant())
                entries.put("releases", jar(it))
            }

            val report = verify().verify(RunTrigger.MANUAL)!!.report!!

            assertThat(report.driftRatio).`as`("drift").isEqualTo(0.0)
            assertThat(report.degraded).`as`("degraded").isFalse()
        }
    }

    @Test
    fun `a drift of exactly five percent does not degrade the index`() {
        withHarness(directory, "degraded-edge") {
            // 1 of 20 rows is missing from storage
            (1..20).forEach { seedRow("releases", jar(it), size = 1, indexedAt = clock.instant()) }
            (1..19).forEach { entries.put("releases", jar(it)) }

            val report = verify().verify(RunTrigger.MANUAL)!!.report!!

            assertThat(report.sampled).`as`("rows checked").isEqualTo(20)
            assertThat(report.driftRatio).`as`("drift").isEqualTo(0.05)
            assertThat(report.degraded).`as`("exactly five percent").isFalse()
        }
    }

    @Test
    fun `a drift above five percent is degraded`() {
        withHarness(directory, "degraded-over") {
            (1..2).forEach { seedRow("releases", jar(it), size = 1, indexedAt = clock.instant()) }
            entries.put("releases", jar(1))

            val report = verify().verify(RunTrigger.MANUAL)!!.report!!

            assertThat(report.degraded).`as`("one of two rows missing").isTrue()
        }
    }

    @Test
    fun `a repository that cannot be listed is counted as an error and keeps its rows`() {
        withHarness(directory, "unreadable") {
            seedRow("broken", jar(1), size = 1, indexedAt = clock.instant())
            entries.addRepository("broken")
            entries.failing += "broken"

            val outcome = verify().verify(RunTrigger.MANUAL)!!

            assertThat(indexedPaths("broken")).`as`("rows of the unreadable repository").containsExactly(jar(1))
            assertThat(outcome.run.counters.errors).`as`("errors").isEqualTo(1L)
            assertThat(outcome.run.status).`as`("status").isEqualTo(RunStatus.FAILED)
            assertThat(outcome.report!!.sampled).`as`("rows read from the table").isEqualTo(1)
            assertThat(outcome.report!!.driftRatio).`as`("rows that could not be checked are no drift").isEqualTo(0.0)
        }
    }

    @Test
    fun `the report is stored as a summary in the search state`() {
        withHarness(directory, "summary") {
            seedRow("releases", jar(1), size = 1, indexedAt = clock.instant())
            entries.addRepository("releases")

            val outcome = verify().verify(RunTrigger.MANUAL)!!

            val summary = state.get(SearchStateKey.VERIFY_LAST_REPORT)!!
            assertThat(summary).`as`("stored summary").contains("\"runId\":${outcome.run.id}").contains("\"missing\":1").contains("\"sampled\":1")
        }
    }

    @Test
    fun `a verify run is refused while a rebuild holds its lease`() {
        withHarness(directory, "refused") {
            entries.put("releases", jar(1))
            service().start(RunTrigger.MANUAL)!!

            assertThat(verify().start(RunTrigger.MANUAL)).`as`("verify while a rebuild is live").isNull()
        }
    }

    @Test
    fun `a rebuild is refused while a verify run holds its lease`() {
        withHarness(directory, "refused-rebuild") {
            entries.put("releases", jar(1))
            verify().start(RunTrigger.MANUAL)!!

            assertThat(service().start(RunTrigger.MANUAL)).`as`("rebuild while a verify run is live").isNull()
        }
    }

    @Test
    fun `a second manual verify run is refused while the first is live`() {
        withHarness(directory, "refused-twice") {
            verify().start(RunTrigger.MANUAL)!!

            assertThat(verify().start(RunTrigger.MANUAL)).`as`("second verify run").isNull()
        }
    }

}
