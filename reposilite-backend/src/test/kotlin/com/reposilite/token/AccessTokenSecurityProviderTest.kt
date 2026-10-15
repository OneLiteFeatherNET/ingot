/*
 * Copyright (c) 2026 dzikoysk
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

package com.reposilite.token

import at.favre.lib.crypto.bcrypt.BCrypt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AccessTokenSecurityProviderTest {

    @Test
    fun `should match correct secret`() {
        val secret = "correct-password"
        val encoded = AccessTokenSecurityProvider.encodeSecret(secret)
        assertThat(AccessTokenSecurityProvider.matches(encoded, secret)).isTrue
    }

    @Test
    fun `should not match incorrect secret`() {
        val encoded = AccessTokenSecurityProvider.encodeSecret("correct-password")
        assertThat(AccessTokenSecurityProvider.matches(encoded, "wrong-password")).isFalse
    }

    @Test
    fun `accepts a bcrypt hash produced by another implementation`() {
        // The published OpenBSD vector for "U*U" at cost 5, which is how a token written by a Reposilite 2 install is hashed.
        val reference = "\$2a\$05\$CCCCCCCCCCCCCCCCCCCCC.E5YPO9kmyuRGyh0XouQYb4YMJKvyOeW"

        assertThat(AccessTokenSecurityProvider.matches(reference, "U*U")).isTrue
    }

    @Test
    fun `reads the 2a hashes of a reposilite 2 tokens dat file as valid bcrypt`() {
        // Hash taken verbatim from the migration plugin's tokens.dat fixture. Its plaintext is unknown, so the test asserts the
        // format is accepted: a wrong secret must be a plain mismatch, not a malformed hash.
        val legacyHash = "\$2a\$10\$emGPCUwsR650EJYNrjy/tucpi2rA0O2oYTT87OYQU9bSodGlNrI.q"

        val result = BCrypt.verifyer(BCrypt.Version.VERSION_2A).verify("not-the-secret".toCharArray(), legacyHash.toCharArray())

        assertThat(result.validFormat).describedAs("format of a 2.x hash").isTrue
        assertThat(result.verified).describedAs("wrong secret").isFalse
    }

    @Test
    fun `should return false for password exceeding bcrypt 72 byte limit`() {
        val encoded = AccessTokenSecurityProvider.encodeSecret("short")
        val longPassword = "a".repeat(256)
        assertThat(AccessTokenSecurityProvider.matches(encoded, longPassword)).isFalse
    }

}
