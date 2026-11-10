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

package com.reposilite.configuration.shared

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class SharedConfigurationReloadTest {

    @Test
    fun `reload is off when no variable is set`() {
        // given: an environment without the switch
        val environment = emptyMap<String, String>()

        // when: the switch is read
        val enabled = SharedConfigurationReload.read(environment)

        // then: the reload stays off, as it always was
        assertThat(enabled).describedAs("reload without any variable").isFalse()
    }

    @Test
    fun `reload is on for the value true in any letter case`() {
        // given: the value written with mixed case
        val environment = mapOf("INGOT_SHARED_CONFIGURATION_RELOAD" to "TrUe")

        // when: the switch is read
        val enabled = SharedConfigurationReload.read(environment)

        // then: the reload is on
        assertThat(enabled).describedAs("reload for TrUe").isTrue()
    }

    @Test
    fun `reload is off for the value false in upper case`() {
        // given: the value written in upper case
        val environment = mapOf("INGOT_SHARED_CONFIGURATION_RELOAD" to "FALSE")

        // when: the switch is read
        val enabled = SharedConfigurationReload.read(environment)

        // then: the reload is off
        assertThat(enabled).describedAs("reload for FALSE").isFalse()
    }

    @Test
    fun `the INGOT variable wins over the legacy variable when both are set to false and true`() {
        // given: the new variable disables the reload and the legacy one enables it
        val environment = mapOf(
            "INGOT_SHARED_CONFIGURATION_RELOAD" to "false",
            "REPOSILITE_SHARED_CONFIGURATION_RELOAD" to "true",
        )

        // when: the switch is read
        val enabled = SharedConfigurationReload.read(environment)

        // then: the new variable decides
        assertThat(enabled).describedAs("INGOT variable precedence over the legacy one").isFalse()
    }

    @Test
    fun `the INGOT variable wins over the legacy variable when both are set to true and false`() {
        // given: the new variable enables the reload and the legacy one disables it
        val environment = mapOf(
            "INGOT_SHARED_CONFIGURATION_RELOAD" to "true",
            "REPOSILITE_SHARED_CONFIGURATION_RELOAD" to "false",
        )

        // when: the switch is read
        val enabled = SharedConfigurationReload.read(environment)

        // then: the new variable decides
        assertThat(enabled).describedAs("INGOT variable precedence over the legacy one").isTrue()
    }

    @Test
    fun `the legacy variable alone turns the reload on`() {
        // given: only the legacy spelling is set
        val environment = mapOf("REPOSILITE_SHARED_CONFIGURATION_RELOAD" to "true")

        // when: the switch is read
        val enabled = SharedConfigurationReload.read(environment)

        // then: the reload is on
        assertThat(enabled).describedAs("reload from the legacy variable").isTrue()
    }

    @Test
    fun `an unknown value is refused with a message that names the INGOT variable`() {
        // given: a value that is neither true nor false
        val environment = mapOf("INGOT_SHARED_CONFIGURATION_RELOAD" to "yes")

        // when, then: reading the switch stops with a message that names the variable
        assertThatThrownBy { SharedConfigurationReload.read(environment) }
            .describedAs("unknown value for the switch")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_SHARED_CONFIGURATION_RELOAD")
    }
}
