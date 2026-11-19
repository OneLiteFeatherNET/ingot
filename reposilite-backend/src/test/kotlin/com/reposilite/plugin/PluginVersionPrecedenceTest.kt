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
import com.reposilite.plugin.api.PluginTier
import com.reposilite.plugin.fixtures.CapturedLog
import com.reposilite.plugin.fixtures.EmbeddedPluginFixture
import net.onelitefeather.ingot.plugin.embedded.EmbeddedPluginSource
import net.onelitefeather.ingot.plugin.tier.PluginOrigin
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes

/**
 * Each test embeds a plugin with the id `fixture` at version 1.0.0 in the server jar, and puts a jar of the plugins directory
 * beside it. The decision is read from the origin and the class of the registered plugin, and from the start log, which is
 * read from a captured logger.
 */
internal class PluginVersionPrecedenceTest {

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

    private val embeddedClass = "com.reposilite.plugin.embedded.precedence.EmbeddedFixture"

    /** The server jar with the embedded `fixture` plugin at version 1.0.0, or at the given version. */
    private fun serverEmbeddingFixture(version: String? = "1.0.0", tier: String? = null): Path {
        val jar = EmbeddedPluginFixture.compileJar(
            root.resolve("build-embedded"), "fixture-embedded.jar", embeddedClass, "fixture", version = version, tier = tier,
        )
        return EmbeddedPluginFixture.serverJar(
            root.resolve("server"),
            mapOf("embedded-plugins/fixture-embedded.jar" to jar.readBytes()),
        )
    }

    /** Compiles a plugin of the plugins directory with the id [pluginId] and copies its jar into the plugins directory. */
    private fun pluginsDirectoryJar(
        fileName: String,
        className: String,
        pluginId: String = "fixture",
        version: String? = null,
        tier: String? = null,
    ): Path {
        val built = EmbeddedPluginFixture.compileJar(root.resolve("build-$fileName"), fileName, className, pluginId, version, tier)
        return Files.copy(built, pluginsDirectory.resolve(fileName))
    }

    private fun loader(server: Path) = PluginLoader(
        pluginsDirectory = pluginsDirectory,
        extensions = Extensions(journalist),
        bundledPluginsDirectory = bundledDirectory,
        environment = emptyMap(),
        embeddedPluginSource = EmbeddedPluginSource(server),
        embeddedPluginsDirectory = working.resolve("embedded-plugins"),
        embeddedFallbackDirectory = fallbackRoot,
    )

    /** The start log lines of the replacement decision, which name the plugin id and both versions. */
    private fun decisionLines(): List<String> =
        captured.lines.filter { "Plugin fixture: embedded version" in it }

    @Test
    fun `should register the plugins directory copy when its version is higher than the embedded version`() {
        // given: an embedded fixture at 1.0.0 and a plugins directory copy of the same id at 1.1.0
        val server = serverEmbeddingFixture("1.0.0")
        pluginsDirectoryJar("upgrade.jar", "com.reposilite.plugin.precedence.UpgradeFixture", version = "1.1.0")
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the plugins directory copy is registered and the embedded copy is not
        assertThat(loader.extensions.originOf("fixture"))
            .describedAs("the origin of the registered plugin with the id fixture")
            .isEqualTo(PluginOrigin.PLUGINS_DIRECTORY)
        assertThat(loader.extensions.getPlugins().getValue("fixture").plugin::class.java.name)
            .describedAs("the class of the registered plugin with the id fixture")
            .isEqualTo("com.reposilite.plugin.precedence.UpgradeFixture")
    }

    @Test
    fun `should keep the embedded copy when the plugins directory version is equal`() {
        // given: an embedded fixture at 1.0.0 and a plugins directory copy of the same id at 1.0.0
        val server = serverEmbeddingFixture("1.0.0")
        pluginsDirectoryJar("equal.jar", "com.reposilite.plugin.precedence.EqualFixture", version = "1.0.0")
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the embedded copy is registered and the plugins directory copy is not
        assertThat(loader.extensions.originOf("fixture"))
            .describedAs("the origin of the registered plugin with the id fixture")
            .isEqualTo(PluginOrigin.EMBEDDED)
        assertThat(loader.extensions.getPlugins().getValue("fixture").plugin::class.java.name)
            .describedAs("the class of the registered plugin with the id fixture")
            .isEqualTo(embeddedClass)
    }

    @Test
    fun `should keep the embedded copy when the plugins directory version is lower`() {
        // given: an embedded fixture at 1.0.0 and a plugins directory copy of the same id at 0.9.0
        val server = serverEmbeddingFixture("1.0.0")
        pluginsDirectoryJar("lower.jar", "com.reposilite.plugin.precedence.LowerFixture", version = "0.9.0")
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the embedded copy is registered and the plugins directory copy is not
        assertThat(loader.extensions.originOf("fixture"))
            .describedAs("the origin of the registered plugin with the id fixture")
            .isEqualTo(PluginOrigin.EMBEDDED)
        assertThat(loader.extensions.getPlugins().getValue("fixture").plugin::class.java.name)
            .describedAs("the class of the registered plugin with the id fixture")
            .isEqualTo(embeddedClass)
    }

    @Test
    fun `should keep the embedded copy and go on when the plugins directory jar has no version`() {
        // given: an embedded fixture at 1.0.0 and a plugins directory copy of the same id without a manifest version
        val server = serverEmbeddingFixture("1.0.0")
        pluginsDirectoryJar("no-version.jar", "com.reposilite.plugin.precedence.NoVersionFixture")
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the embedded copy is used, the start goes on, and the line names the missing version
        assertThat(loader.extensions.originOf("fixture"))
            .describedAs("the origin of the registered plugin with the id fixture")
            .isEqualTo(PluginOrigin.EMBEDDED)
        assertThat(decisionLines().single())
            .describedAs("the decision line for a missing plugins directory version")
            .contains("plugins directory version none, the embedded copy is used")
    }

    @Test
    fun `should keep the embedded copy and go on when the plugins directory version is invalid`() {
        // given: an embedded fixture at 1.0.0 and a plugins directory copy of the same id with the version latest
        val server = serverEmbeddingFixture("1.0.0")
        pluginsDirectoryJar("latest.jar", "com.reposilite.plugin.precedence.LatestFixture", version = "latest")
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the embedded copy is used, and the line shows the raw version in quotes
        assertThat(loader.extensions.originOf("fixture"))
            .describedAs("the origin of the registered plugin with the id fixture")
            .isEqualTo(PluginOrigin.EMBEDDED)
        assertThat(decisionLines().single())
            .describedAs("the decision line for an invalid plugins directory version")
            .contains("plugins directory version \"latest\", the embedded copy is used")
    }

    @Test
    fun `should log one INFO line naming both versions when the plugins directory copy replaces the embedded copy`() {
        // given: an embedded fixture at 1.0.0 and a plugins directory copy at 1.1.0
        val server = serverEmbeddingFixture("1.0.0")
        pluginsDirectoryJar("upgrade.jar", "com.reposilite.plugin.precedence.UpgradeFixture", version = "1.1.0")
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: exactly one decision line in the form of the spec is logged
        assertThat(decisionLines())
            .describedAs("the decision lines of the replacement")
            .hasSize(1)
        assertThat(decisionLines().single())
            .describedAs("the decision line of the replacement")
            .contains("Plugin fixture: embedded version 1.0.0, plugins directory version 1.1.0, the plugins directory copy is used")
    }

    @Test
    fun `should log one INFO line naming the embedded copy when the plugins directory copy loses`() {
        // given: an embedded fixture at 1.0.0 and a plugins directory copy at 0.9.0
        val server = serverEmbeddingFixture("1.0.0")
        pluginsDirectoryJar("lower.jar", "com.reposilite.plugin.precedence.LowerFixture", version = "0.9.0")
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: exactly one decision line names both versions and says the embedded copy is used
        assertThat(decisionLines())
            .describedAs("the decision lines of the kept embedded copy")
            .hasSize(1)
        assertThat(decisionLines().single())
            .describedAs("the decision line of the kept embedded copy")
            .contains("Plugin fixture: embedded version 1.0.0, plugins directory version 0.9.0, the embedded copy is used")
    }

    @Test
    fun `should log no decision line when no plugins directory jar declares the embedded id`() {
        // given: an embedded fixture at 1.0.0 and a plugins directory jar with another id
        val server = serverEmbeddingFixture("1.0.0")
        pluginsDirectoryJar("other.jar", "com.reposilite.plugin.precedence.OtherFixture", pluginId = "other-fixture", version = "2.0.0")
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: no line about a replacement decision is logged
        assertThat(decisionLines())
            .describedAs("the decision lines without a plugins directory copy")
            .isEmpty()
    }

    @Test
    fun `should stop the start when two plugins directory jars share an id that an embedded jar holds`() {
        // given: an embedded fixture at 1.0.0 and two plugins directory copies of the same id, both higher
        val server = serverEmbeddingFixture("1.0.0")
        pluginsDirectoryJar("first.jar", "com.reposilite.plugin.precedence.FirstFixture", version = "1.1.0")
        pluginsDirectoryJar("second.jar", "com.reposilite.plugin.precedence.SecondFixture", version = "1.2.0")
        val loader = loader(server)

        // when / then: the start stops with the existing duplicate message
        assertThatThrownBy { loader.loadPluginsByServiceFiles() }
            .describedAs("two plugins directory copies of one id")
            .hasMessageContaining("Plugin fixture is already registered")
    }

    @Test
    fun `should show a replacing copy that declares CORE as COMMUNITY`() {
        // given: an embedded fixture at 1.0.0 and a higher plugins directory copy that declares CORE
        val server = serverEmbeddingFixture("1.0.0")
        pluginsDirectoryJar("core.jar", "com.reposilite.plugin.precedence.CoreFixture", version = "1.1.0", tier = "CORE")
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the replacing copy keeps its origin and reads as Community
        assertThat(loader.extensions.effectiveTierOf("fixture"))
            .describedAs("the effective tier of the replacing copy that declares CORE")
            .isEqualTo(PluginTier.COMMUNITY)
    }

    @Test
    fun `should show a replacing copy that declares OFFICIAL as OFFICIAL`() {
        // given: an embedded fixture at 1.0.0 and a higher plugins directory copy that declares OFFICIAL
        val server = serverEmbeddingFixture("1.0.0")
        pluginsDirectoryJar("official.jar", "com.reposilite.plugin.precedence.OfficialFixture", version = "1.1.0", tier = "OFFICIAL")
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the replacing copy reads as Official
        assertThat(loader.extensions.effectiveTierOf("fixture"))
            .describedAs("the effective tier of the replacing copy that declares OFFICIAL")
            .isEqualTo(PluginTier.OFFICIAL)
    }

    @Test
    fun `should load a plugins directory jar whose id no embedded jar holds as before`() {
        // given: an embedded fixture at 1.0.0 and a plugins directory jar with the id other-fixture
        val server = serverEmbeddingFixture("1.0.0")
        pluginsDirectoryJar("other.jar", "com.reposilite.plugin.precedence.OtherFixture", pluginId = "other-fixture", version = "0.1.0")
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the other plugin is registered with the plugins directory origin, and the embedded fixture is unchanged
        assertThat(loader.extensions.originOf("other-fixture"))
            .describedAs("the origin of the plugin whose id no embedded jar holds")
            .isEqualTo(PluginOrigin.PLUGINS_DIRECTORY)
        assertThat(loader.extensions.originOf("fixture"))
            .describedAs("the origin of the embedded fixture")
            .isEqualTo(PluginOrigin.EMBEDDED)
    }

}
