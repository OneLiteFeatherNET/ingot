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

package net.onelitefeather.ingot.support

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class SupportBundleSettingTest {

    @Test
    fun `should enable the support bundle when nothing is set`() {
        assertThat(SupportBundleSetting.read(emptyMap(), null).enabled).`as`("default switch").isTrue()
    }

    @Test
    fun `should disable the support bundle when the ingot variable says false`() {
        assertThat(SupportBundleSetting.read(mapOf("INGOT_SUPPORT_BUNDLE_ENABLED" to "false"), null).enabled)
            .`as`("switch of the ingot variable").isFalse()
    }

    @Test
    fun `should read the legacy variable when the ingot variable is unset`() {
        assertThat(SupportBundleSetting.read(mapOf("REPOSILITE_SUPPORT_BUNDLE_ENABLED" to "false"), null).enabled)
            .`as`("switch of the legacy variable").isFalse()
    }

    @Test
    fun `should prefer the ingot variable over the legacy one`() {
        val environment = mapOf("INGOT_SUPPORT_BUNDLE_ENABLED" to "true", "REPOSILITE_SUPPORT_BUNDLE_ENABLED" to "false")

        assertThat(SupportBundleSetting.read(environment, null).enabled).`as`("switch with both variables").isTrue()
    }

    @Test
    fun `should read the system property when no variable is set`() {
        assertThat(SupportBundleSetting.read(emptyMap(), "false").enabled).`as`("switch of the system property").isFalse()
    }

    @Test
    fun `should refuse a value that is neither true nor false and name the variable`() {
        assertThatThrownBy { SupportBundleSetting.read(mapOf("INGOT_SUPPORT_BUNDLE_ENABLED" to "yes"), null) }
            .`as`("invalid value")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_SUPPORT_BUNDLE_ENABLED")
    }
}
