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

package net.onelitefeather.ingot.web

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration

internal class ShutdownSettingsTest {

    @Test
    fun `should default to no drain and no delay when nothing is set`() {
        // when: the settings are read from an empty environment
        val settings = ShutdownSettings.read(emptyMap())

        // then: both durations are zero, which keeps the stop sequence of earlier versions
        assertThat(settings.timeout).`as`("drain timeout").isEqualTo(Duration.ZERO)
        assertThat(settings.delay).`as`("pre-stop delay").isEqualTo(Duration.ZERO)
    }

    @Test
    fun `should read the ingot timeout in seconds`() {
        // when: the timeout is set with the ingot name
        val settings = ShutdownSettings.read(mapOf("INGOT_SHUTDOWN_TIMEOUT" to "10"))

        // then: the drain timeout is ten seconds
        assertThat(settings.timeout).`as`("drain timeout").isEqualTo(Duration.ofSeconds(10))
    }

    @Test
    fun `should read the ingot delay in seconds`() {
        // when: the delay is set with the ingot name
        val settings = ShutdownSettings.read(mapOf("INGOT_SHUTDOWN_DELAY" to "5"))

        // then: the pre-stop delay is five seconds and the timeout stays at zero
        assertThat(settings.delay).`as`("pre-stop delay").isEqualTo(Duration.ofSeconds(5))
        assertThat(settings.timeout).`as`("drain timeout").isEqualTo(Duration.ZERO)
    }

    @Test
    fun `should fall back to the reposilite timeout name`() {
        // when: only the legacy name is set
        val settings = ShutdownSettings.read(mapOf("REPOSILITE_SHUTDOWN_TIMEOUT" to "10"))

        // then: the legacy value is used
        assertThat(settings.timeout).`as`("drain timeout from the fallback").isEqualTo(Duration.ofSeconds(10))
    }

    @Test
    fun `should prefer the ingot name over the reposilite name`() {
        // when: both names are set with different values
        val settings = ShutdownSettings.read(
            mapOf(
                "INGOT_SHUTDOWN_TIMEOUT" to "10",
                "REPOSILITE_SHUTDOWN_TIMEOUT" to "30",
            )
        )

        // then: the ingot value wins
        assertThat(settings.timeout).`as`("drain timeout").isEqualTo(Duration.ofSeconds(10))
    }

    @Test
    fun `should read the legacy delay name when the ingot name is blank`() {
        // when: the ingot name is blank and the legacy name holds a value
        val settings = ShutdownSettings.read(
            mapOf(
                "INGOT_SHUTDOWN_DELAY" to "  ",
                "REPOSILITE_SHUTDOWN_DELAY" to "7",
            )
        )

        // then: a blank value counts as unset and the legacy value is used
        assertThat(settings.delay).`as`("pre-stop delay").isEqualTo(Duration.ofSeconds(7))
    }

    @ParameterizedTest
    @ValueSource(strings = ["soon", "-1", "1.5"])
    fun `should refuse a timeout that is not a non-negative whole number and name the variable`(value: String) {
        // when: the timeout is read with a value that is not a whole number of seconds
        // then: the start is refused, the message names the variable and does not repeat the value
        assertThatThrownBy { ShutdownSettings.read(mapOf("INGOT_SHUTDOWN_TIMEOUT" to value)) }
            .`as`("refusal of '$value'")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_SHUTDOWN_TIMEOUT")
            .hasMessageNotContaining(value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["soon", "-1", "1.5"])
    fun `should refuse a delay that is not a non-negative whole number and name the variable`(value: String) {
        // when: the delay is read with a value that is not a whole number of seconds
        // then: the start is refused, the message names the variable and does not repeat the value
        assertThatThrownBy { ShutdownSettings.read(mapOf("INGOT_SHUTDOWN_DELAY" to value)) }
            .`as`("refusal of '$value'")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_SHUTDOWN_DELAY")
            .hasMessageNotContaining(value)
    }

    @Test
    fun `should name the legacy variable when the legacy value is refused`() {
        // when: only the legacy timeout name is set to an invalid value
        // then: the refusal names that legacy variable
        assertThatThrownBy { ShutdownSettings.read(mapOf("REPOSILITE_SHUTDOWN_TIMEOUT" to "soon")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("REPOSILITE_SHUTDOWN_TIMEOUT")
    }

    @Test
    fun `should not read the reposilite name when the ingot name is set`() {
        // when: the ingot name is valid and the legacy name is invalid
        val settings = ShutdownSettings.read(
            mapOf(
                "INGOT_SHUTDOWN_TIMEOUT" to "10",
                "REPOSILITE_SHUTDOWN_TIMEOUT" to "soon",
            )
        )

        // then: the legacy value is ignored and the start is not refused
        assertThat(settings.timeout).`as`("drain timeout").isEqualTo(Duration.ofSeconds(10))
    }

}
