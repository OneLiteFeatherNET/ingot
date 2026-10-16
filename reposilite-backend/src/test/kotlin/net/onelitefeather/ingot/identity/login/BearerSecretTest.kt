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

internal class BearerSecretTest {

    @Test
    fun `creates a secret that starts with the prefix and the token id`() {
        val secret = BearerSecret.create(42)

        assertThat(secret).describedAs("the secret must carry the prefix and the token id").startsWith("ingot_42_")
    }

    @Test
    fun `reads the token id back from a secret it created`() {
        assertThat(BearerSecret.tokenId(BearerSecret.create(42))).describedAs("the id must be recoverable without a name").isEqualTo(42)
    }

    @Test
    fun `gives every secret a different random part`() {
        assertThat(BearerSecret.create(7)).isNotEqualTo(BearerSecret.create(7))
    }

    @Test
    fun `keeps every secret below the 72 bytes that bcrypt hashes`() {
        val longest = BearerSecret.create(Int.MAX_VALUE)

        assertThat(longest.toByteArray(Charsets.UTF_8).size)
            .describedAs("bcrypt ignores everything after 72 bytes, so a longer secret would not be fully protected")
            .isLessThanOrEqualTo(72)
    }

    @Test
    fun `does not read a secret without the prefix`() {
        assertThat(BearerSecret.tokenId("42_abcdef")).describedAs("a secret without the prefix has no id").isNull()
    }

    @Test
    fun `does not read an id that is not a number`() {
        assertThat(BearerSecret.tokenId("ingot_forty_abcdef")).describedAs("a non-numeric id is not an id").isNull()
    }

    @Test
    fun `does not read a secret without a random part`() {
        assertThat(BearerSecret.tokenId("ingot_42_")).describedAs("an empty random part is not a secret").isNull()
        assertThat(BearerSecret.tokenId("ingot_42")).describedAs("a missing separator is not a secret").isNull()
    }

}
