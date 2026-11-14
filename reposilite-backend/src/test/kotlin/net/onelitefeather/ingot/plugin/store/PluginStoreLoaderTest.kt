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

package net.onelitefeather.ingot.plugin.store

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.PluginLoader
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.fixtures.FixturePluginOne
import com.reposilite.plugin.fixtures.OtherFixturePlugin
import com.reposilite.plugin.fixtures.PluginJarFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.reflect.KClass

/** A store plugin whose initialise throws, so that the store isolates its failure. */
@Plugin(name = "failing-store-fixture")
class FailingStorePlugin : ReposilitePlugin() {
    override fun initialize(): Facade? = throw IllegalStateException("boom from initialize")
}

/**
 * The loader with a store in front of the bundled jars. The store serves unsigned jars from memory, so these tests cover the
 * order, the shadowing and the failure isolation without keys or a network. Each test owns its directories.
 */
internal class PluginStoreLoaderTest {

    @TempDir
    lateinit var root: Path

    private val journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(System.out, System.err)
    }

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneOffset.UTC)

    private val pluginsDirectory: Path by lazy { root.resolve("plugins").createDirectories() }
    private val bundledDirectory: Path by lazy { root.resolve("bundled-plugins").createDirectories() }

    /** A store whose only plugin is the jar of [pluginClass], listed in the catalogue under [catalogueId]. */
    private fun storeServing(
        pluginClass: KClass<out ReposilitePlugin>,
        catalogueId: String,
        reachable: Boolean = true,
    ): Pair<PluginStore, FakeSourceTransport> {
        val jarFile = PluginJarFixture.write(root.resolve("store-source").createDirectories(), "store.jar", pluginClass)
        val jar = jarFile.readBytes()
        val catalogue = """
            {
              "schema": 1,
              "plugins": [ {
                "id": "$catalogueId",
                "name": "Store fixture",
                "publisher": "test-publisher",
                "coordinates": "$COORDINATE",
                "sha256": "${PluginDigest.sha256Hex(jar)}",
                "compatibility": { "ingot": { "min": "1.2.0", "below": "2.0.0" }, "pluginApi": 1 },
                "yanked": false
              } ]
            }
        """.trimIndent().toByteArray()
        val responses = if (reachable) {
            mapOf("$SOURCE/ingot-plugin-catalogue.json" to FetchResult.Found(catalogue), "$SOURCE/$JAR_PATH" to FetchResult.Found(jar))
        } else {
            mapOf("$SOURCE/ingot-plugin-catalogue.json" to FetchResult.Unreachable("connection refused"))
        }
        val transport = FakeSourceTransport(responses)
        val settings = PluginStoreSettings.read(
            mapOf("INGOT_PLUGINS" to COORDINATE, "INGOT_PLUGIN_SOURCES" to SOURCE, "INGOT_PLUGIN_ALLOW_UNSIGNED" to "true")
        )
        return PluginStore(
            settings = settings,
            ingotVersion = "1.5.0",
            cache = PluginCache(root.resolve("plugin-cache")),
            transport = transport,
            verifier = null,
            clock = clock,
        ) to transport
    }

    private fun loader(store: PluginStore?, environment: Map<String, String> = emptyMap()): Pair<PluginLoader, Extensions> {
        val extensions = Extensions(journalist)
        val loader = PluginLoader(
            pluginsDirectory = pluginsDirectory,
            extensions = extensions,
            bundledPluginsDirectory = bundledDirectory,
            environment = environment,
            pluginStore = store,
        )
        return loader to extensions
    }

    @Test
    fun `should keep the manual jar and shadow the store plugin that has the same id`() {
        // given: a manual jar in the plugins directory and a store plugin with the same id
        PluginJarFixture.write(pluginsDirectory, "manual.jar", FixturePluginOne::class)
        val (store, _) = storeServing(FixturePluginOne::class, catalogueId = "fixture")
        val (loader, extensions) = loader(store)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the store plugin is shadowed, and the manual jar is the one registered
        val entry = loader.storeEntries.single()
        assertThat(entry.state).describedAs("a manual jar must win over a store plugin with the same id").isEqualTo(StoreState.SHADOWED)
        assertThat(entry.reason).describedAs("the reason must name the plugins directory").contains("plugins directory")
        assertThat(extensions.getPlugins()["fixture"]).describedAs("the manual jar must stay registered").isNotNull
    }

    @Test
    fun `should load the store plugin and skip the bundled jar that has the same id`() {
        // given: a store plugin and a switched on bundled jar with the same id
        PluginJarFixture.write(bundledDirectory, "prometheus-plugin.jar", OtherFixturePlugin::class)
        val (store, _) = storeServing(OtherFixturePlugin::class, catalogueId = "other-fixture")
        val (loader, extensions) = loader(store, environment = mapOf("INGOT_METRICS_ENABLED" to "true"))

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the store plugin is loaded, and the start registers one plugin with that id
        assertThat(loader.storeEntries.single().state).describedAs("a store plugin must win over a bundled jar").isEqualTo(StoreState.LOADED)
        assertThat(extensions.getPlugins()["other-fixture"]).isNotNull
    }

    @Test
    fun `should start without the store plugin when its source cannot be reached`() {
        // given: a store whose source is unreachable and no cached copy
        val (store, _) = storeServing(FixturePluginOne::class, catalogueId = "fixture", reachable = false)
        val (loader, extensions) = loader(store)

        // when: the plugins are loaded and initialised
        loader.loadPluginsByServiceFiles()
        loader.initialize()

        // then: the start completes without the plugin
        assertThat(loader.storeEntries.single().state).isEqualTo(StoreState.UNAVAILABLE)
        assertThat(extensions.getPlugins()).describedAs("no plugin must be registered from an unavailable store").isEmpty()
    }

    @Test
    fun `should mark a store plugin that fails in initialize as failed and finish the start`() {
        // given: a store plugin whose initialise throws
        val (store, _) = storeServing(FailingStorePlugin::class, catalogueId = "failing-store-fixture")
        val (loader, _) = loader(store)

        // when: the plugins are loaded and initialised
        loader.loadPluginsByServiceFiles()
        loader.initialize()

        // then: the plugin is failed in the status, with the message of the exception, and the start completes
        val entry = loader.storeEntries.single()
        assertThat(entry.state).describedAs("a failing store plugin must be marked as failed").isEqualTo(StoreState.FAILED)
        assertThat(entry.reason).describedAs("the reason must carry the exception message").contains("boom from initialize")
    }

    @Test
    fun `should refuse a store jar whose plugin id differs from its catalogue entry`() {
        // given: a catalogue that names another id than the jar declares
        val (store, _) = storeServing(FixturePluginOne::class, catalogueId = "another-id")
        val (loader, extensions) = loader(store)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: the jar is refused and nothing is registered under either id
        val entry = loader.storeEntries.single()
        assertThat(entry.state).describedAs("an id that differs from the catalogue must be refused").isEqualTo(StoreState.REFUSED)
        assertThat(entry.reason).contains("another-id", "fixture")
        assertThat(extensions.getPlugins()).isEmpty()
    }

    @Test
    fun `should make no store request when the recovery switch is on`() {
        // given: a store with the recovery switch on
        val (store, transport) = storeServing(FixturePluginOne::class, catalogueId = "fixture")
        val recovery = PluginStore(
            settings = PluginStoreSettings.read(
                mapOf("INGOT_PLUGINS" to COORDINATE, "INGOT_PLUGIN_SOURCES" to SOURCE, "INGOT_PLUGIN_STORE_DISABLED" to "true")
            ),
            ingotVersion = "1.5.0",
            cache = PluginCache(root.resolve("plugin-cache")),
            transport = transport,
            verifier = null,
            clock = clock,
        )
        val (loader, _) = loader(recovery)

        // when: the plugins are loaded
        loader.loadPluginsByServiceFiles()

        // then: no request was made and no store entry exists
        assertThat(transport.requested).describedAs("the recovery switch must make no request").isEmpty()
        assertThat(loader.storeEntries).isEmpty()
    }

    private companion object {
        const val SOURCE = "https://repo.example.org/maven"
        const val COORDINATE = "org.example:fixture-store:1.0.0"
        const val JAR_PATH = "org/example/fixture-store/1.0.0/fixture-store-1.0.0.jar"
    }

}
