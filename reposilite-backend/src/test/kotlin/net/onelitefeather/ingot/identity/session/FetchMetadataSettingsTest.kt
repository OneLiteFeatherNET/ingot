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

package net.onelitefeather.ingot.identity.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FetchMetadataSettingsTest {

    private fun resolve(
        env: Map<String, String> = emptyMap(),
        props: Map<String, String> = emptyMap(),
    ): FetchMetadataSettings = FetchMetadataSettings.resolve({ env[it] }, { props[it] })

    @Test
    fun `keeps the refusal on when nothing is set`() {
        assertThat(resolve().enabled).describedAs("the protection is on by default").isTrue()
    }

    @Test
    fun `turns the refusal off for the environment value off`() {
        assertThat(resolve(env = mapOf("INGOT_FETCH_METADATA" to "off")).enabled).isFalse()
    }

    @Test
    fun `matches the value off without regard to case and surrounding blanks`() {
        assertThat(resolve(env = mapOf("INGOT_FETCH_METADATA" to " OFF ")).enabled).isFalse()
    }

    @Test
    fun `turns the refusal off for the system property off when the variable is unset`() {
        assertThat(resolve(props = mapOf("ingot.fetchMetadata" to "off")).enabled).isFalse()
    }

    @Test
    fun `lets the variable win over the system property`() {
        assertThat(resolve(env = mapOf("INGOT_FETCH_METADATA" to "on"), props = mapOf("ingot.fetchMetadata" to "off")).enabled)
            .describedAs("the variable is read first, as for the other security switches")
            .isTrue()
    }

    @Test
    fun `falls back to the system property when the variable is blank`() {
        assertThat(resolve(env = mapOf("INGOT_FETCH_METADATA" to "  "), props = mapOf("ingot.fetchMetadata" to "off")).enabled)
            .isFalse()
    }

    @Test
    fun `keeps the refusal on for a value that is not off, so a typo cannot switch it off`() {
        assertThat(resolve(env = mapOf("INGOT_FETCH_METADATA" to "of")).enabled).isTrue()
    }

    @Test
    fun `keeps the refusal on for the value false`() {
        assertThat(resolve(env = mapOf("INGOT_FETCH_METADATA" to "false")).enabled)
            .describedAs("only the value off is a switch, every other value keeps the protection")
            .isTrue()
    }

    @Test
    fun `does not read the reposilite spelling of the switch`() {
        assertThat(resolve(env = mapOf("REPOSILITE_FETCH_METADATA" to "off")).enabled)
            .describedAs("the cookie sessions are Ingot's own, so there is no older setting to fall back to")
            .isTrue()
    }
}
