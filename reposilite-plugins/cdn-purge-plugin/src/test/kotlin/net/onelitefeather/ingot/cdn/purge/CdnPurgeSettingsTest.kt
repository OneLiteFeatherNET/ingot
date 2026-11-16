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

package net.onelitefeather.ingot.cdn.purge

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class CdnPurgeSettingsTest {

    @Test
    fun `should leave the purge off when no variable is set`() {
        val settings = CdnPurgeSettings.read(emptyMap())

        assertThat(settings.purgeUrl).describedAs("purge URL without variables").isNull()
    }

    @Test
    fun `should refuse a purge URL without a base URL and name the variable`() {
        assertThatThrownBy { CdnPurgeSettings.read(mapOf("INGOT_CDN_PURGE_URL" to "https://cdn.example.test/purge")) }
            .describedAs("start with a purge URL and no base URL")
            .hasMessageContaining("INGOT_CDN_PURGE_BASE_URL")
    }

    @Test
    fun `should read the URLs and the token from the INGOT variables`() {
        val settings = CdnPurgeSettings.read(
            mapOf(
                "INGOT_CDN_PURGE_URL" to "https://cdn.example.test/purge",
                "INGOT_CDN_PURGE_BASE_URL" to "https://repo.example.com",
                "INGOT_CDN_PURGE_TOKEN" to "secret-token",
            )
        )

        assertThat(settings.purgeUrl).isEqualTo("https://cdn.example.test/purge")
        assertThat(settings.baseUrl).isEqualTo("https://repo.example.com")
        assertThat(settings.token).isEqualTo("secret-token")
    }

    @Test
    fun `should read the legacy REPOSILITE variables when the INGOT variables are not set`() {
        val settings = CdnPurgeSettings.read(
            mapOf(
                "REPOSILITE_CDN_PURGE_URL" to "https://cdn.example.test/purge",
                "REPOSILITE_CDN_PURGE_BASE_URL" to "https://repo.example.com",
            )
        )

        assertThat(settings.purgeUrl).describedAs("legacy purge URL").isEqualTo("https://cdn.example.test/purge")
        assertThat(settings.baseUrl).describedAs("legacy base URL").isEqualTo("https://repo.example.com")
    }

}
