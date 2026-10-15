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

package net.onelitefeather.ingot.identity.login

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SetupCodeTest {

    @Test
    fun `issues a code of twelve characters without the easily confused ones`() {
        val code = SetupCode().issue()

        assertThat(code).describedAs("the setup code length").hasSize(12)
        assertThat(code.all { it in SetupCode.ALPHABET }).describedAs("every character must come from the alphabet").isTrue()
        assertThat(code.any { it in "0O1IL" }).describedAs("0, O, 1, I and L are easily confused when copied from a log").isFalse()
    }

    @Test
    fun `keeps the same code until it is consumed`() {
        val setupCode = SetupCode()

        assertThat(setupCode.issue()).describedAs("issuing again must not replace the code the operator already read").isEqualTo(setupCode.issue())
    }

    @Test
    fun `accepts the issued code and rejects any other`() {
        val setupCode = SetupCode()
        val code = setupCode.issue()

        assertThat(setupCode.matches(code)).describedAs("the issued code must match").isTrue()
        assertThat(setupCode.matches("WRONGCODE234")).describedAs("another code must not match").isFalse()
    }

    @Test
    fun `accepts the code in any letter case and with surrounding spaces`() {
        val setupCode = SetupCode()
        val code = setupCode.issue()

        assertThat(setupCode.matches("  ${code.lowercase()} ")).describedAs("copying the code from a log must not depend on case or spaces").isTrue()
    }

    @Test
    fun `rejects every code once it is consumed`() {
        val setupCode = SetupCode()
        val code = setupCode.issue()

        setupCode.clear()

        assertThat(setupCode.matches(code)).describedAs("a consumed code must never match again").isFalse()
    }

    @Test
    fun `rejects a candidate when no code was issued`() {
        assertThat(SetupCode().matches("ANYTHING")).describedAs("without an issued code nothing matches").isFalse()
    }

    @Test
    fun `issues different codes to different instances`() {
        assertThat(SetupCode().issue()).describedAs("two instances must not share a code by construction").isNotEqualTo(SetupCode().issue())
    }

}
