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

package com.reposilite.audit.decision

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DecisionLogSettingsTest {

    @Test
    fun `the mode is off when no variable is set`() {
        assertThat(DecisionLogSettings.read(emptyMap()).mode).isEqualTo(DecisionLogMode.OFF)
    }

    @Test
    fun `a blank INGOT variable without a REPOSILITE variable gives off`() {
        assertThat(DecisionLogSettings.read(mapOf("INGOT_DECISION_LOG" to "   ")).mode).isEqualTo(DecisionLogMode.OFF)
    }

    @Test
    fun `a blank REPOSILITE variable gives off`() {
        assertThat(DecisionLogSettings.read(mapOf("REPOSILITE_DECISION_LOG" to "")).mode).isEqualTo(DecisionLogMode.OFF)
    }

    @Test
    fun `the async mode is selected by the INGOT variable`() {
        assertThat(DecisionLogSettings.read(mapOf("INGOT_DECISION_LOG" to "async")).mode).isEqualTo(DecisionLogMode.ASYNC)
    }

    @Test
    fun `the INGOT variable selects the mode`() {
        assertThat(DecisionLogSettings.read(mapOf("INGOT_DECISION_LOG" to "off")).mode).isEqualTo(DecisionLogMode.OFF)
    }

    @Test
    fun `the REPOSILITE variable is the fallback`() {
        assertThat(DecisionLogSettings.read(mapOf("REPOSILITE_DECISION_LOG" to "sync")).mode).isEqualTo(DecisionLogMode.SYNC)
    }

    @Test
    fun `the INGOT variable wins over the REPOSILITE variable`() {
        val environment = mapOf("INGOT_DECISION_LOG" to "off", "REPOSILITE_DECISION_LOG" to "sync")

        assertThat(DecisionLogSettings.read(environment).mode).isEqualTo(DecisionLogMode.OFF)
    }

    @Test
    fun `a blank INGOT variable falls back to the REPOSILITE variable`() {
        val environment = mapOf("INGOT_DECISION_LOG" to "  ", "REPOSILITE_DECISION_LOG" to "sync")

        assertThat(DecisionLogSettings.read(environment).mode).isEqualTo(DecisionLogMode.SYNC)
    }

    @Test
    fun `the values are read without regard to case`() {
        assertThat(DecisionLogSettings.read(mapOf("INGOT_DECISION_LOG" to "SYNC")).mode).isEqualTo(DecisionLogMode.SYNC)
    }

    @Test
    fun `an unknown value gives off`() {
        assertThat(DecisionLogSettings.read(mapOf("INGOT_DECISION_LOG" to "sometimes")).mode).isEqualTo(DecisionLogMode.OFF)
    }

}
