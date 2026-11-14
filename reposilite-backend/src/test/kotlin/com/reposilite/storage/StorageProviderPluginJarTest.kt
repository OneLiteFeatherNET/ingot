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

package com.reposilite.storage

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.PluginLoader
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.storage.fixtures.FixtureStorageProviderFactory
import com.reposilite.storage.fixtures.FixtureStorageSettings
import com.reposilite.storage.fixtures.StorageProbePlugin
import com.reposilite.storage.fixtures.StorageProviderJarFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.createDirectories

class StorageProviderPluginJarTest {

    private val journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(System.out, System.err)
    }

    @TempDir
    lateinit var root: Path

    private fun factoryJar(fileName: String): Path =
        StorageProviderJarFixture.write(
            directory = root.resolve("jars").createDirectories(),
            fileName = fileName,
            services = mapOf(StorageProviderFactory::class.java to listOf(FixtureStorageProviderFactory::class.java)),
        )

    @Test
    fun `should not list a plugin storage provider on the application class path`() {
        factoryJar("fixture-storage.jar")

        val applicationFactories = ServiceLoader.load(StorageProviderFactory::class.java).map { it.type }

        assertThat(applicationFactories).describedAs("factories on the application class path").doesNotContain("fixture")
    }

    @Test
    fun `should find the storage provider of a plugin jar through its class loader`() {
        val jar = factoryJar("fixture-storage.jar")
        val pluginLoader = URLClassLoader(arrayOf(jar.toUri().toURL()), StorageProviderFactory::class.java.classLoader)
        val registry = StorageProviderRegistry()

        registry.discover(pluginLoader, builtIn = false)

        assertThat(registry.resolve("fixture")).describedAs("fixture factory of the plugin jar").isNotNull()
        assertThat(registry.settingsType("fixture")).describedAs("settings type of the fixture factory").isEqualTo(FixtureStorageSettings::class.java)
    }

    @Test
    fun `should register the storage provider of a plugin jar before any plugin initialises`() {
        val pluginsDirectory = root.resolve("plugins").createDirectories()
        StorageProviderJarFixture.write(
            directory = pluginsDirectory,
            fileName = "probe-with-storage.jar",
            services = mapOf(
                ReposilitePlugin::class.java to listOf(StorageProbePlugin::class.java),
                StorageProviderFactory::class.java to listOf(FixtureStorageProviderFactory::class.java),
            ),
        )
        val extensions = Extensions(journalist)
        val loader = PluginLoader(
            pluginsDirectory = pluginsDirectory,
            extensions = extensions,
            bundledPluginsDirectory = root.resolve("no-bundled-plugins"),
            environment = emptyMap(),
        )

        loader.loadPluginsByServiceFiles()
        loader.initialize()

        val probe = extensions.getPlugins().getValue("storage-probe").plugin as StorageProbePlugin
        assertThat(probe.fixtureVisibleAtInitialize)
            .describedAs("fixture storage provider visible while the probe plugin initialises")
            .isTrue()
        assertThat(extensions.storageProviders.resolve("fixture"))
            .describedAs("fixture storage provider after start")
            .isNotNull()
    }

}
