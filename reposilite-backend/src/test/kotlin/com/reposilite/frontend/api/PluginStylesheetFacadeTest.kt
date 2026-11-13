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
import java.security.MessageDigest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PluginStylesheetFacadeTest {

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

    private val validSheet = "[data-ingot-component=\"plugin-page\"] [data-ingot-part=\"header\"] { color: red; }"

    private fun everyone(@Suppress("UNUSED_PARAMETER") area: String) = true

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun readerOf(vararg resources: Pair<String, String>): (String) -> ByteArray? {
        val byName = resources.associate { (name, text) -> name to text.toByteArray(UTF_8) }
        return { name -> byName[name] }
    }

    @Test
    fun `should list the stylesheet of a plugin with its path and the hash of the wrapped bytes`() {
        // given: a facade and a plugin that ships a valid sheet
        val facade = PluginUiFacade(logger = CapturedLog().logger, enabled = { true })
        val registrar = facade.forPlugin(ExamplePlugin(), readerOf("dashboard.css" to validSheet))

        // when: the sheet is registered and the manifest is read
        val accepted = registrar.registerStylesheet("dashboard.css")
        val listed = facade.manifest(::everyone, emptyList()).stylesheets.single()

        // then: the entry carries the plugin, a path with the first 16 hex characters, and the full digest of the served bytes
        val served = PluginStylesheetValidator.wrap(validSheet).toByteArray(UTF_8)
        val digest = sha256Hex(served)
        assertThat(accepted).describedAs("sheet accepted").isTrue()
        assertThat(listed.plugin).describedAs("plugin").isEqualTo("example")
        assertThat(listed.path).describedAs("path").isEqualTo("/api/frontend/plugin-styles/example/${digest.take(16)}.css")
        assertThat(listed.sha256).describedAs("sha256 of the served bytes").isEqualTo(digest)
    }

    @Test
    fun `should drop a second stylesheet of the same plugin with a warning and keep the first`() {
        // given: a plugin whose first sheet is accepted
        val log = CapturedLog()
        val facade = PluginUiFacade(logger = log.logger, enabled = { true })
        val registrar = facade.forPlugin(ExamplePlugin(), readerOf("first.css" to validSheet, "second.css" to ".other { color: blue; }"))
        registrar.registerStylesheet("first.css")

        // when: the plugin registers a second sheet
        val second = registrar.registerStylesheet("second.css")

        // then: the second call is dropped, the warning names the plugin, and the listed hash is the first sheet's
        val first = PluginStylesheetValidator.wrap(validSheet).toByteArray(UTF_8)
        assertThat(second).describedAs("second sheet accepted").isFalse()
        assertThat(facade.manifest(::everyone, emptyList()).stylesheets.single().sha256)
            .describedAs("sha256 of the first sheet").isEqualTo(sha256Hex(first))
        assertThat(log.text).describedAs("warning").contains("example").contains("one stylesheet")
    }

    @Test
    fun `should warn and list nothing when the resource is missing from the plugin`() {
        // given: a plugin that names a resource its jar does not contain
        val log = CapturedLog()
        val facade = PluginUiFacade(logger = log.logger, enabled = { true })

        // when: the sheet is registered
        val accepted = facade.forPlugin(ExamplePlugin(), readerOf()).registerStylesheet("missing.css")

        // then: nothing is listed, and the warning names the missing resource and the plugin
        assertThat(accepted).describedAs("sheet accepted").isFalse()
        assertThat(facade.manifest(::everyone, emptyList()).stylesheets).describedAs("stylesheets").isEmpty()
        assertThat(log.text).describedAs("warning").contains("missing.css").contains("example")
    }

    @Test
    fun `should drop an invalid sheet with a warning that names the rule`() {
        // given: a plugin whose sheet loads a remote image
        val log = CapturedLog()
        val facade = PluginUiFacade(logger = log.logger, enabled = { true })
        val unsafe = ".banner { background: url(https://example.org/x.png); }"

        // when: the sheet is registered
        val accepted = facade.forPlugin(ExamplePlugin(), readerOf("unsafe.css" to unsafe)).registerStylesheet("unsafe.css")

        // then: the sheet is not listed, and the warning names the plugin and the construct
        assertThat(accepted).describedAs("sheet accepted").isFalse()
        assertThat(facade.manifest(::everyone, emptyList()).stylesheets).describedAs("stylesheets").isEmpty()
        assertThat(log.text).describedAs("warning").contains("example").contains("url(")
    }

    @Test
    fun `should list no stylesheet for a plugin that registered none`() {
        // given: a facade with one plugin that declares a sheet and another that declares none
        val facade = PluginUiFacade(logger = CapturedLog().logger, enabled = { true })
        facade.forPlugin(ExamplePlugin(), readerOf("dashboard.css" to validSheet)).registerStylesheet("dashboard.css")

        // when: the manifest is read
        val listed = facade.manifest(::everyone, emptyList()).stylesheets.map { it.plugin }

        // then: only the plugin with a sheet is listed
        assertThat(listed).describedAs("plugins with a stylesheet").containsExactly("example")
    }

    @Test
    fun `should list no stylesheet while the stylesheet switch is off and keep the pages`() {
        // given: a plugin with a sheet and a page, and the stylesheet switch off
        var stylesheetsOn = false
        val facade = PluginUiFacade(logger = CapturedLog().logger, enabled = { true }, stylesheetsEnabled = { stylesheetsOn })
        val registrar = facade.forPlugin(ExamplePlugin(), readerOf("dashboard.css" to validSheet))
        registrar.registerStylesheet("dashboard.css")
        registrar.registerPage(PluginUiPage(id = "status", title = "Status", area = "maintenance", blocks = listOf(PluginUiBlock.Text("Hello"))))

        // when: the manifest is read
        val manifest = facade.manifest(::everyone, emptyList())

        // then: no stylesheet is listed, and the page is still there
        assertThat(manifest.stylesheets).describedAs("stylesheets").isEmpty()
        assertThat(manifest.pages.map { it.id }).describedAs("pages").containsExactly("status")
        assertThat(facade.stylesheet("example", hashOf(validSheet))).describedAs("sheet by hash").isNull()
        stylesheetsOn = true
        assertThat(facade.manifest(::everyone, emptyList()).stylesheets).describedAs("stylesheets once the switch is on").hasSize(1)
    }

    @Test
    fun `should list no stylesheet while the plugin UI switch is off`() {
        // given: a plugin with a sheet, and the whole plugin UI switched off
        val facade = PluginUiFacade(logger = CapturedLog().logger, enabled = { false })
        facade.forPlugin(ExamplePlugin(), readerOf("dashboard.css" to validSheet)).registerStylesheet("dashboard.css")

        // when: the manifest is read
        val manifest = facade.manifest(::everyone, listOf("example"))

        // then: no stylesheet is listed, and the enabled plugins are still reported
        assertThat(manifest.stylesheets).describedAs("stylesheets").isEmpty()
        assertThat(manifest.enabledPlugins).describedAs("enabled plugins").containsExactly("example")
    }

    @Test
    fun `should answer a stylesheet only for the hash of its current bytes`() {
        // given: a registered sheet
        val facade = PluginUiFacade(logger = CapturedLog().logger, enabled = { true })
        facade.forPlugin(ExamplePlugin(), readerOf("dashboard.css" to validSheet)).registerStylesheet("dashboard.css")
        val hash = hashOf(validSheet)

        // when: the sheet is asked for by its hash and by a hash of other bytes
        val current = facade.stylesheet("example", hash)
        val stale = facade.stylesheet("example", "0123456789abcdef")

        // then: the current bytes are answered, and the other hash is not
        assertThat(current?.body).describedAs("body of the current hash").isEqualTo(PluginStylesheetValidator.wrap(validSheet).toByteArray(UTF_8))
        assertThat(stale).describedAs("sheet of a stale hash").isNull()
    }

    @Test
    fun `should refuse a plugin name that the path cannot carry`() {
        // given: a sheet registered under a plugin name with a capital letter
        val log = CapturedLog()
        val facade = PluginUiFacade(logger = log.logger, enabled = { true })

        // when: the sheet is registered through a plugin with that name
        @Plugin(name = "Example")
        class UpperCasePlugin : ReposilitePlugin() {
            override fun initialize(): Facade? = null
        }
        val accepted = facade.forPlugin(UpperCasePlugin(), readerOf("dashboard.css" to validSheet)).registerStylesheet("dashboard.css")

        // then: the sheet is dropped with the name in the warning
        assertThat(accepted).describedAs("sheet accepted").isFalse()
        assertThat(log.text).describedAs("warning").contains("Example")
    }

    private fun hashOf(sheet: String): String = sha256Hex(PluginStylesheetValidator.wrap(sheet).toByteArray(UTF_8)).take(16)
}
