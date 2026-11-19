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
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.plugin.ui.PluginUiReaderPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalIngotPluginApi::class)
internal class PluginUiReaderFacadeTest {

    @Plugin(name = "example")
    private class ExamplePlugin : ReposilitePlugin() {
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

    private fun readerPage(id: String) = PluginUiReaderPage(id = id, title = "Releases", blocks = listOf(PluginUiBlock.Text("Hello")))

    @Test
    fun `should drop the 21st reader page of a plugin with a warning and keep the first 20`() {
        // given: a facade and a plugin that registers 21 reader pages
        val log = CapturedLog()
        val facade = PluginUiFacade(logger = log.logger, enabled = { true })
        val registrar = facade.forPlugin(ExamplePlugin())

        // when: the pages are registered in order
        val accepted = (1..21).map { registrar.registerReaderPage(readerPage("page-$it")) }

        // then: the last one is dropped with a warning that names the plugin, and the others are kept
        assertThat(accepted.take(20)).describedAs("first twenty pages accepted").allMatch { it }
        assertThat(accepted.last()).describedAs("21st page accepted").isFalse()
        assertThat(facade.readerPages()).describedAs("reader pages kept").hasSize(20)
        assertThat(log.text).describedAs("warning").contains("example").contains("page-21").contains("20 reader pages")
    }

    @Test
    fun `should hide the reader pages while the readers switch is off`() {
        // given: a facade with the readers switch off and a reader page registered while it was on
        var readersOn = true
        val facade = PluginUiFacade(logger = CapturedLog().logger, enabled = { true }, readersEnabled = { readersOn })
        facade.forPlugin(ExamplePlugin()).registerReaderPage(readerPage("releases"))

        // when: the switch is turned off
        readersOn = false

        // then: no reader page is served
        assertThat(facade.readerPages()).describedAs("reader pages").isEmpty()
    }

    @Test
    fun `should keep the admin pages apart from the reader pages`() {
        // given: a facade where a plugin registers an admin page and a reader page with the same id
        val facade = PluginUiFacade(logger = CapturedLog().logger, enabled = { true })
        val registrar = facade.forPlugin(ExamplePlugin())

        // when: both are registered
        val admin = registrar.registerPage(PluginUiPage(id = "status", title = "Status", area = "maintenance", blocks = listOf(PluginUiBlock.Text("Hello"))))
        val reader = registrar.registerReaderPage(readerPage("status"))

        // then: both are accepted, and the admin manifest still holds the admin page only
        assertThat(admin).describedAs("admin page accepted").isTrue()
        assertThat(reader).describedAs("reader page accepted").isTrue()
        assertThat(facade.manifest({ true }, emptyList()).pages.map { it.id }).containsExactly("status")
        assertThat(facade.readerPages().map { it.id }).containsExactly("status")
    }
}
