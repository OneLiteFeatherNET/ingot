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

package com.reposilite.shared.http

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class UpstreamStatusModeTest {

    @Test
    fun `should default to legacy when nothing is set`() {
        assertThat(UpstreamStatusMode.read(emptyMap(), emptyMap())).isEqualTo(UpstreamStatusMode.LEGACY)
    }

    @Test
    fun `should read strict regardless of case`() {
        assertThat(UpstreamStatusMode.read(emptyMap(), mapOf("INGOT_PROXY_UPSTREAM_STATUS" to "Strict"))).isEqualTo(UpstreamStatusMode.STRICT)
    }

    @Test
    fun `should prefer the property over the environment`() {
        val mode = UpstreamStatusMode.read(
            properties = mapOf("ingot.proxy.upstreamStatus" to "legacy"),
            environment = mapOf("INGOT_PROXY_UPSTREAM_STATUS" to "strict"),
        )

        assertThat(mode).isEqualTo(UpstreamStatusMode.LEGACY)
    }

    @Test
    fun `should prefer the ingot spelling over the reposilite fallbacks`() {
        val mode = UpstreamStatusMode.read(
            properties = mapOf("reposilite.proxy.upstreamStatus" to "legacy"),
            environment = mapOf("INGOT_PROXY_UPSTREAM_STATUS" to "strict"),
        )

        assertThat(mode).isEqualTo(UpstreamStatusMode.STRICT)
    }

    @Test
    fun `should fall back to the reposilite property`() {
        assertThat(UpstreamStatusMode.read(mapOf("reposilite.proxy.upstreamStatus" to "strict"), emptyMap())).isEqualTo(UpstreamStatusMode.STRICT)
    }

    @Test
    fun `should fall back to the reposilite environment variable`() {
        assertThat(UpstreamStatusMode.read(emptyMap(), mapOf("REPOSILITE_PROXY_UPSTREAM_STATUS" to "strict"))).isEqualTo(UpstreamStatusMode.STRICT)
    }

    @Test
    fun `should refuse an unknown value with a message that names the setting`() {
        assertThatThrownBy { UpstreamStatusMode.read(emptyMap(), mapOf("INGOT_PROXY_UPSTREAM_STATUS" to "loose")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_PROXY_UPSTREAM_STATUS")
            .hasMessageContaining("loose")
    }

}
