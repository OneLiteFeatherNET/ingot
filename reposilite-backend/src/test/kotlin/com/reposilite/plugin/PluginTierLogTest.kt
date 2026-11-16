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

import com.reposilite.journalist.Channel
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.PluginTier
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.fixtures.PluginJarFixture
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The start log is asserted on the lines a real logger writes into memory, so no test reads the console. The plugins come
 * from jars that each test writes into its own temporary directory, and the loader never initialises them.
 */
internal class PluginTierLogTest {

    @Plugin(name = "alpha")
    class Alpha : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Plugin(name = "bravo", tier = PluginTier.OFFICIAL)
    class Bravo : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Plugin(name = "charlie", tier = PluginTier.EXPERIMENTAL)
    class Charlie : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Plugin(name = "delta", tier = PluginTier.CORE)
    class Delta : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Plugin(name = "echo")
    class Echo : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Plugin(name = "foxtrot")
    class Foxtrot : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @TempDir
    lateinit var root: Path

    private val pluginsDirectory: Path by lazy { root.resolve("plugins").createDirectories() }
    private val bundledDirectory: Path by lazy { root.resolve("bundled-plugins").createDirectories() }
    private val captured = CapturedLog()
    private val extensions = Extensions(object : Journalist {
        override fun getLogger(): Logger = captured.logger
    })

    @Test
    fun `should keep the loaded plugins line byte for byte and chunk it by five`() {
        // when: the plugins of six jars are loaded and the start log is written
        val lines = startWith(Alpha::class, Bravo::class, Charlie::class, Delta::class, Echo::class, Foxtrot::class)

        // then: the first lines are the names in chunks of five, exactly as before the tiers existed
        assertThat(lines.take(2))
            .describedAs("the existing loaded plugins lines")
            .containsExactly("alpha, bravo, charlie, delta, echo", "foxtrot")
    }

    @Test
    fun `should log one tier line directly after the loaded plugins line`() {
        // when: the plugins are loaded and the start log is written
        val lines = startWith(Alpha::class, Bravo::class, Charlie::class, Delta::class, Echo::class, Foxtrot::class)

        // then: the tier line follows the two existing lines and lists every plugin with its effective tier
        assertThat(lines[2])
            .describedAs("the tier line after the existing lines")
            .isEqualTo("alpha (community), bravo (official), charlie (experimental), delta (community), echo (community), foxtrot (community)")
    }

    @Test
    fun `should log exactly one warning per experimental plugin and no other tier warning`() {
        // when: the plugins are loaded and the start log is written
        val lines = startWith(Alpha::class, Bravo::class, Charlie::class, Delta::class, Echo::class, Foxtrot::class)
        val warnings = lines.drop(3)

        // then: the Experimental plugin has its warning, and the Core claim is the only other tier warning
        assertThat(warnings)
            .describedAs("warning lines after the tier line")
            .hasSize(2)
        assertThat(warnings.filter { it.contains("EXPERIMENTAL") })
            .describedAs("warnings about the experimental plugin")
            .hasSize(1)
        assertThat(warnings.single { it.contains("EXPERIMENTAL") })
            .describedAs("the experimental warning names the plugin")
            .contains("charlie")
    }

    @Test
    fun `should resolve a core claim from outside the image to community with one warning`() {
        // when: the plugins are loaded and the start log is written
        val lines = startWith(Alpha::class, Bravo::class, Charlie::class, Delta::class, Echo::class, Foxtrot::class)

        // then: the tier line shows the claim as community, and one warning names the plugin and the image
        assertThat(lines[2])
            .describedAs("the tier line shows the Core claim as community")
            .contains("delta (community)")
        val warnings = lines.drop(3).filter { it.contains("CORE") }
        assertThat(warnings)
            .describedAs("warnings about the Core claim")
            .hasSize(1)
        assertThat(warnings.single())
            .describedAs("the Core warning names the plugin and says it is not shipped in the image")
            .contains("delta")
            .contains("not shipped in the image")
    }

    @Test
    fun `should resolve a jar of the bundled directory that declares official to core`() {
        // given: a bundled jar with the metrics switch on, declaring the official fixture
        PluginJarFixture.write(bundledDirectory, "prometheus-plugin.jar", Bravo::class)
        val loader = PluginLoader(
            pluginsDirectory = pluginsDirectory,
            extensions = extensions,
            bundledPluginsDirectory = bundledDirectory,
            environment = mapOf("INGOT_METRICS_ENABLED" to "true"),
        )
        loader.loadPluginsByServiceFiles()

        // when: the start log is written for that plugin
        loader.logPluginList(listOf(extensions.getPlugins().getValue("bravo")))

        // then: the bundled origin makes it core, whatever it declares
        assertThat(captured.lines)
            .describedAs("tier line of the bundled plugin")
            .contains("bravo (core)")
    }

    /** Writes one jar per class into the plugins directory, loads them and writes the start log for exactly those plugins. */
    private fun startWith(vararg classes: KClass<out ReposilitePlugin>): List<String> {
        classes.forEach { PluginJarFixture.write(pluginsDirectory, "${it.simpleName}.jar", it) }
        val loader = PluginLoader(
            pluginsDirectory = pluginsDirectory,
            extensions = extensions,
            bundledPluginsDirectory = root.resolve("no-bundled-plugins"),
            environment = emptyMap(),
        )
        loader.loadPluginsByServiceFiles()

        val entries = classes.map { extensions.getPlugins().getValue(requireNotNull(it.findAnnotation<Plugin>()).name) }
        loader.logPluginList(entries)
        return captured.lines
    }

    /** A real logger whose output goes to memory, read as lines. Only INFO and above is written, as at start. */
    private class CapturedLog {
        private val buffer = ByteArrayOutputStream()
        private val stream = PrintStream(buffer, true, UTF_8)
        val logger: Logger = PrintStreamLogger(stream, stream, Channel.INFO, true)

        val lines: List<String>
            get() {
                stream.flush()
                return buffer.toString(UTF_8).lines().filter { it.isNotEmpty() }
            }
    }

}
