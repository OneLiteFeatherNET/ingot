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

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.audit.application.AuditSettings
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.PluginTier
import com.reposilite.plugin.api.ReposilitePlugin
import net.onelitefeather.ingot.plugin.tier.PluginOrigin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The origin and the settings domain of a plugin in the support information. Plain functions and fixtures only, no server. */
internal class SupportPluginDetailsTest {

    @Plugin(name = "with-settings", dependencies = ["status"], settings = AuditSettings::class)
    class WithSettingsFixture : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Plugin(name = "without-settings")
    class WithoutSettingsFixture : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    private val clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC)

    private fun metadataOf(type: Class<*>): Plugin = type.getAnnotation(Plugin::class.java)

    private fun sources(plugins: List<PluginFact>) = SupportSources(
        instance = "ingot-test",
        clock = clock,
        version = "1.2.3",
        jvm = { JvmFacts(vendor = "Eclipse Adoptium", version = "21.0.4", uptimeMillis = 5_000, heapUsedMb = 120.5, heapMaxMb = 512, processors = 4, threads = 30) },
        operatingSystem = { OperatingSystemFacts(name = "Linux", version = "6.8", architecture = "amd64", container = true) },
        plugins = { plugins },
    )

    @Test
    fun `should name the settings domain of a plugin that declares settings`() {
        // when: the plugin fact is built from the annotation of a plugin with settings
        val fact = pluginFact(metadataOf(WithSettingsFixture::class.java), PluginTier.CORE, PluginOrigin.BUILT_IN)

        // then: the domain is the name the settings page uses
        assertThat(fact.settingsDomain).describedAs("settings domain of a plugin with settings").isEqualTo("audit")
    }

    @Test
    fun `should name no settings domain for a plugin without settings`() {
        // when: the plugin fact is built from the annotation of a plugin without settings
        val fact = pluginFact(metadataOf(WithoutSettingsFixture::class.java), PluginTier.COMMUNITY, PluginOrigin.PLUGINS_DIRECTORY)

        // then: there is no settings page to link
        assertThat(fact.settingsDomain).describedAs("settings domain of a plugin without settings").isNull()
    }

    @Test
    fun `should keep the dependencies of a plugin in the plugin fact`() {
        // when: the plugin fact is built
        val fact = pluginFact(metadataOf(WithSettingsFixture::class.java), PluginTier.CORE, PluginOrigin.BUILT_IN)

        // then: the dependencies are the names the annotation declares
        assertThat(fact.dependencies).describedAs("dependencies of the plugin fact").containsExactly("status")
    }

    @Test
    fun `should name a plugin that is compiled into the server as built-in`() {
        val fact = pluginFact(metadataOf(WithoutSettingsFixture::class.java), PluginTier.CORE, PluginOrigin.BUILT_IN)

        assertThat(fact.origin).describedAs("origin of a built-in plugin").isEqualTo("built-in")
    }

    @Test
    fun `should name a plugin from the bundled directory of the image as bundled-image`() {
        val fact = pluginFact(metadataOf(WithoutSettingsFixture::class.java), PluginTier.CORE, PluginOrigin.BUNDLED_IMAGE)

        assertThat(fact.origin).describedAs("origin of a bundled plugin").isEqualTo("bundled-image")
    }

    @Test
    fun `should name a plugin from the operator plugins directory as plugins-directory`() {
        val fact = pluginFact(metadataOf(WithoutSettingsFixture::class.java), PluginTier.COMMUNITY, PluginOrigin.PLUGINS_DIRECTORY)

        assertThat(fact.origin).describedAs("origin of a plugin from the plugins directory").isEqualTo("plugins-directory")
    }

    @Test
    fun `should name a plugin verified from the plugin store as plugin-store`() {
        val fact = pluginFact(metadataOf(WithoutSettingsFixture::class.java), PluginTier.OFFICIAL, PluginOrigin.PLUGIN_STORE)

        assertThat(fact.origin).describedAs("origin of a plugin from the store").isEqualTo("plugin-store")
    }

    @Test
    fun `should write the origin and the settings domain of each plugin into the system information`() {
        // given: a plugin fact with an origin and a settings domain
        val collector = SupportInformationCollector(
            sources(listOf(PluginFact(name = "audit", version = "1.2.3", dependencies = emptyList(), tier = "core", origin = "bundled-image", settingsDomain = "audit"))),
        )

        // when: the system information is collected
        val plugin = collector.collect().systemInfo.plugins.single()

        // then: the page carries both fields
        assertThat(plugin.origin).describedAs("origin in the system information").isEqualTo("bundled-image")
        assertThat(plugin.settingsDomain).describedAs("settings domain in the system information").isEqualTo("audit")
    }

    @Test
    fun `should write the origin and the settings domain into plugins json`() {
        // given: the same plugin fact
        val collector = SupportInformationCollector(
            sources(listOf(PluginFact(name = "audit", version = "1.2.3", dependencies = emptyList(), tier = "core", origin = "built-in", settingsDomain = "audit"))),
        )

        // when: the bundle is collected
        val plugin = DEFAULT_OBJECT_MAPPER.readTree(collector.collect().files.getValue("plugins.json")).single()

        // then: the bundle carries the fields next to the existing ones
        assertThat(plugin.get("origin").asText()).describedAs("origin in plugins.json").isEqualTo("built-in")
        assertThat(plugin.get("settingsDomain").asText()).describedAs("settings domain in plugins.json").isEqualTo("audit")
    }

    @Test
    fun `should read plugins-directory and no settings domain for a plugin fact that names neither`() {
        // when: a plugin fact is created the way existing callers create it
        val fact = PluginFact(name = "metrics", version = "1.2.3", dependencies = emptyList())

        // then: the origin is the one that never grants core, and there is no settings page
        assertThat(fact.origin).describedAs("default origin of a plugin fact").isEqualTo("plugins-directory")
        assertThat(fact.settingsDomain).describedAs("default settings domain of a plugin fact").isNull()
    }

}
