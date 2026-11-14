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

internal class TotpAlgorithmTest {

    /** The seed of RFC 6238, Appendix B: the ASCII string "12345678901234567890". */
    private val rfcSeed = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @Test
    fun `produces the RFC 6238 code for the first step`() {
        // Time 59 seconds is step 1. The RFC lists 8 digits (94287082); the last six are 287082.
        assertThat(TotpAlgorithm.code(rfcSeed, step = 1)).`as`("code of step 1").isEqualTo("287082")
    }

    @Test
    fun `keeps the leading zero of a six digit code`() {
        // Time 1111111109 is step 37037036; the RFC lists 07081804, so the six digit code starts with a zero.
        assertThat(TotpAlgorithm.code(rfcSeed, step = 37037036)).`as`("code of step 37037036").isEqualTo("081804")
    }

    @Test
    fun `accepts the code of the previous step`() {
        val nowSeconds = 3 * TotpAlgorithm.STEP_SECONDS
        val code = TotpAlgorithm.code(rfcSeed, step = 2)

        assertThat(TotpAlgorithm.matchingStep(rfcSeed, code, nowSeconds)).`as`("step of the previous code").isEqualTo(2L)
    }

    @Test
    fun `accepts the code of the next step`() {
        val nowSeconds = 3 * TotpAlgorithm.STEP_SECONDS
        val code = TotpAlgorithm.code(rfcSeed, step = 4)

        assertThat(TotpAlgorithm.matchingStep(rfcSeed, code, nowSeconds)).`as`("step of the next code").isEqualTo(4L)
    }

    @Test
    fun `refuses a code two steps away from the current time`() {
        val nowSeconds = 3 * TotpAlgorithm.STEP_SECONDS
        val code = TotpAlgorithm.code(rfcSeed, step = 5)

        assertThat(TotpAlgorithm.matchingStep(rfcSeed, code, nowSeconds)).`as`("a code outside the window").isNull()
    }

    @Test
    fun `refuses input that is not six digits`() {
        val nowSeconds = 3 * TotpAlgorithm.STEP_SECONDS

        assertThat(TotpAlgorithm.matchingStep(rfcSeed, "12345", nowSeconds)).`as`("five digits").isNull()
        assertThat(TotpAlgorithm.matchingStep(rfcSeed, "1234567", nowSeconds)).`as`("seven digits").isNull()
        assertThat(TotpAlgorithm.matchingStep(rfcSeed, "12a456", nowSeconds)).`as`("letters").isNull()
    }

    @Test
    fun `encodes the seed in base32 without padding`() {
        assertThat(TotpAlgorithm.base32("foobar".toByteArray(Charsets.US_ASCII)))
            .`as`("RFC 4648 base32 of foobar, without padding")
            .isEqualTo("MZXW6YTBOI")
    }

    @Test
    fun `builds an authenticator URI with the seed and the SHA-1 six digit parameters`() {
        val uri = TotpAlgorithm.otpauthUri(issuer = "Ingot", account = "alice", seed = "foobar".toByteArray(Charsets.US_ASCII))

        assertThat(uri)
            .`as`("otpauth URI")
            .startsWith("otpauth://totp/Ingot:alice?")
            .contains("secret=MZXW6YTBOI")
            .contains("issuer=Ingot")
            .contains("algorithm=SHA1&digits=6&period=30")
    }

}
