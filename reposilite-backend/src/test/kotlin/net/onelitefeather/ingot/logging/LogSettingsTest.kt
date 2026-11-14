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

package net.onelitefeather.ingot.logging

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The logging variables are read as a pure function of an environment map, so no test changes the environment of its process.
 */
internal class LogSettingsTest {

    @Test
    fun `should fall back to the legacy file switch when the ingot one is unset`() {
        assertThat(LogSettings.read(mapOf("REPOSILITE_LOG_FILE" to "false")).file)
            .describedAs("file writer with REPOSILITE_LOG_FILE=false").isFalse()
    }

    @Test
    fun `should treat a blank ingot variable as unset`() {
        // when: the INGOT_ name is blank and the legacy name is set
        val settings = LogSettings.read(mapOf("INGOT_LOG_FILE" to "  ", "REPOSILITE_LOG_FILE" to "false"))

        // then: the legacy name applies
        assertThat(settings.file).describedAs("file writer with a blank INGOT_LOG_FILE").isFalse()
    }

    @Test
    fun `should use the defaults when no logging variable is set`() {
        // when: the environment has no logging variable
        val settings = LogSettings.read(emptyMap())

        // then: the server keeps its current behaviour
        assertThat(settings).describedAs("settings of an empty environment")
            .isEqualTo(LogSettings(file = true))
    }

    @Test
    fun `should refuse a file switch that is neither true nor false and name the variable`() {
        assertThatThrownBy { LogSettings.read(mapOf("INGOT_LOG_FILE" to "no")) }
            .describedAs("refusal of a file switch that is not a boolean")
            .isInstanceOf(LogSettingsRefusal::class.java)
            .hasMessageContaining("INGOT_LOG_FILE")
    }

}
