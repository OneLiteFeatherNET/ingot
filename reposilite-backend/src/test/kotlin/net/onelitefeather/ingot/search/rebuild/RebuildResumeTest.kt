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

import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.jar
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.withHarness
import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunTrigger
import net.onelitefeather.ingot.search.runs.SimulatedCrash
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

/** A run that dies mid-repository is continued from its persisted cursor by another instance (artifact search spec, 3.6). */
internal class RebuildResumeTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `a new instance resumes a crashed run from its cursor without repeating or skipping an entry`() {
        withHarness(directory, "resume") {
            // given: six files that are listed two per page, and a first instance that dies while it asks for the third page
            (1..6).forEach { entries.put("releases", jar(it)) }
            entries.crashOnRequest = 3
            assertThatThrownBy { service("instance-a").rebuild(RunTrigger.MANUAL) }
                .`as`("the crash leaves the first instance")
                .isInstanceOf(SimulatedCrash::class.java)
            val firstPass = clock.instant()

            val crashed = runs.recent(1).single()
            assertThat(crashed.status).`as`("status of the crashed run").isEqualTo(RunStatus.RUNNING)
            assertThat(crashed.cursorPath).`as`("cursor after two committed pages").isEqualTo(jar(4))
            assertThat(crashed.counters.processed).`as`("entries read before the crash").isEqualTo(4)

            // when: the lease has run out and a second instance resumes
            entries.crashOnRequest = null
            entries.requests.clear()
            clock.advance(Duration.ofMinutes(3))
            val resumedAt = clock.instant()
            val resumed = service("instance-b").resume()

            // then: it continues after the cursor and asks for nothing earlier
            assertThat(entries.requests.map { it.after }).`as`("cursors the second instance asked for").containsExactly(jar(4))
            assertThat(resumed?.status).`as`("status of the resumed run").isEqualTo(RunStatus.COMPLETED)
            assertThat(resumed?.id).`as`("the same run continues").isEqualTo(crashed.id)

            // and: every file has a row, the first four written once by the first instance, the last two by the second
            assertThat(indexedPaths("releases")).`as`("indexed paths").containsExactlyInAnyOrderElementsOf((1..6).map { jar(it) })
            (1..4).forEach {
                assertThat(indexRow("releases", jar(it))!![tables.artifact.indexedAt])
                    .`as`("row ${jar(it)} is not written again")
                    .isEqualTo(firstPass.toEpochMilli())
            }
            (5..6).forEach {
                assertThat(indexRow("releases", jar(it))!![tables.artifact.indexedAt])
                    .`as`("row ${jar(it)} is written by the resumed run")
                    .isEqualTo(resumedAt.toEpochMilli())
            }
            assertThat(resumed?.counters?.processed).`as`("entries read over both passes").isEqualTo(6)
            assertThat(resumed?.counters?.upserted).`as`("rows written over both passes").isEqualTo(6)
        }
    }

    @Test
    fun `a run whose lease is still live is not taken over`() {
        withHarness(directory, "live") {
            (1..6).forEach { entries.put("releases", jar(it)) }
            entries.crashOnRequest = 3
            assertThatThrownBy { service("instance-a").rebuild(RunTrigger.MANUAL) }.isInstanceOf(SimulatedCrash::class.java)

            clock.advance(Duration.ofSeconds(30))

            assertThat(service("instance-b").resume()).`as`("takeover while the first lease is live").isNull()
        }
    }

}
