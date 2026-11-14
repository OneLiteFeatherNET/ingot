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
package net.onelitefeather.ingot.identity.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The switch of the external reference guard. Pure reads of a given environment: no test touches the real one. */
internal class IdpGuardModeTest {

    @Test
    fun `the guard enforces when no variable is set`() {
        assertThat(IdpGuardMode.read(environment = emptyMap())).describedAs("the default mode").isEqualTo(IdpGuardMode.ENFORCE)
    }

    @Test
    fun `a blank variable counts as unset`() {
        assertThat(IdpGuardMode.read(environment = mapOf("INGOT_IDENTITY_IDP_GUARD" to "  "))).isEqualTo(IdpGuardMode.ENFORCE)
    }

    @Test
    fun `the warn value is read from the ingot variable`() {
        assertThat(IdpGuardMode.read(environment = mapOf("INGOT_IDENTITY_IDP_GUARD" to "warn"))).isEqualTo(IdpGuardMode.WARN)
    }

    @Test
    fun `the legacy variable is read when the ingot variable is absent`() {
        assertThat(IdpGuardMode.read(environment = mapOf("REPOSILITE_IDENTITY_IDP_GUARD" to "warn"))).isEqualTo(IdpGuardMode.WARN)
    }

    @Test
    fun `the ingot variable wins over the legacy variable`() {
        val environment = mapOf("INGOT_IDENTITY_IDP_GUARD" to "enforce", "REPOSILITE_IDENTITY_IDP_GUARD" to "warn")

        assertThat(IdpGuardMode.read(environment = environment)).isEqualTo(IdpGuardMode.ENFORCE)
    }

    @Test
    fun `the value is read regardless of its letter case`() {
        assertThat(IdpGuardMode.read(environment = mapOf("INGOT_IDENTITY_IDP_GUARD" to "OFF"))).isEqualTo(IdpGuardMode.OFF)
    }

    @Test
    fun `an unknown value stops the start and names the variable`() {
        val failure = assertThrows<IllegalArgumentException> {
            IdpGuardMode.read(environment = mapOf("INGOT_IDENTITY_IDP_GUARD" to "strict"))
        }

        assertThat(failure.message).describedAs("the message").contains("INGOT_IDENTITY_IDP_GUARD").contains("enforce")
    }
}
