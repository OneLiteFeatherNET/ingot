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

import java.security.SecureRandom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RecoveryCodesTest {

    @Test
    fun `generates ten codes per set`() {
        val codes = RecoveryCodes.generate(SecureRandom())

        assertThat(codes).`as`("codes of one set").hasSize(10)
    }

    @Test
    fun `generates codes that differ from each other`() {
        val codes = RecoveryCodes.generate(SecureRandom())

        assertThat(codes.toSet()).`as`("distinct codes of one set").hasSize(10)
    }

    @Test
    fun `shows each code in groups of four characters`() {
        val code = RecoveryCodes.generate(SecureRandom()).first()

        assertThat(code).`as`("display form").matches("[A-Z2-7]{4}(-[A-Z2-7]{4}){3}")
    }

    @Test
    fun `normalises input so that case, dashes and spaces do not matter`() {
        assertThat(RecoveryCodes.normalise(" abcd-efgh ijkl-mnop "))
            .`as`("normalised form")
            .isEqualTo("ABCDEFGHIJKLMNOP")
    }

    @Test
    fun `hashes the normalised form, so the display form and the typed form match`() {
        assertThat(RecoveryCodes.hash("abcd-efgh-ijkl-mnop"))
            .`as`("hash of the typed form")
            .isEqualTo(RecoveryCodes.hash("ABCDEFGHIJKLMNOP"))
    }

    @Test
    fun `stores a hash that is not the code itself`() {
        val hash = RecoveryCodes.hash("ABCDEFGHIJKLMNOP")

        assertThat(hash).`as`("stored hash").hasSize(64).doesNotContain("ABCDEFGHIJKLMNOP")
    }

}
