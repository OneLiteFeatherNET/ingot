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

package net.onelitefeather.ingot.readonly

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class ReadOnlySettingTest {

    @Test
    fun `should force read-only when the ingot variable is true`() {
        // when: the variable is read with the value true
        val setting = ReadOnlySetting.read(mapOf("INGOT_READ_ONLY" to "true"), systemProperty = null)

        // then: the environment forces the state on
        assertThat(setting.forced).`as`("forced by the environment").isTrue()
    }

    @Test
    fun `should leave the stored state in charge when the ingot variable is false`() {
        // when: the variable is read with the value false
        val setting = ReadOnlySetting.read(mapOf("INGOT_READ_ONLY" to "false"), systemProperty = null)

        // then: the environment does not force the state
        assertThat(setting.forced).`as`("forced by the environment").isFalse()
    }

    @Test
    fun `should leave the stored state in charge when nothing is set`() {
        // when: the settings are read from an empty environment and no system property
        val setting = ReadOnlySetting.read(emptyMap(), systemProperty = null)

        // then: the environment does not force the state, so the stored state decides
        assertThat(setting.forced).`as`("forced by the environment").isFalse()
    }

    @Test
    fun `should read the legacy reposilite variable as fallback`() {
        // when: only the legacy variable is set to true
        val setting = ReadOnlySetting.read(mapOf("REPOSILITE_READ_ONLY" to "true"), systemProperty = null)

        // then: the instance is forced read-only
        assertThat(setting.forced).`as`("forced by the legacy variable").isTrue()
    }

    @Test
    fun `should prefer the ingot variable over the legacy variable`() {
        // when: the ingot variable is false and the legacy variable is true
        val setting = ReadOnlySetting.read(
            mapOf("INGOT_READ_ONLY" to "false", "REPOSILITE_READ_ONLY" to "true"),
            systemProperty = null,
        )

        // then: the ingot variable decides and the environment does not force the state
        assertThat(setting.forced).`as`("forced by the environment").isFalse()
    }

    @Test
    fun `should read the system property when no variable is set`() {
        // when: no variable is set and the system property is true
        val setting = ReadOnlySetting.read(emptyMap(), systemProperty = "true")

        // then: the instance is forced read-only
        assertThat(setting.forced).`as`("forced by the system property").isTrue()
    }

    @Test
    fun `should refuse a value that is neither true nor false and name the variable`() {
        // when / then: a value that is not a boolean stops the start and names the variable
        assertThatThrownBy { ReadOnlySetting.read(mapOf("INGOT_READ_ONLY" to "yes"), systemProperty = null) }
            .`as`("invalid value")
            .hasMessageContaining("INGOT_READ_ONLY")
    }

}

internal class ReadOnlyStateTest {

    @Test
    fun `should report the environment as source when the variable forces the state`() {
        // when: the environment forces the state, even though nothing is stored
        val state = ReadOnlyState.resolve(ReadOnlySetting(forced = true), storedEnabled = false)

        // then: the instance is read-only and the source is the environment
        assertThat(state.enabled).`as`("enabled").isTrue()
        assertThat(state.source).`as`("source").isEqualTo(ReadOnlySource.ENVIRONMENT)
    }

    @Test
    fun `should let the stored state decide when the variable is unset`() {
        // when: the environment does not force the state and the stored state is on
        val state = ReadOnlyState.resolve(ReadOnlySetting(forced = false), storedEnabled = true)

        // then: the instance is read-only and the source is the store
        assertThat(state.enabled).`as`("enabled").isTrue()
        assertThat(state.source).`as`("source").isEqualTo(ReadOnlySource.STORED)
    }

    @Test
    fun `should default to off when nothing is stored`() {
        // when: neither the environment nor the stored state turns the state on
        val state = ReadOnlyState.resolve(ReadOnlySetting(forced = false), storedEnabled = false)

        // then: the instance is writable
        assertThat(state.enabled).`as`("enabled").isFalse()
    }

}
