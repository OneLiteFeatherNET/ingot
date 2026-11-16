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

package net.onelitefeather.ingot.plugin.tier

import com.reposilite.plugin.api.PluginTier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * The origin rule of the design (D2) as a table. The resolver is a pure function, so no test touches the loader,
 * the file system or the clock.
 */
internal class PluginTierResolverTest {

    @ParameterizedTest(name = "{0} plugin declaring {1} is {2}")
    @CsvSource(
        "BUILT_IN, COMMUNITY, CORE",
        "BUILT_IN, CORE, CORE",
        "BUILT_IN, OFFICIAL, CORE",
        "BUILT_IN, EXPERIMENTAL, EXPERIMENTAL",
        "BUNDLED_IMAGE, COMMUNITY, CORE",
        "BUNDLED_IMAGE, CORE, CORE",
        "BUNDLED_IMAGE, OFFICIAL, CORE",
        "BUNDLED_IMAGE, EXPERIMENTAL, EXPERIMENTAL",
        "PLUGINS_DIRECTORY, COMMUNITY, COMMUNITY",
        "PLUGINS_DIRECTORY, CORE, COMMUNITY",
        "PLUGINS_DIRECTORY, OFFICIAL, OFFICIAL",
        "PLUGINS_DIRECTORY, EXPERIMENTAL, EXPERIMENTAL",
        "PLUGIN_STORE, COMMUNITY, COMMUNITY",
        "PLUGIN_STORE, CORE, COMMUNITY",
        "PLUGIN_STORE, OFFICIAL, OFFICIAL",
        "PLUGIN_STORE, EXPERIMENTAL, EXPERIMENTAL",
    )
    fun `should resolve the effective tier from the origin and the declared tier`(
        origin: PluginOrigin,
        declared: PluginTier,
        expected: PluginTier,
    ) {
        // when: the effective tier is resolved
        val effective = PluginTierResolver.resolve(origin, declared)

        // then: the table of the design gives the expected tier
        assertThat(effective)
            .describedAs("effective tier of $declared declared by a plugin of origin $origin")
            .isEqualTo(expected)
    }

}
