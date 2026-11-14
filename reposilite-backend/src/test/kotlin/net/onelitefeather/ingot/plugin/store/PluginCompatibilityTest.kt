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

package net.onelitefeather.ingot.plugin.store

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PluginCompatibilityTest {

    /** A range from 1.2.0 inclusive to 2.0.0 exclusive, for plugin API 1. */
    private val compatibility = Compatibility(ingotMin = "1.2.0", ingotBelow = "2.0.0", pluginApi = 1)

    @Test
    fun `should accept the running version when it equals min`() {
        // when: the running version is the inclusive lower bound
        val reason = CompatibilityRule.check(compatibility, ingotVersion = "1.2.0", pluginApi = 1)

        // then: the entry is compatible
        assertThat(reason).describedAs("min is inclusive, so the lower bound must be accepted").isNull()
    }

    @Test
    fun `should refuse the running version when it equals below`() {
        // when: the running version is the exclusive upper bound
        val reason = CompatibilityRule.check(compatibility, ingotVersion = "2.0.0", pluginApi = 1)

        // then: the entry is refused and the reason names the range
        assertThat(reason).describedAs("below is exclusive, so the upper bound must be refused").isNotNull
        assertThat(reason).contains("1.2.0", "2.0.0")
    }

    @Test
    fun `should compare versions by number and not as text`() {
        // given: a range whose lower bound is 1.9.0
        val range = Compatibility(ingotMin = "1.9.0", ingotBelow = "2.0.0", pluginApi = 1)

        // when: the running version is 1.10.0, which is greater as a number but smaller as text
        val reason = CompatibilityRule.check(range, ingotVersion = "1.10.0", pluginApi = 1)

        // then: the version is inside the range
        assertThat(reason).describedAs("1.10.0 is above 1.9.0 when the parts are compared as numbers").isNull()
    }

    @Test
    fun `should refuse a plugin api other than the one this Ingot reads`() {
        // when: the entry declares plugin API 2
        val reason = CompatibilityRule.check(compatibility.copy(pluginApi = 2), ingotVersion = "1.5.0", pluginApi = 1)

        // then: the entry is refused and the reason names the API
        assertThat(reason).describedAs("a different pluginApi must be refused").isNotNull
        assertThat(reason).contains("pluginApi")
    }

    @Test
    fun `should refuse an entry without a compatibility block`() {
        // when: the entry has no compatibility block
        val reason = CompatibilityRule.check(null, ingotVersion = "1.5.0", pluginApi = 1)

        // then: the reason says that compatibility is missing
        assertThat(reason).describedAs("a missing block must be refused").isNotNull
        assertThat(reason).contains("compatibility")
    }

}
