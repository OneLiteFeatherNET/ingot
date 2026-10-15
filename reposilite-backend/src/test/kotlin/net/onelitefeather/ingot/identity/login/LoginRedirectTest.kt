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

internal class LoginRedirectTest {

    @Test
    fun `a relative path is kept with its query`() {
        assertThat(LoginRedirect.sanitize("/repositories/releases?page=2")).isEqualTo("/repositories/releases?page=2")
    }

    @Test
    fun `a missing redirect goes to the root`() {
        assertThat(LoginRedirect.sanitize(null)).isEqualTo("/")
        assertThat(LoginRedirect.sanitize("  ")).isEqualTo("/")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://evil.example/steal",
            "//evil.example",
            "/\\evil.example",
            "\\\\evil.example",
            "javascript:alert(1)",
            "dashboard",
            "/path\r\nSet-Cookie: x=1",
            "/tab\u0000",
            "/with\\backslash",
        ],
    )
    fun `anything that could leave the origin is replaced by the root`(value: String) {
        assertThat(LoginRedirect.sanitize(value)).describedAs("redirect for $value").isEqualTo("/")
    }

    @Test
    fun `a path longer than 1024 characters is replaced by the root`() {
        val tooLong = "/" + "a".repeat(1024)

        assertThat(LoginRedirect.sanitize(tooLong)).isEqualTo("/")
        assertThat(LoginRedirect.sanitize(tooLong.dropLast(1))).isEqualTo(tooLong.dropLast(1))
    }
}
