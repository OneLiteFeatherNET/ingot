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

import com.reposilite.journalist.Channel
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.plugin.ui.PluginUiReaderPage
import org.assertj.core.api.Assertions.assertThat
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import org.junit.jupiter.api.Test

@OptIn(ExperimentalIngotPluginApi::class)
internal class PluginUiReaderManifestTest {

    @Plugin(name = "example")
    private class ExamplePlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Plugin(name = "other")
    private class OtherPlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    private val logger: Logger = PrintStreamLogger(PrintStream(java.io.OutputStream.nullOutputStream(), true, UTF_8), PrintStream(java.io.OutputStream.nullOutputStream(), true, UTF_8), Channel.ALL, true)

    private fun facade(readersOn: () -> Boolean = { true }) = PluginUiFacade(logger = logger, enabled = { true }, readersEnabled = readersOn)

    private fun page(id: String, source: String) = PluginUiReaderPage(
        id = id,
        title = "Page $id",
        blocks = listOf(PluginUiBlock.KeyValue(source)),
    )

    @Test
    fun `should list the reader pages grouped by plugin in registration order`() {
        // given: two plugins that register reader pages, one after the other
        val facade = facade()
        facade.forPlugin(ExamplePlugin()).registerReaderPage(page("releases", "/api/plugins/example/releases"))
        facade.forPlugin(OtherPlugin()).registerReaderPage(page("status", "/api/plugins/other/status"))
        facade.forPlugin(ExamplePlugin()).registerReaderPage(page("mirrors", "/api/plugins/example/mirrors"))

        // when: the reader manifest is built
        val manifest = facade.readerManifest()

        // then: the pages of each plugin are listed together, the plugins in the order of their first registration
        assertThat(manifest.version).describedAs("version").isEqualTo(1)
        assertThat(manifest.pages.map { it.plugin to it.id })
            .describedAs("plugin and page id")
            .containsExactly("example" to "releases", "example" to "mirrors", "other" to "status")
    }

    @Test
    fun `should list no reader page while the readers switch is off`() {
        // given: a reader page registered while the switch was on
        var readersOn = true
        val facade = facade { readersOn }
        facade.forPlugin(ExamplePlugin()).registerReaderPage(page("releases", "/api/plugins/example/releases"))

        // when: the switch is turned off and the manifest is built
        readersOn = false

        // then: the manifest is empty
        assertThat(facade.readerManifest().pages).describedAs("pages").isEmpty()
    }

    @Test
    fun `should name the source routes of a plugin even while the readers switch is off`() {
        // given: a reader page with a source, registered while the switch is off
        val facade = facade { false }
        facade.forPlugin(ExamplePlugin()).registerReaderPage(page("releases", "/api/plugins/example/releases"))

        // when: the sources of the plugin are asked for the guard
        val sources = facade.readerSources("example")

        // then: the source is named, so the guard can answer 404 for it
        assertThat(sources).describedAs("sources").containsExactly("/api/plugins/example/releases")
    }

    @Test
    fun `should not name the source routes of another plugin`() {
        // given: a reader page of the other plugin with a source of its own
        val facade = facade()
        facade.forPlugin(OtherPlugin()).registerReaderPage(page("status", "/api/plugins/other/status"))

        // when: the sources of the example plugin are asked for
        val sources = facade.readerSources("example")

        // then: none of the other plugin's sources is named
        assertThat(sources).describedAs("sources").isEmpty()
    }
}
