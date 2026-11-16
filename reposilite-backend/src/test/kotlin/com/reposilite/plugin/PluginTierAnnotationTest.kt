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

package com.reposilite.plugin

import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.PluginTier
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.fixtures.FixturePluginOne
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.findAnnotation

internal class PluginTierAnnotationTest {

    @Plugin(name = "official-fixture", tier = PluginTier.OFFICIAL)
    class OfficialFixturePlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Test
    fun `should declare the four tiers in the documented order`() {
        // when: the enum values are read
        val names = PluginTier.entries.map { it.name }

        // then: they are exactly the four tiers of the proposal
        assertThat(names)
            .describedAs("values of PluginTier")
            .containsExactly("CORE", "OFFICIAL", "COMMUNITY", "EXPERIMENTAL")
    }

    @Test
    fun `should default the tier element of the annotation to community`() {
        // when: the default value is read from the annotation type itself
        val default = Plugin::class.java.getMethod("tier").defaultValue

        // then: the default is community, the tier of every plugin that declares nothing
        assertThat(default)
            .describedAs("default of Plugin.tier")
            .isEqualTo(PluginTier.COMMUNITY)
    }

    @Test
    fun `should read community for a plugin that does not declare the element`() {
        // when: the annotation of a plugin without the element is read
        val tier = FixturePluginOne::class.findAnnotation<Plugin>()?.tier

        // then: the default of the annotation type applies
        assertThat(tier)
            .describedAs("tier of a plugin without the element")
            .isEqualTo(PluginTier.COMMUNITY)
    }

    @Test
    fun `should read the declared tier when the element is set`() {
        // when: the annotation of a plugin that declares official is read
        val tier = OfficialFixturePlugin::class.findAnnotation<Plugin>()?.tier

        // then: the declared value is returned
        assertThat(tier)
            .describedAs("tier of a plugin that declares OFFICIAL")
            .isEqualTo(PluginTier.OFFICIAL)
    }

}
