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

@file:OptIn(net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.rebuild

import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.FakeArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.database.DatabaseSearchBackend
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.jar
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.withHarness
import net.onelitefeather.ingot.search.runs.RunHandle
import net.onelitefeather.ingot.search.runs.RunTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * What the rebuild relies on from its backend (search backend seam, D5 and D8). The database backend is wrapped by a
 * decorator that stops the run right after a page is applied, so the test sees what the core commits for that page.
 */
internal class RebuildBackendTest {

    @TempDir
    lateinit var directory: Path

    /** Delegates to [delegate] and runs [afterApply] once a batch is applied. */
    private class StopAfterApply(
        private val delegate: ArtifactSearchBackend,
        override val joinsDatabaseTransaction: Boolean,
        private val afterApply: () -> Unit,
    ) : ArtifactSearchBackend by delegate {

        override fun apply(actions: List<IndexAction>) {
            delegate.apply(actions)
            afterApply()
        }
    }

    @Test
    fun `a page of a backend that joins the transaction is rolled back with its cursor when the run stops`() {
        withHarness(directory, "joined") {
            (1..3).forEach { entries.put("releases", jar(it)) }
            lateinit var handle: RunHandle
            val backend = StopAfterApply(DatabaseSearchBackend(database, clock), joinsDatabaseTransaction = true) {
                runs.cancel(handle.runId)
            }
            val service = service(backend = backend)
            handle = checkNotNull(service.start(RunTrigger.MANUAL)) { "the run starts" }

            service.execute(handle)

            assertThat(indexedPaths("releases")).`as`("rows of the page that was stopped").isEmpty()
            assertThat(runs.get(handle.runId)?.cursorPath).`as`("cursor of the page that was stopped").isNull()
        }
    }

    @Test
    fun `a page of a backend that does not join the transaction stays applied when the run stops before its cursor`() {
        withHarness(directory, "separate") {
            (1..3).forEach { entries.put("releases", jar(it)) }
            lateinit var handle: RunHandle
            val backend = StopAfterApply(DatabaseSearchBackend(database, clock), joinsDatabaseTransaction = false) {
                runs.cancel(handle.runId)
            }
            val service = service(backend = backend)
            handle = checkNotNull(service.start(RunTrigger.MANUAL)) { "the run starts" }

            service.execute(handle)

            assertThat(indexedPaths("releases"))
                .`as`("rows applied before the cursor was refused")
                .containsExactlyInAnyOrder(jar(1), jar(2))
            assertThat(runs.get(handle.runId)?.cursorPath).`as`("cursor of the page that was stopped").isNull()
        }
    }

    @Test
    fun `the startup rules count the rows of the backend they were given`() {
        withHarness(directory, "startup") {
            val backend = FakeArtifactSearchBackend()
            val parsed = ArtifactPathParser.parse("releases", jar(1)) as ArtifactPathResult.Row
            backend.apply(
                listOf(IndexAction.Upsert(IndexRow(parsed.file, Provenance.HOSTED, 1L, 1L, null, null, clock.millis()))),
            )

            val trigger = service(backend = backend).startupTrigger(gapRebuild = true)

            assertThat(trigger).`as`("trigger for a backend with one row and no heartbeat").isEqualTo(RunTrigger.GAP)
        }
    }

}
