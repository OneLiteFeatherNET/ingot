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

package net.onelitefeather.ingot.identity.login.mfa

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FactorNameTest {

    @Test
    fun `a name is kept as typed, without its surrounding spaces`() {
        assertThat(validFactorName("  Work phone  ")).isEqualTo("Work phone")
    }

    @Test
    fun `a blank name is refused`() {
        assertThat(validFactorName("   ")).`as`("blank name").isNull()
        assertThat(validFactorName("")).`as`("empty name").isNull()
    }

    @Test
    fun `a missing name is refused`() {
        assertThat(validFactorName(null)).`as`("absent name").isNull()
    }

    @Test
    fun `a name of exactly the maximum length is accepted`() {
        val name = "a".repeat(MAX_FACTOR_NAME_LENGTH)

        assertThat(validFactorName(name)).isEqualTo(name)
    }

    @Test
    fun `a name one character over the maximum length is refused`() {
        assertThat(validFactorName("a".repeat(MAX_FACTOR_NAME_LENGTH + 1))).`as`("name over the limit").isNull()
    }

    @Test
    fun `a name with a line break is refused`() {
        assertThat(validFactorName("phone\nbackup")).`as`("name with a line break").isNull()
    }

    @Test
    fun `a name with a NUL character is refused`() {
        assertThat(validFactorName("phone\u0000")).`as`("name with a NUL character").isNull()
    }
}
