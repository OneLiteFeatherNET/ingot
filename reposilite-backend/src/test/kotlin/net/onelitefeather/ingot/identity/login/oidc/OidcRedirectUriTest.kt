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

package net.onelitefeather.ingot.identity.login.oidc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The redirect uri that is registered at a provider: the configured public URL when there is one, else the request. */
internal class OidcRedirectUriTest {

    @Test
    fun `a configured public url is the base of the callback, whatever the request says`() {
        val uri = OidcRedirectUri.of(
            publicUrl = "https://repo.example.org/",
            scheme = "http",
            host = "internal.local:8080",
            basePath = "",
            id = "corp",
        )

        assertThat(uri).isEqualTo("https://repo.example.org/api/auth/oidc/corp/callback")
    }

    @Test
    fun `a trailing slash of the public url does not double`() {
        val uri = OidcRedirectUri.of("https://repo.example.org/ingot///", "https", "ignored", "", "corp")

        assertThat(uri).isEqualTo("https://repo.example.org/ingot/api/auth/oidc/corp/callback")
    }

    @Test
    fun `without a public url the scheme, the host and the base path of the request are used`() {
        val uri = OidcRedirectUri.of(publicUrl = "", scheme = "https", host = "repo.example.org", basePath = "/ingot/", id = "github")

        assertThat(uri).isEqualTo("https://repo.example.org/ingot/api/auth/oidc/github/callback")
    }

    @Test
    fun `a blank public url counts as not configured`() {
        val uri = OidcRedirectUri.of(publicUrl = "   ", scheme = "http", host = "localhost:8080", basePath = "", id = "corp")

        assertThat(uri).isEqualTo("http://localhost:8080/api/auth/oidc/corp/callback")
    }

}
