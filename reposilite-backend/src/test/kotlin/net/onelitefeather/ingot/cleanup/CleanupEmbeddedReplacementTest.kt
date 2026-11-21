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

package net.onelitefeather.ingot.cleanup

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.PluginLoader
import com.reposilite.plugin.fixtures.CapturedLog
import com.reposilite.plugin.fixtures.EmbeddedPluginFixture
import net.onelitefeather.ingot.plugin.embedded.EmbeddedPluginSource
import net.onelitefeather.ingot.plugin.tier.PluginOrigin
import net.onelitefeather.ingot.plugin.version.PluginVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.Attributes
import java.util.jar.JarFile
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes

/**
 * The server jar of each test embeds the shipped `cleanup-plugin.jar`, which the build passes in the system property
 * `ingot.cleanupPlugin.jar`, and the plugins directory in a temporary directory holds a jar with the plugin id `cleanup`. The
 * decision is read from the origin and the class of the registered `cleanup` plugin, and from the captured start log.
 */
internal class CleanupEmbeddedReplacementTest {

    @TempDir
    lateinit var root: Path

    private val captured = CapturedLog()
    private val journalist = object : Journalist {
        override fun getLogger(): Logger = captured.logger
    }

    private val pluginsDirectory: Path by lazy { root.resolve("plugins").createDirectories() }

    private val shippedJar: Path by lazy {
        val property = System.getProperty("ingot.cleanupPlugin.jar")
        assertThat(property)
            .describedAs("the system property ingot.cleanupPlugin.jar that names the shipped cleanup-plugin.jar")
            .isNotBlank()
        Paths.get(property)
    }

    /** The `Implementation-Version` of the shipped jar, which is the embedded version every decision compares with. */
    private val embeddedVersion: String by lazy {
        val version = JarFile(shippedJar.toFile()).use { it.manifest?.mainAttributes?.getValue(Attributes.Name.IMPLEMENTATION_VERSION) }
        assertThat(PluginVersion.parse(version))
            .describedAs("the Implementation-Version of the shipped cleanup-plugin.jar, read as a plugin version: $version")
            .isNotNull()
        version!!
    }

    private val embeddedClass = "com.reposilite.cleanup.application.CleanupPlugin"

    private fun serverEmbeddingCleanup(): Path =
        EmbeddedPluginFixture.serverJar(
            root.resolve("server"),
            mapOf("embedded-plugins/cleanup-plugin.jar" to shippedJar.readBytes()),
        )

    /** Compiles a plugin with the id `cleanup` and copies its jar into the plugins directory, with [version] in its manifest. */
    private fun pluginsDirectoryCleanup(fileName: String, className: String, version: String?): Path {
        val built = EmbeddedPluginFixture.compileJar(root.resolve("build-$fileName"), fileName, className, "cleanup", version)
        return Files.copy(built, pluginsDirectory.resolve(fileName))
    }

    private fun loader(server: Path) = PluginLoader(
        pluginsDirectory = pluginsDirectory,
        extensions = Extensions(journalist),
        bundledPluginsDirectory = root.resolve("bundled-plugins").createDirectories(),
        environment = emptyMap(),
        embeddedPluginSource = EmbeddedPluginSource(server),
        embeddedPluginsDirectory = root.resolve("work").resolve("embedded-plugins"),
        embeddedFallbackDirectory = root.resolve("tmp").createDirectories(),
    )

    private fun decisionLines(): List<String> =
        captured.lines.filter { "Plugin cleanup: embedded version" in it }

    private fun higherThanEmbedded(): String =
        "${embeddedVersion.substringBefore('.').toInt() + 1}.0.0"

    private fun lowerThanEmbedded(): String =
        "0.0.1".also { lower ->
            assertThat(PluginVersion.compareOrLowest(PluginVersion.parse(lower), PluginVersion.parse(embeddedVersion)))
                .describedAs("the lower version $lower compared with the embedded version $embeddedVersion")
                .isNegative()
        }

    @Test
    fun `should replace the embedded cleanup copy with a plugins directory jar of a higher version`() {
        // given: the shipped cleanup jar embedded in the server, and a cleanup jar of a higher version in the plugins directory
        val server = serverEmbeddingCleanup()
        val higher = higherThanEmbedded()
        pluginsDirectoryCleanup("cleanup-higher.jar", "com.reposilite.cleanup.replacement.HigherCleanup", higher)
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the plugins directory copy is registered, and one line names both versions and the copy in use
        assertThat(loader.extensions.originOf("cleanup"))
            .describedAs("the origin of the registered cleanup plugin")
            .isEqualTo(PluginOrigin.PLUGINS_DIRECTORY)
        assertThat(loader.extensions.getPlugins().getValue("cleanup").plugin::class.java.name)
            .describedAs("the class of the registered cleanup plugin")
            .isEqualTo("com.reposilite.cleanup.replacement.HigherCleanup")
        assertThat(decisionLines())
            .describedAs("the start log lines of the cleanup replacement decision")
            .singleElement()
            .asString()
            .contains("Plugin cleanup: embedded version $embeddedVersion, plugins directory version $higher, the plugins directory copy is used")
    }

    @Test
    fun `should keep the embedded cleanup copy when the plugins directory jar has the same version`() {
        // given: the shipped cleanup jar embedded in the server, and a cleanup jar of the same version in the plugins directory
        val server = serverEmbeddingCleanup()
        pluginsDirectoryCleanup("cleanup-equal.jar", "com.reposilite.cleanup.replacement.EqualCleanup", embeddedVersion)
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the embedded copy is registered, and one line names both versions and the embedded copy
        assertThat(loader.extensions.originOf("cleanup"))
            .describedAs("the origin of the registered cleanup plugin")
            .isEqualTo(PluginOrigin.EMBEDDED)
        assertThat(loader.extensions.getPlugins().getValue("cleanup").plugin::class.java.name)
            .describedAs("the class of the registered cleanup plugin")
            .isEqualTo(embeddedClass)
        assertThat(decisionLines())
            .describedAs("the start log lines of the cleanup replacement decision")
            .singleElement()
            .asString()
            .contains("Plugin cleanup: embedded version $embeddedVersion, plugins directory version $embeddedVersion, the embedded copy is used")
    }

    @Test
    fun `should keep the embedded cleanup copy when the plugins directory jar has a lower version`() {
        // given: the shipped cleanup jar embedded in the server, and a cleanup jar of a lower version in the plugins directory
        val server = serverEmbeddingCleanup()
        val lower = lowerThanEmbedded()
        pluginsDirectoryCleanup("cleanup-lower.jar", "com.reposilite.cleanup.replacement.LowerCleanup", lower)
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the embedded copy is registered, and one line names both versions and the embedded copy
        assertThat(loader.extensions.originOf("cleanup"))
            .describedAs("the origin of the registered cleanup plugin")
            .isEqualTo(PluginOrigin.EMBEDDED)
        assertThat(loader.extensions.getPlugins().getValue("cleanup").plugin::class.java.name)
            .describedAs("the class of the registered cleanup plugin")
            .isEqualTo(embeddedClass)
        assertThat(decisionLines())
            .describedAs("the start log lines of the cleanup replacement decision")
            .singleElement()
            .asString()
            .contains("Plugin cleanup: embedded version $embeddedVersion, plugins directory version $lower, the embedded copy is used")
    }

    @Test
    fun `should keep the embedded cleanup copy when the plugins directory jar has no version`() {
        // given: the shipped cleanup jar embedded in the server, and a cleanup jar without a manifest version in the plugins directory
        val server = serverEmbeddingCleanup()
        pluginsDirectoryCleanup("cleanup-unversioned.jar", "com.reposilite.cleanup.replacement.UnversionedCleanup", null)
        val loader = loader(server)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the embedded copy is registered, and one line names the embedded version and the missing one
        assertThat(loader.extensions.originOf("cleanup"))
            .describedAs("the origin of the registered cleanup plugin")
            .isEqualTo(PluginOrigin.EMBEDDED)
        assertThat(loader.extensions.getPlugins().getValue("cleanup").plugin::class.java.name)
            .describedAs("the class of the registered cleanup plugin")
            .isEqualTo(embeddedClass)
        assertThat(decisionLines())
            .describedAs("the start log lines of the cleanup replacement decision")
            .singleElement()
            .asString()
            .contains("Plugin cleanup: embedded version $embeddedVersion, plugins directory version none, the embedded copy is used")
    }

}
