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

import com.reposilite.journalist.backend.InMemoryLogger
import net.onelitefeather.ingot.identity.login.LoginSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.toMutableReference

/** The entries of `login.oidcProviders` that are skipped or cannot be announced are logged, once per change, with the reason. */
internal class OidcProviderCatalogTest {

    // The captured appender: every line the catalog writes lands here, and the tests read it back.
    private val journalist = InMemoryLogger()
    private val settings = LoginSettings().toMutableReference()
    private val catalog = OidcProviderCatalog(settings) { journalist.warn(it) }

    private fun configure(vararg providers: OidcProviderSettings) {
        settings.update(LoginSettings(oidcProviders = providers.toList()))
    }

    private fun logged(): List<String> = journalist.messages.map { it.value }

    private fun provider(
        id: String,
        enabled: Boolean = false,
        issuerUrl: String = "https://sso.example.org",
        clientSecret: String = "very-secret-value",
    ) = OidcProviderSettings(id = id, enabled = enabled, issuerUrl = issuerUrl, clientId = "ingot", clientSecret = clientSecret)

    @Test
    fun `an entry whose id is not a slug is logged with its position and the reason`() {
        configure(provider("corp"), provider("Not A Slug"))

        catalog.report()

        assertThat(logged()).hasSize(1)
        assertThat(logged().single())
            .contains("oidcProviders[1]")
            .contains("not a slug")
            .contains("Not A Slug")
    }

    @Test
    fun `an entry without an id is logged as skipped for an empty id`() {
        configure(provider(""))

        catalog.report()

        assertThat(logged().single()).contains("oidcProviders[0]").contains("the id is empty")
    }

    @Test
    fun `a later entry with the id of an earlier one is logged as a duplicate and skipped`() {
        val first = provider("corp", enabled = true)
        configure(first, provider("corp", enabled = true, issuerUrl = "https://other.example.org"))

        catalog.report()

        assertThat(logged().single()).contains("oidcProviders[1]").contains("already used by an earlier entry")
        assertThat(catalog.enabled()).containsExactly(first)
    }

    @Test
    fun `an enabled entry without its issuer is logged as not announced, naming the missing field`() {
        configure(provider("corp", enabled = true, issuerUrl = ""))

        catalog.report()

        assertThat(logged().single()).contains("oidcProviders[0]").contains("not announced").contains("issuerUrl is missing")
    }

    @Test
    fun `a configuration without problems logs nothing`() {
        configure(provider("corp", enabled = true), provider("keycloak"))

        catalog.report()
        catalog.enabled()

        assertThat(logged()).isEmpty()
    }

    @Test
    fun `the same problem is not logged again on the next read`() {
        configure(provider("Not A Slug"))

        catalog.report()
        catalog.enabled()
        catalog.announced()

        assertThat(logged()).hasSize(1)
    }

    @Test
    fun `a changed configuration logs the problems it has now`() {
        configure(provider("Not A Slug"))
        catalog.report()

        configure(provider("Another Bad Id"))
        catalog.report()

        assertThat(logged()).hasSize(2)
        assertThat(logged()[1]).contains("Another Bad Id")
    }

    @Test
    fun `a log line never names the client secret`() {
        configure(provider("Not A Slug", clientSecret = "very-secret-value"))

        catalog.report()

        assertThat(logged().single()).doesNotContain("very-secret-value")
    }

}
