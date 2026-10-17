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

import net.onelitefeather.ingot.search.outbox.OutboxOp
import net.onelitefeather.ingot.search.outbox.OutboxPolicy
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.jar
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.withHarness
import net.onelitefeather.ingot.search.runs.RunTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

/** A path with a pending outbox entry is left to the worker, which reads fresher information (artifact search spec, 3.6). */
internal class RebuildPendingOutboxTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `the rebuild does not overwrite the row of a path with a pending outbox entry`() {
        withHarness(directory, "pending-row") {
            // given: a row from before, a newer file in storage, and a worker that has not processed the change yet
            seedRow("releases", jar(1), size = 100, indexedAt = clock.instant())
            entries.put("releases", jar(1), size = 500)
            outbox.enqueue("releases", jar(1), OutboxOp.CHANGED_PATH)
            clock.advance(Duration.ofMinutes(1))

            // when
            service().rebuild(RunTrigger.MANUAL)

            // then: the rebuild left the row to the worker
            assertThat(indexRow("releases", jar(1))!![tables.artifact.sizeBytes])
                .`as`("size of the row with a pending change")
                .isEqualTo(100L)
        }
    }

    @Test
    fun `the rebuild writes no row for a new path with a pending outbox entry`() {
        withHarness(directory, "pending-new") {
            entries.put("releases", jar(1))
            entries.put("releases", jar(2))
            outbox.enqueue("releases", jar(1), OutboxOp.CHANGED_PATH)

            service().rebuild(RunTrigger.MANUAL)

            assertThat(indexedPaths("releases"))
                .`as`("only the path without a pending entry is written")
                .containsExactly(jar(2))
        }
    }

    @Test
    fun `the row of a path with a pending outbox entry survives the stale cleanup`() {
        withHarness(directory, "pending-stale") {
            seedRow("releases", jar(1), size = 100, indexedAt = clock.instant())
            entries.put("releases", jar(1), size = 500)
            outbox.enqueue("releases", jar(1), OutboxOp.CHANGED_PATH)
            clock.advance(Duration.ofMinutes(1))

            service().rebuild(RunTrigger.MANUAL)

            assertThat(indexRow("releases", jar(1))).`as`("row of a file that still exists").isNotNull()
        }
    }

    @Test
    fun `a dead letter does not hold its path back from the rebuild`() {
        withHarness(directory, "dead-letter") {
            entries.put("releases", jar(1), size = 500)
            outbox.enqueue("releases", jar(1), OutboxOp.CHANGED_PATH)
            repeat(OutboxPolicy.MAX_ATTEMPTS) {
                outbox.claim("worker", 10).forEach { outbox.fail("worker", it.id, "storage unavailable") }
                clock.advance(Duration.ofMinutes(11))
            }
            assertThat(outbox.deadLetters()).`as`("dead letters before the rebuild").isEqualTo(1L)

            service().rebuild(RunTrigger.MANUAL)

            assertThat(indexRow("releases", jar(1))!![tables.artifact.sizeBytes])
                .`as`("the rebuild repairs the row the worker gave up on")
                .isEqualTo(500L)
        }
    }

}
