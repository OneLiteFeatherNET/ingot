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
package com.reposilite.frontend.api

import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.frontend.application.FrontendSettings
import com.reposilite.journalist.Channel
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import com.reposilite.configuration.shared.api.Doc

internal class PluginUiFacadeTest {

    @Plugin(name = "example")
    private class ExamplePlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Plugin(name = "frontend-owner", settings = FrontendSettings::class)
    private class FrontendOwnerPlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Doc(title = "Plugins", description = "A domain whose name collides with the reserved address")
    private data class PluginsNamedSettings(val enabled: Boolean = false) : SharedSettings

    @Plugin(name = "collides", settings = PluginsNamedSettings::class)
    private class CollidingPlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    /** A real logger whose output goes to memory, so the warnings are read from the same text an operator sees. */
    private class CapturedLog {
        private val buffer = ByteArrayOutputStream()
        private val stream = PrintStream(buffer, true, UTF_8)
        val logger: Logger = PrintStreamLogger(stream, stream, Channel.ALL, true)

        val text: String
            get() {
                stream.flush()
                return buffer.toString(UTF_8)
            }
    }

    private fun page(id: String = "status", blocks: List<PluginUiBlock> = listOf(PluginUiBlock.Text("Hello"))) =
        PluginUiPage(id = id, title = "Status", area = "maintenance", blocks = blocks)

    private fun everyone(@Suppress("UNUSED_PARAMETER") area: String) = true

    @Test
    fun `should keep a valid page and drop an invalid one with a warning`() {
        // given: a facade and a plugin that registers a valid page and a page with 31 blocks
        val log = CapturedLog()
        val facade = PluginUiFacade(logger = log.logger, enabled = { true })
        val registrar = facade.forPlugin(ExamplePlugin())

        // when: both pages are registered
        val kept = registrar.registerPage(page(id = "status"))
        val dropped = registrar.registerPage(page(id = "too-long", blocks = List(31) { PluginUiBlock.Text("line $it") }))

        // then: the valid page is served and the invalid one is not, with a warning that names the plugin and the problem
        assertThat(kept).describedAs("valid page accepted").isTrue()
        assertThat(dropped).describedAs("invalid page accepted").isFalse()
        assertThat(facade.manifest(::everyone, emptyList()).pages.map { it.id }).containsExactly("status")
        assertThat(log.text).describedAs("warning").contains("example").contains("too-long").contains("30 blocks")
    }

    @Test
    fun `should take the plugin name from the registering plugin`() {
        // given: a page registered by the plugin named example
        val facade = PluginUiFacade(logger = CapturedLog().logger, enabled = { true })

        // when: the page is registered and the manifest is read
        facade.forPlugin(ExamplePlugin()).registerPage(page())
        val manifestPage = facade.manifest(::everyone, emptyList()).pages.single()

        // then: the page carries the plugin name of its registrar, which the page itself cannot set
        assertThat(manifestPage.plugin).describedAs("plugin of the page").isEqualTo("example")
    }

    @Test
    fun `should drop a settings section for a domain the plugin does not own`() {
        // given: a plugin whose own settings domain is frontend
        val log = CapturedLog()
        val facade = PluginUiFacade(logger = log.logger, enabled = { true })

        // when: the plugin places the domain web, which is not its own
        val accepted = facade.forPlugin(FrontendOwnerPlugin()).registerSettingsSection(PluginUiSection(domain = "web", label = "Web"))

        // then: the section is dropped with a warning and the manifest has no section
        assertThat(accepted).describedAs("section accepted").isFalse()
        assertThat(facade.manifest(::everyone, emptyList()).sections).describedAs("sections").isEmpty()
        assertThat(log.text).describedAs("warning").contains("web").contains("frontend-owner")
    }

    @Test
    fun `should keep a settings section for the own domain of the plugin`() {
        // given: a plugin whose own settings domain is frontend
        val facade = PluginUiFacade(logger = CapturedLog().logger, enabled = { true })

        // when: the plugin places its own domain with a label and a group
        val accepted = facade.forPlugin(FrontendOwnerPlugin())
            .registerSettingsSection(PluginUiSection(domain = "frontend", label = "Mirror checks", group = "Repository"))

        // then: the section is served with the plugin name
        assertThat(accepted).describedAs("section accepted").isTrue()
        val section = facade.manifest(::everyone, emptyList()).sections.single()
        assertThat(section.plugin).isEqualTo("frontend-owner")
        assertThat(section.label).isEqualTo("Mirror checks")
        assertThat(section.group).isEqualTo("Repository")
    }

    @Test
    fun `should report a settings domain named plugins when the plugin is registered`() {
        // given: a plugin whose settings domain is called plugins, the reserved address of the dashboard
        val log = CapturedLog()

        // when: the registrar is created for the plugin
        PluginUiFacade(logger = log.logger, enabled = { true }).forPlugin(CollidingPlugin())

        // then: the collision is reported at startup
        assertThat(log.text).describedAs("report").contains("collides").contains("plugins")
    }

    @Test
    fun `should refuse a second page with the same id`() {
        // given: a registrar with one page
        val log = CapturedLog()
        val registrar = PluginUiFacade(logger = log.logger, enabled = { true }).forPlugin(ExamplePlugin())
        registrar.registerPage(page(id = "status"))

        // when: a page with the same id is registered
        val accepted = registrar.registerPage(page(id = "status"))

        // then: the second page is refused
        assertThat(accepted).describedAs("second page accepted").isFalse()
    }

    @Test
    fun `should refuse a twenty first page of one plugin`() {
        // given: a registrar that already holds twenty pages
        val log = CapturedLog()
        val registrar = PluginUiFacade(logger = log.logger, enabled = { true }).forPlugin(ExamplePlugin())
        (1..20).forEach { registrar.registerPage(page(id = "page-$it")) }

        // when: one more page is registered
        val accepted = registrar.registerPage(page(id = "page-21"))

        // then: it is refused with the limit named
        assertThat(accepted).describedAs("twenty first page accepted").isFalse()
        assertThat(log.text).describedAs("warning").contains("20 pages")
    }

    @Test
    fun `should serve no page and no area guard while the switch is off`() {
        // given: a facade with the switch off and a registered page
        val facade = PluginUiFacade(logger = CapturedLog().logger, enabled = { false })
        facade.forPlugin(ExamplePlugin()).registerPage(page())

        // when: the manifest is read and the guard of the plugin is asked for
        val manifest = facade.manifest(::everyone, listOf("nexus-import"))

        // then: no page is served, no route is guarded, and the enabled plugins are still reported
        assertThat(manifest.pages).describedAs("pages").isEmpty()
        assertThat(facade.areasGuarding("example")).describedAs("guarded areas").isEmpty()
        assertThat(manifest.enabledPlugins).containsExactly("nexus-import")
    }

    @Test
    fun `should leave out a page whose area the caller does not hold`() {
        // given: a page that needs the audit area
        val facade = PluginUiFacade(logger = CapturedLog().logger, enabled = { true })
        facade.forPlugin(ExamplePlugin()).registerPage(page().copy(area = "audit"))

        // when: the manifest is read by a caller that holds no area
        val manifest = facade.manifest({ false }, emptyList())

        // then: the page is absent, not only greyed out
        assertThat(manifest.pages).describedAs("pages").isEmpty()
    }

}
