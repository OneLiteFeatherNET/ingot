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

import com.reposilite.journalist.Channel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The logging variables are read as a pure function of an environment map, so no test changes the environment of its process.
 */
internal class LogSettingsTest {

    @Test
    fun `should use the ingot variable when both spellings are set`() {
        // when: both spellings name a format
        val settings = LogSettings.read(mapOf("INGOT_LOG_FORMAT" to "json", "REPOSILITE_LOG_FORMAT" to "text"))

        // then: the INGOT_ name wins
        assertThat(settings.format).describedAs("format when both spellings are set").isEqualTo(LogFormat.JSON)
    }

    @Test
    fun `should fall back to the legacy file switch when the ingot one is unset`() {
        assertThat(LogSettings.read(mapOf("REPOSILITE_LOG_FILE" to "false")).file)
            .describedAs("file writer with REPOSILITE_LOG_FILE=false").isFalse()
    }

    @Test
    fun `should fall back to the legacy format when the ingot one is unset`() {
        assertThat(LogSettings.read(mapOf("REPOSILITE_LOG_FORMAT" to "json")).format)
            .describedAs("format with REPOSILITE_LOG_FORMAT=json").isEqualTo(LogFormat.JSON)
    }

    @Test
    fun `should fall back to the legacy threshold when the ingot one is unset`() {
        assertThat(LogSettings.read(mapOf("REPOSILITE_LOG_LEVEL" to "debug")).level)
            .describedAs("threshold with REPOSILITE_LOG_LEVEL=debug").isEqualTo(Channel.DEBUG)
    }

    @Test
    fun `should fall back to the legacy package levels when the ingot one is unset`() {
        assertThat(LogSettings.read(mapOf("REPOSILITE_LOG_LEVELS" to "com.reposilite.web=warn")).packageLevels)
            .describedAs("package levels with REPOSILITE_LOG_LEVELS").containsEntry("com.reposilite.web", "warn")
    }

    @Test
    fun `should treat a blank ingot variable as unset`() {
        // when: the INGOT_ name is blank and the legacy name is set
        val settings = LogSettings.read(mapOf("INGOT_LOG_FORMAT" to "  ", "REPOSILITE_LOG_FORMAT" to "json"))

        // then: the legacy name applies
        assertThat(settings.format).describedAs("format with a blank INGOT_LOG_FORMAT").isEqualTo(LogFormat.JSON)
    }

    @Test
    fun `should use the defaults when no logging variable is set`() {
        // when: the environment has no logging variable
        val settings = LogSettings.read(emptyMap())

        // then: the server keeps its current behaviour
        assertThat(settings).describedAs("settings of an empty environment")
            .isEqualTo(LogSettings(file = true, format = LogFormat.TEXT, level = null, packageLevels = emptyMap()))
    }

    @Test
    fun `should refuse an unknown format and name the variable and the accepted values`() {
        assertThatThrownBy { LogSettings.read(mapOf("INGOT_LOG_FORMAT" to "xml")) }
            .describedAs("refusal of an unknown format")
            .isInstanceOf(LogSettingsRefusal::class.java)
            .hasMessageContaining("INGOT_LOG_FORMAT")
            .hasMessageContaining("text")
            .hasMessageContaining("json")
    }

    @Test
    fun `should refuse an unknown threshold and name the variable`() {
        assertThatThrownBy { LogSettings.read(mapOf("INGOT_LOG_LEVEL" to "verbose")) }
            .describedAs("refusal of an unknown threshold")
            .isInstanceOf(LogSettingsRefusal::class.java)
            .hasMessageContaining("INGOT_LOG_LEVEL")
    }

    @Test
    fun `should refuse a file switch that is neither true nor false and name the variable`() {
        assertThatThrownBy { LogSettings.read(mapOf("INGOT_LOG_FILE" to "no")) }
            .describedAs("refusal of a file switch that is not a boolean")
            .isInstanceOf(LogSettingsRefusal::class.java)
            .hasMessageContaining("INGOT_LOG_FILE")
    }

    @Test
    fun `should refuse a package level entry with an unknown level and name the variable and the entry`() {
        assertThatThrownBy { LogSettings.read(mapOf("INGOT_LOG_LEVELS" to "com.reposilite.web=loud")) }
            .describedAs("refusal of an unknown package level")
            .isInstanceOf(LogSettingsRefusal::class.java)
            .hasMessageContaining("INGOT_LOG_LEVELS")
            .hasMessageContaining("com.reposilite.web=loud")
    }

    @Test
    fun `should read every package level entry of the list`() {
        // when: two entries are listed with spaces around them
        val settings = LogSettings.read(mapOf("INGOT_LOG_LEVELS" to "com.reposilite.web=warn, org.eclipse.jetty=error"))

        // then: both entries are applied
        assertThat(settings.packageLevels).describedAs("package levels")
            .containsEntry("com.reposilite.web", "warn")
            .containsEntry("org.eclipse.jetty", "error")
    }

    @Test
    fun `should use the command line level over the variable`() {
        // given: the variable names DEBUG
        val settings = LogSettings.read(mapOf("INGOT_LOG_LEVEL" to "DEBUG"))

        // when: the flag names WARN
        val threshold = settings.consoleThreshold(commandLineLevel = "WARN")

        // then: the flag wins
        assertThat(threshold).describedAs("threshold with --level=WARN and INGOT_LOG_LEVEL=DEBUG").isEqualTo(Channel.WARN)
    }

    @Test
    fun `should use the variable when no command line level is given`() {
        val threshold = LogSettings.read(mapOf("INGOT_LOG_LEVEL" to "DEBUG")).consoleThreshold(commandLineLevel = null)

        assertThat(threshold).describedAs("threshold with INGOT_LOG_LEVEL=DEBUG and no flag").isEqualTo(Channel.DEBUG)
    }

    @Test
    fun `should use info when neither the command line nor the variable names a level`() {
        val threshold = LogSettings.read(emptyMap()).consoleThreshold(commandLineLevel = null)

        assertThat(threshold).describedAs("threshold with neither flag nor variable").isEqualTo(Channel.INFO)
    }

}
