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

package net.onelitefeather.ingot.search.database

import net.onelitefeather.ingot.search.IndexState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** The state rules of `indexStatus` (artifact search spec, 4.8). Pure: the inputs are given, nothing is read. */
internal class IndexStateRulesTest {

    private val completed: Instant? = Instant.parse("2026-10-15T03:30:00Z")

    @Test
    fun `an empty index with no rebuild running is missing`() {
        assertThat(state(lastFullRebuild = null, rowsExist = false)).isEqualTo(IndexState.MISSING)
    }

    @Test
    fun `an index with rows and no completed rebuild is building`() {
        assertThat(state(lastFullRebuild = null, rowsExist = true)).isEqualTo(IndexState.BUILDING)
    }

    @Test
    fun `a rebuild running before its first completion is building`() {
        assertThat(state(lastFullRebuild = null, rowsExist = false, rebuildRunning = true)).isEqualTo(IndexState.BUILDING)
    }

    @Test
    fun `a completed rebuild without any problem is ready`() {
        assertThat(state(lastFullRebuild = completed)).isEqualTo(IndexState.READY)
    }

    @Test
    fun `a later rebuild does not turn a ready index back into building`() {
        assertThat(state(lastFullRebuild = completed, rebuildRunning = true)).isEqualTo(IndexState.READY)
    }

    @Test
    fun `a lag of exactly five minutes is still ready`() {
        assertThat(state(lastFullRebuild = completed, lagSeconds = 300)).isEqualTo(IndexState.READY)
    }

    @Test
    fun `a lag over five minutes is degraded`() {
        assertThat(state(lastFullRebuild = completed, lagSeconds = 301)).isEqualTo(IndexState.DEGRADED)
    }

    @Test
    fun `a dead letter is degraded`() {
        assertThat(state(lastFullRebuild = completed, deadLetters = 1)).isEqualTo(IndexState.DEGRADED)
    }

    @Test
    fun `a dropped enqueue is degraded`() {
        assertThat(state(lastFullRebuild = completed, droppedEnqueues = 1)).isEqualTo(IndexState.DEGRADED)
    }

    @Test
    fun `drift of exactly five percent is still ready`() {
        assertThat(state(lastFullRebuild = completed, drift = 0.05)).isEqualTo(IndexState.READY)
    }

    @Test
    fun `drift over five percent is degraded`() {
        assertThat(state(lastFullRebuild = completed, drift = 0.051)).isEqualTo(IndexState.DEGRADED)
    }

    @Test
    fun `a failed last rebuild is degraded even after an earlier completion`() {
        assertThat(state(lastFullRebuild = completed, lastRebuildFailed = true)).isEqualTo(IndexState.DEGRADED)
    }

    @Test
    fun `a failed first rebuild with rows is degraded`() {
        assertThat(state(lastFullRebuild = null, rowsExist = true, lastRebuildFailed = true)).isEqualTo(IndexState.DEGRADED)
    }

    private fun state(
        lastFullRebuild: Instant?,
        rowsExist: Boolean = true,
        rebuildRunning: Boolean = false,
        lastRebuildFailed: Boolean = false,
        lagSeconds: Long = 0,
        deadLetters: Long = 0,
        droppedEnqueues: Long = 0,
        drift: Double? = null,
    ): IndexState = indexStateOf(
        lastFullRebuild = lastFullRebuild,
        rowsExist = rowsExist,
        rebuildRunning = rebuildRunning,
        lastRebuildFailed = lastRebuildFailed,
        lagSeconds = lagSeconds,
        deadLetters = deadLetters,
        droppedEnqueues = droppedEnqueues,
        drift = drift,
    )

}
