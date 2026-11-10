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
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.fixtures.FixturePluginOne
import com.reposilite.plugin.fixtures.FixturePluginTwo
import com.reposilite.plugin.fixtures.PluginJarFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * The bundled directory is a parameter of the loader and the environment is an injected map, so no test changes the
 * process environment or a system property.
 */
internal class BundledPluginLoadingTest {

    private val journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(System.out, System.err)
    }

    @TempDir
    lateinit var root: Path

    private val pluginsDirectory: Path by lazy { root.resolve("plugins").createDirectories() }
    private val bundledDirectory: Path by lazy { root.resolve("bundled-plugins").createDirectories() }

    private fun loader(extensions: Extensions, environment: Map<String, String>) =
        PluginLoader(
            pluginsDirectory = pluginsDirectory,
            extensions = extensions,
            bundledPluginsDirectory = bundledDirectory,
            environment = environment,
        )

    @Test
    fun `should not load the bundled plugin when the switch is unset`() {
        // given: a bundled jar that declares the fixture plugin, and no switch in the environment
        PluginJarFixture.write(bundledDirectory, "prometheus-plugin.jar", FixturePluginOne::class)
        val extensions = Extensions(journalist)

        // when: the plugins are loaded
        loader(extensions, environment = emptyMap()).loadPluginsByServiceFiles()

        // then: the bundled plugin is not registered
        assertThat(extensions.getPlugins()["fixture"])
            .describedAs("without the switch the bundled copy must not start")
            .isNull()
    }

    @Test
    fun `should load the bundled plugin when INGOT_METRICS_ENABLED is true`() {
        // given: a bundled jar that declares the fixture plugin, and the switch on
        PluginJarFixture.write(bundledDirectory, "prometheus-plugin.jar", FixturePluginOne::class)
        val extensions = Extensions(journalist)

        // when: the plugins are loaded
        loader(extensions, environment = mapOf("INGOT_METRICS_ENABLED" to "true")).loadPluginsByServiceFiles()

        // then: the bundled plugin is registered under its id
        assertThat(extensions.getPlugins()["fixture"]?.plugin)
            .describedAs("INGOT_METRICS_ENABLED=true must load the bundled plugin")
            .isInstanceOf(FixturePluginOne::class.java)
    }

    @Test
    fun `should load the bundled plugin on the legacy switch when the INGOT name is unset`() {
        // given: a bundled jar and only the legacy switch set
        PluginJarFixture.write(bundledDirectory, "prometheus-plugin.jar", FixturePluginOne::class)
        val extensions = Extensions(journalist)

        // when: the plugins are loaded
        loader(extensions, environment = mapOf("REPOSILITE_METRICS_ENABLED" to "true")).loadPluginsByServiceFiles()

        // then: the bundled plugin is registered
        assertThat(extensions.getPlugins()["fixture"]?.plugin)
            .describedAs("REPOSILITE_METRICS_ENABLED=true must load the bundled plugin when INGOT_METRICS_ENABLED is unset")
            .isInstanceOf(FixturePluginOne::class.java)
    }

    @Test
    fun `should let INGOT_METRICS_ENABLED=false override the legacy switch`() {
        // given: a bundled jar, the legacy switch on and the new switch off
        PluginJarFixture.write(bundledDirectory, "prometheus-plugin.jar", FixturePluginOne::class)
        val extensions = Extensions(journalist)

        // when: the plugins are loaded
        loader(
            extensions,
            environment = mapOf("INGOT_METRICS_ENABLED" to "false", "REPOSILITE_METRICS_ENABLED" to "true"),
        ).loadPluginsByServiceFiles()

        // then: the bundled plugin is not registered, because the set name decides
        assertThat(extensions.getPlugins()["fixture"])
            .describedAs("a set INGOT_METRICS_ENABLED must take precedence over the legacy name")
            .isNull()
    }

    @Test
    fun `should keep the operator's own jar when it declares the same plugin id`() {
        // given: the operator's jar in the plugins directory, and a bundled jar with the same plugin id
        PluginJarFixture.write(pluginsDirectory, "prometheus-plugin.jar", FixturePluginOne::class)
        PluginJarFixture.write(bundledDirectory, "prometheus-plugin.jar", FixturePluginTwo::class)
        val extensions = Extensions(journalist)

        // when: the plugins are loaded with the switch on
        loader(extensions, environment = mapOf("INGOT_METRICS_ENABLED" to "true")).loadPluginsByServiceFiles()

        // then: the operator's copy is the one that is registered, and start-up did not fail
        assertThat(extensions.getPlugins()["fixture"]?.plugin)
            .describedAs("the operator's jar must win over the bundled copy")
            .isInstanceOf(FixturePluginOne::class.java)
    }

    @Test
    fun `should start with the switch on and no bundled directory`() {
        // given: the switch on and a bundled directory that does not exist
        val missing = root.resolve("missing-bundled")
        val extensions = Extensions(journalist)

        // when: the plugins are loaded
        PluginLoader(
            pluginsDirectory = pluginsDirectory,
            extensions = extensions,
            bundledPluginsDirectory = missing,
            environment = mapOf("INGOT_METRICS_ENABLED" to "true"),
        ).loadPluginsByServiceFiles()

        // then: no bundled plugin is registered and nothing fails
        assertThat(extensions.getPlugins()["fixture"])
            .describedAs("a missing bundled directory must not stop the server")
            .isNull()
    }

}
