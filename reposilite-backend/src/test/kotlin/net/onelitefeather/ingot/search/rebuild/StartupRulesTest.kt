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

import net.onelitefeather.ingot.search.runs.RunTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** The rules of artifact search spec, 3.7, as pure decisions on a fixed instant. */
internal class StartupRulesTest {

    private val now = Instant.parse("2026-10-17T10:00:00Z")

    @Test
    fun `an empty index without a completed rebuild starts a startup rebuild`() {
        assertThat(StartupRules.decide(rowCount = 0, lastFullRebuild = null, heartbeat = null, gapRebuild = true, now = now))
            .isEqualTo(RunTrigger.STARTUP)
    }

    @Test
    fun `an empty index starts a startup rebuild although the gap rule is off`() {
        assertThat(StartupRules.decide(rowCount = 0, lastFullRebuild = null, heartbeat = now, gapRebuild = false, now = now))
            .isEqualTo(RunTrigger.STARTUP)
    }

    @Test
    fun `an empty index after a completed rebuild starts nothing`() {
        assertThat(StartupRules.decide(rowCount = 0, lastFullRebuild = now.minus(Duration.ofDays(1)), heartbeat = now, gapRebuild = true, now = now))
            .isNull()
    }

    @Test
    fun `rows without a heartbeat start a gap rebuild`() {
        assertThat(StartupRules.decide(rowCount = 5, lastFullRebuild = null, heartbeat = null, gapRebuild = true, now = now))
            .isEqualTo(RunTrigger.GAP)
    }

    @Test
    fun `a heartbeat older than ten minutes starts a gap rebuild`() {
        val heartbeat = now.minus(Duration.ofMinutes(10)).minusSeconds(1)
        assertThat(StartupRules.decide(rowCount = 5, lastFullRebuild = now, heartbeat = heartbeat, gapRebuild = true, now = now))
            .isEqualTo(RunTrigger.GAP)
    }

    @Test
    fun `a heartbeat of exactly ten minutes ago is not a gap`() {
        val heartbeat = now.minus(Duration.ofMinutes(10))
        assertThat(StartupRules.decide(rowCount = 5, lastFullRebuild = now, heartbeat = heartbeat, gapRebuild = true, now = now))
            .isNull()
    }

    @Test
    fun `a recent heartbeat starts nothing`() {
        assertThat(StartupRules.decide(rowCount = 5, lastFullRebuild = now, heartbeat = now.minusSeconds(30), gapRebuild = true, now = now))
            .isNull()
    }

    @Test
    fun `a gap starts nothing when the gap rule is off`() {
        assertThat(StartupRules.decide(rowCount = 5, lastFullRebuild = now, heartbeat = null, gapRebuild = false, now = now))
            .isNull()
    }

}
