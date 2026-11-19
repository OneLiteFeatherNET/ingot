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
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.fixtures.CapturedLog
import com.reposilite.plugin.fixtures.EmbeddedPluginFixture
import com.reposilite.plugin.fixtures.FixturePluginOne
import com.reposilite.plugin.fixtures.PluginJarFixture
import net.onelitefeather.ingot.plugin.embedded.EmbeddedPluginSource
import net.onelitefeather.ingot.plugin.tier.PluginOrigin
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import com.reposilite.plugin.api.PluginTier

/**
 * The embedded jar is packed into a server jar that the test writes, and the extraction directories are temporary directories of
 * the test. The start log is read from a captured logger, and no test changes the process environment.
 */
internal class EmbeddedPluginLoadingTest {

    @TempDir
    lateinit var root: Path

    private val journalist = object : Journalist {
        override fun getLogger(): Logger = captured.logger
    }
    private val captured = CapturedLog()

    private val pluginsDirectory: Path by lazy { root.resolve("plugins").createDirectories() }
    private val bundledDirectory: Path by lazy { root.resolve("bundled-plugins").createDirectories() }
    private val working: Path by lazy { root.resolve("work").createDirectories() }
    private val fallbackRoot: Path by lazy { root.resolve("tmp").createDirectories() }

    /** A server jar of the test that embeds the given jars under embedded-plugins, by their names. */
    private fun serverEmbedding(vararg jars: Path): Path =
        EmbeddedPluginFixture.serverJar(
            root.resolve("server"),
            jars.associate { "embedded-plugins/${it.fileName}" to it.readBytes() },
        )

    private fun loader(server: Path?, environment: Map<String, String> = emptyMap()) = PluginLoader(
        pluginsDirectory = pluginsDirectory,
        extensions = Extensions(journalist),
        bundledPluginsDirectory = bundledDirectory,
        environment = environment,
        embeddedPluginSource = EmbeddedPluginSource(server),
        embeddedPluginsDirectory = working.resolve("embedded-plugins"),
        embeddedFallbackDirectory = fallbackRoot,
    )

    @Test
    fun `should register an embedded plugin with origin EMBEDDED and tier CORE`() {
        // given: a server jar that embeds a plugin with the id embedded-fixture
        val jar = EmbeddedPluginFixture.compileJar(root.resolve("build"), "embedded-fixture.jar", "com.reposilite.plugin.embedded.fixture.EmbeddedFixturePlugin", "embedded-fixture")
        val loader = loader(serverEmbedding(jar))

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the plugin is registered with the origin EMBEDDED and shown as Core
        assertThat(loader.extensions.originOf("embedded-fixture"))
            .describedAs("the origin of the embedded plugin")
            .isEqualTo(PluginOrigin.EMBEDDED)
        assertThat(loader.extensions.effectiveTierOf("embedded-fixture"))
            .describedAs("the effective tier of the embedded plugin")
            .isEqualTo(PluginTier.CORE)
    }

    @Test
    fun `should load the embedded plugin with its own class loader outside the server loader`() {
        // given: a server jar that embeds a plugin class that exists in no class path of the test run
        val jar = EmbeddedPluginFixture.compileJar(root.resolve("build"), "embedded-fixture.jar", "com.reposilite.plugin.embedded.fixture.IsolatedFixturePlugin", "isolated-fixture")
        val loader = loader(serverEmbedding(jar))

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the plugin object comes from a class loader of its own, whose only jar is the extracted one
        val plugin = loader.extensions.getPlugins().getValue("isolated-fixture").plugin
        val classLoader = plugin.javaClass.classLoader
        assertThat(classLoader)
            .describedAs("the class loader of the embedded plugin")
            .isNotSameAs(ReposilitePlugin::class.java.classLoader)
            .isInstanceOf(URLClassLoader::class.java)
        assertThat((classLoader as URLClassLoader).urLs.map { it.toString() })
            .describedAs("the jar of the class loader is the extracted copy")
            .containsExactly(working.resolve("embedded-plugins").resolve("embedded-fixture.jar").toUri().toURL().toString())
    }

    @Test
    fun `should skip a bundled jar whose plugin id an embedded plugin holds`() {
        // given: an embedded plugin with the id fixture and a bundled jar with the same id, switched on
        val embedded = EmbeddedPluginFixture.compileJar(root.resolve("build"), "fixture-embedded.jar", "com.reposilite.plugin.embedded.fixture.EmbeddedNamedFixture", "fixture")
        PluginJarFixture.write(bundledDirectory, "prometheus-plugin.jar", FixturePluginOne::class)
        val loader = loader(serverEmbedding(embedded), environment = mapOf("INGOT_METRICS_ENABLED" to "true"))

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the embedded plugin is the one registered, and the bundled jar is skipped
        assertThat(loader.extensions.originOf("fixture"))
            .describedAs("the registered plugin with the id fixture")
            .isEqualTo(PluginOrigin.EMBEDDED)
    }

    @Test
    fun `should stop the start when an embedded plugin id is already held by a built-in plugin`() {
        // given: a built-in plugin with the id fixture and an embedded plugin with the same id
        val embedded = EmbeddedPluginFixture.compileJar(root.resolve("build"), "fixture-embedded.jar", "com.reposilite.plugin.embedded.fixture.DuplicateFixturePlugin", "fixture")
        val loader = loader(serverEmbedding(embedded))
        loader.extensions.registerPlugin(FixturePluginOne(), PluginOrigin.BUILT_IN)

        // when / then: the start stops with the existing duplicate message
        assertThatThrownBy { loader.loadPluginsByServiceFiles() }
            .describedAs("a duplicate id of a built-in plugin")
            .hasMessageContaining("Plugin fixture is already registered")
    }

    @Test
    fun `should keep the embedded copy when a plugins directory jar without a version has the id of an embedded plugin`() {
        // given: a jar of the plugins directory with the id fixture, without a manifest version, and an embedded plugin with the same id
        PluginJarFixture.write(pluginsDirectory, "manual.jar", FixturePluginOne::class)
        val embedded = EmbeddedPluginFixture.compileJar(root.resolve("build"), "fixture-embedded.jar", "com.reposilite.plugin.embedded.fixture.ManualDuplicatePlugin", "fixture")
        val loader = loader(serverEmbedding(embedded))

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the embedded copy is used and the start goes on, as a missing version counts as lowest
        assertThat(loader.extensions.originOf("fixture"))
            .describedAs("the origin of the registered plugin with the id fixture")
            .isEqualTo(PluginOrigin.EMBEDDED)
    }

    @Test
    fun `should register the same plugins and create no directory when no embedded jar is in the server`() {
        // given: a plugin in the plugins directory and a server jar without embedded entries
        PluginJarFixture.write(pluginsDirectory, "fixture.jar", FixturePluginOne::class)
        val loader = loader(EmbeddedPluginFixture.serverJar(root.resolve("server"), mapOf("com/example/Main.class" to byteArrayOf(1))))

        // when: the plugins are loaded and the start log is written
        loader.loadPluginsByServiceFiles()
        val linesBeforeStartLog = captured.lines.size
        loader.logPluginList(listOf(loader.extensions.getPlugins().getValue("fixture")))

        // then: the plugin is registered as before, no embedded directory exists and the start log is unchanged
        assertThat(loader.extensions.originOf("fixture"))
            .describedAs("the origin of the plugin of the plugins directory")
            .isEqualTo(PluginOrigin.PLUGINS_DIRECTORY)
        assertThat(working.resolve("embedded-plugins").exists())
            .describedAs("the extraction directory without embedded jars")
            .isFalse()
        assertThat(captured.lines.drop(linesBeforeStartLog))
            .describedAs("the start log without embedded jars")
            .containsExactly("fixture", "fixture (community)")
    }

}
