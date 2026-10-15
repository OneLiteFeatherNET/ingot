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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** The public URL of `login`: an absolute https address, or http for a loopback host, and nothing else. */
internal class PublicUrlTest {

    @ParameterizedTest
    @ValueSource(strings = [
        "https://repo.example.org/",
        "https://repo.example.org",
        "https://repo.example.org/ingot/",
        "https://repo.example.org:8443/",
        "http://localhost:8080/",
        "http://127.0.0.1:8080",
        "http://[::1]:8080/",
    ])
    fun `an absolute https address or a loopback http address is accepted`(value: String) {
        assertThat(PublicUrl.problemOf(value)).isNull()
    }

    @Test
    fun `a blank public url means that the address is derived from the request`() {
        assertThat(PublicUrl.problemOf("")).isNull()
        assertThat(PublicUrl.problemOf("   ")).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "http://repo.example.org/",
        "repo.example.org",
        "ftp://repo.example.org/",
        "https://",
        "https://repo.example.org/?next=/",
        "https://repo.example.org/#top",
        "https://alice:secret@repo.example.org/",
        "https://repo.example.org/with space",
    ])
    fun `anything else is refused`(value: String) {
        assertThat(PublicUrl.problemOf(value)).isNotNull()
    }

    @Test
    fun `the settings name the refused public url by its field`() {
        val problems = LoginSettings(publicUrl = "http://repo.example.org").violations()

        assertThat(problems).hasSize(1)
        assertThat(problems.single()).contains("publicUrl")
    }

    @Test
    fun `the settings accept a valid public url`() {
        assertThat(LoginSettings(publicUrl = "https://repo.example.org/").violations()).isEmpty()
    }

}
