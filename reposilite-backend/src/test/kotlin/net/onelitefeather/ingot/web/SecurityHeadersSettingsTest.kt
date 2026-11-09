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

package net.onelitefeather.ingot.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SecurityHeadersSettingsTest {

    private fun resolve(env: Map<String, String> = emptyMap(), props: Map<String, String> = emptyMap()) =
        SecurityHeadersSettings.resolve({ env[it] }, { props[it] })

    @Test
    fun `should sandbox repository content and skip hsts when nothing is configured`() {
        val settings = resolve()

        assertThat(settings.repositorySandbox).describedAs("repository sandbox").isTrue()
        assertThat(settings.hstsMaxAgeSeconds).describedAs("hsts max age").isNull()
    }

    @Test
    fun `should switch the repository sandbox off through the environment`() {
        assertThat(resolve(env = mapOf("INGOT_REPOSITORY_SANDBOX" to "FALSE")).repositorySandbox).isFalse()
    }

    @Test
    fun `should switch the repository sandbox off through the system property`() {
        assertThat(resolve(props = mapOf("ingot.repository.sandbox" to "false")).repositorySandbox).isFalse()
    }

    @Test
    fun `should prefer the environment over the system property`() {
        val settings = resolve(
            env = mapOf("INGOT_HSTS_MAX_AGE" to "60"),
            props = mapOf("ingot.hsts.maxAge" to "120"),
        )

        assertThat(settings.hstsMaxAgeSeconds).isEqualTo(60)
    }

    @Test
    fun `should ignore a max age that is not a positive number`() {
        listOf("abc", "0", "-5", "").forEach { value ->
            assertThat(resolve(env = mapOf("INGOT_HSTS_MAX_AGE" to value)).hstsMaxAgeSeconds)
                .describedAs("max age for '$value'")
                .isNull()
        }
    }

}
