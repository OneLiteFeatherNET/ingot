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

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.PluginTier
import com.reposilite.plugin.api.ReposilitePlugin
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import net.onelitefeather.ingot.plugin.tier.PluginOrigin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The effective tier of a registered plugin, which the start log and the support information read. No test touches the console. */
internal class PluginEntryTierTest {

    @Plugin(name = "claims-core", tier = PluginTier.CORE)
    class ClaimsCorePlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    private val journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()))
    }

    @Test
    fun `should show a core claim from the plugins directory as community`() {
        // given: a plugin that declares core and comes from the plugins directory
        val extensions = Extensions(journalist)
        extensions.registerPlugin(ClaimsCorePlugin(), PluginOrigin.PLUGINS_DIRECTORY)

        // when: the effective tier is read by name
        val tier = extensions.effectiveTierOf("claims-core")

        // then: it is community, because the image does not ship the plugin
        assertThat(tier)
            .describedAs("effective tier of a core claim from the plugins directory")
            .isEqualTo(PluginTier.COMMUNITY)
    }

    @Test
    fun `should keep core for a plugin that declares core in the bundled directory`() {
        // given: the same plugin, loaded from the bundled jars of the image
        val extensions = Extensions(journalist)
        extensions.registerPlugin(ClaimsCorePlugin(), PluginOrigin.BUNDLED_IMAGE)

        // when: the effective tier is read by name
        val tier = extensions.effectiveTierOf("claims-core")

        // then: it is core
        assertThat(tier)
            .describedAs("effective tier of a core declaration in the bundled directory")
            .isEqualTo(PluginTier.CORE)
    }

    @Test
    fun `should read a plugin registered without an origin as community when it claims core`() {
        // given: a plugin registered through the one-argument call that existing plugins and tests use
        val extensions = Extensions(journalist)
        extensions.registerPlugin(ClaimsCorePlugin())

        // when: the effective tier is read by name
        val tier = extensions.effectiveTierOf("claims-core")

        // then: the unknown origin reads as the plugins directory, which never grants core
        assertThat(tier)
            .describedAs("effective tier of a plugin registered without an origin")
            .isEqualTo(PluginTier.COMMUNITY)
    }

    @Test
    fun `should read community for a plugin name that is not registered`() {
        // when: the effective tier of an unknown name is read
        val tier = Extensions(journalist).effectiveTierOf("missing")

        // then: the name reads as the plugins directory with the default tier, and nothing fails
        assertThat(tier)
            .describedAs("effective tier of an unknown plugin name")
            .isEqualTo(PluginTier.COMMUNITY)
    }

}
