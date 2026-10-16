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

internal class PasswordHasherTest {

    private val cheap = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1))

    @Test
    fun `encodes an argon2id hash together with its cost parameters`() {
        val encoded = cheap.hash("correct horse battery")

        assertThat(encoded)
            .describedAs("stored hash should be a PHC string of argon2id version 19 with the configured cost")
            .startsWith("\$argon2id\$v=19\$m=8,t=1,p=1\$")
    }

    @Test
    fun `accepts the password it was hashed from`() {
        val encoded = cheap.hash("correct horse battery")

        assertThat(cheap.matches("correct horse battery", encoded)).describedAs("the original password must match").isTrue()
    }

    @Test
    fun `rejects any other password`() {
        val encoded = cheap.hash("correct horse battery")

        assertThat(cheap.matches("correct horse batterY", encoded)).describedAs("a password differing in one letter must not match").isFalse()
    }

    @Test
    fun `salts every hash so that equal passwords yield different hashes`() {
        val first = cheap.hash("same-password")
        val second = cheap.hash("same-password")

        assertThat(first).describedAs("two hashes of one password must differ because of the random salt").isNotEqualTo(second)
    }

    @Test
    fun `verifies with the cost stored in the hash rather than the configured one`() {
        val encoded = cheap.hash("secret-password")
        val stronger = PasswordHasher(Argon2Cost(memoryKib = 16, iterations = 2, parallelism = 1))

        assertThat(stronger.matches("secret-password", encoded))
            .describedAs("hashes made with an older cost must keep verifying after the cost is raised")
            .isTrue()
    }

    @Test
    fun `rejects a stored value that is not an argon2id hash`() {
        assertThat(cheap.matches("password", "\$2a\$10\$abcdefghijklmnopqrstuv"))
            .describedAs("a bcrypt value is not an argon2id hash and must not match")
            .isFalse()
    }

    @Test
    fun `rejects a truncated argon2id hash without throwing`() {
        assertThat(cheap.matches("password", "\$argon2id\$v=19\$m=8,t=1,p=1\$c2FsdA"))
            .describedAs("a malformed stored hash must fail verification instead of failing the login")
            .isFalse()
    }

    @Test
    fun `uses the OWASP parameters by default`() {
        val encoded = PasswordHasher().hash("default-cost")

        assertThat(encoded)
            .describedAs("default cost must follow the OWASP minimum for argon2id: 19 MiB, two iterations, one lane")
            .contains("m=19456,t=2,p=1")
    }

}
