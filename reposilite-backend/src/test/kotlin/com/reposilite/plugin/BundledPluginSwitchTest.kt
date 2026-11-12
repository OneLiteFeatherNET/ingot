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
import com.reposilite.plugin.fixtures.OtherFixturePlugin
import com.reposilite.plugin.fixtures.PluginJarFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * One switch per bundled jar: the importer jar follows its own switch, the metrics jar follows the metrics switch, and a jar
 * the loader does not know follows the metrics switch as before. Plugin ids: `fixture` is the importer jar's plugin and
 * `other-fixture` is the metrics jar's plugin.
 */
internal class BundledPluginSwitchTest {

    private val journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(System.out, System.err)
    }

    @TempDir
    lateinit var root: Path

    private val pluginsDirectory: Path by lazy { root.resolve("plugins").createDirectories() }
    private val bundledDirectory: Path by lazy { root.resolve("bundled-plugins").createDirectories() }

    private fun writeImporterAndMetricsJars() {
        PluginJarFixture.write(bundledDirectory, "nexus-import-plugin.jar", FixturePluginOne::class)
        PluginJarFixture.write(bundledDirectory, "prometheus-plugin.jar", OtherFixturePlugin::class)
    }

    private fun load(environment: Map<String, String>): Extensions {
        val extensions = Extensions(journalist)
        PluginLoader(
            pluginsDirectory = pluginsDirectory,
            extensions = extensions,
            bundledPluginsDirectory = bundledDirectory,
            environment = environment,
        ).loadPluginsByServiceFiles()
        return extensions
    }

    @Test
    fun `should load only the importer jar when the importer switch is on`() {
        // given: the importer and the metrics jar, and only the importer switch on
        writeImporterAndMetricsJars()

        // when: the plugins are loaded
        val extensions = load(mapOf("INGOT_NEXUS_IMPORT_ENABLED" to "true"))

        // then: the importer is registered and the metrics jar is not
        assertThat(extensions.getPlugins()["fixture"]).describedAs("the importer switch must load the importer jar").isNotNull
        assertThat(extensions.getPlugins()["other-fixture"]).describedAs("the importer switch must not load the metrics jar").isNull()
    }

    @Test
    fun `should load only the metrics jar when the metrics switch is on`() {
        // given: the importer and the metrics jar, and only the metrics switch on
        writeImporterAndMetricsJars()

        // when: the plugins are loaded
        val extensions = load(mapOf("INGOT_METRICS_ENABLED" to "true"))

        // then: the metrics jar is registered and the importer is not
        assertThat(extensions.getPlugins()["other-fixture"]).describedAs("the metrics switch must load the metrics jar").isNotNull
        assertThat(extensions.getPlugins()["fixture"]).describedAs("the metrics switch must not load the importer jar").isNull()
    }

    @Test
    fun `should load the importer jar on the legacy importer switch when the INGOT name is unset`() {
        // given: the importer jar and only the legacy importer switch set
        writeImporterAndMetricsJars()

        // when: the plugins are loaded
        val extensions = load(mapOf("REPOSILITE_NEXUS_IMPORT_ENABLED" to "true"))

        // then: the importer is registered
        assertThat(extensions.getPlugins()["fixture"]).describedAs("REPOSILITE_NEXUS_IMPORT_ENABLED must be the fallback").isNotNull
    }

    @Test
    fun `should let INGOT_NEXUS_IMPORT_ENABLED=false override the legacy importer switch`() {
        // given: the importer jar, the legacy switch on and the INGOT switch off
        writeImporterAndMetricsJars()

        // when: the plugins are loaded
        val extensions = load(mapOf("INGOT_NEXUS_IMPORT_ENABLED" to "false", "REPOSILITE_NEXUS_IMPORT_ENABLED" to "true"))

        // then: the importer is not registered, because the set INGOT name decides
        assertThat(extensions.getPlugins()["fixture"]).describedAs("a set INGOT_NEXUS_IMPORT_ENABLED must win").isNull()
    }

    @Test
    fun `should follow the metrics switch for a bundled jar the loader does not know`() {
        // given: an unknown bundled jar
        PluginJarFixture.write(bundledDirectory, "custom-plugin.jar", OtherFixturePlugin::class)

        // when: the plugins are loaded with the metrics switch, and then with only the importer switch
        val withMetrics = load(mapOf("INGOT_METRICS_ENABLED" to "true"))
        val withImporter = load(mapOf("INGOT_NEXUS_IMPORT_ENABLED" to "true"))

        // then: the unknown jar loads with the metrics switch only
        assertThat(withMetrics.getPlugins()["other-fixture"]).describedAs("an unknown jar follows the metrics switch").isNotNull
        assertThat(withImporter.getPlugins()["other-fixture"]).describedAs("an unknown jar must not follow the importer switch").isNull()
    }

    @Test
    fun `should not load the opensearch jar when no switch is set`() {
        // given: the opensearch jar in the bundled directory
        PluginJarFixture.write(bundledDirectory, "opensearch-plugin.jar", FixturePluginOne::class)

        // when: the plugins are loaded without any switch
        val extensions = load(emptyMap())

        // then: the default installation loads nothing
        assertThat(extensions.getPlugins()["fixture"]).describedAs("the opensearch jar is off by default").isNull()
    }

    @Test
    fun `should load the opensearch jar only when the opensearch switch is on`() {
        // given: the opensearch jar and its switch on
        PluginJarFixture.write(bundledDirectory, "opensearch-plugin.jar", FixturePluginOne::class)

        // when: the plugins are loaded
        val extensions = load(mapOf("INGOT_OPENSEARCH_ENABLED" to "true"))

        // then: the opensearch jar is registered
        assertThat(extensions.getPlugins()["fixture"]).describedAs("INGOT_OPENSEARCH_ENABLED must load the opensearch jar").isNotNull
    }

    @Test
    fun `should load the opensearch jar on the legacy switch when the INGOT name is unset`() {
        // given: the opensearch jar and only the legacy switch on
        PluginJarFixture.write(bundledDirectory, "opensearch-plugin.jar", FixturePluginOne::class)

        // when: the plugins are loaded
        val extensions = load(mapOf("REPOSILITE_OPENSEARCH_ENABLED" to "true"))

        // then: the opensearch jar is registered
        assertThat(extensions.getPlugins()["fixture"]).describedAs("REPOSILITE_OPENSEARCH_ENABLED must be the fallback").isNotNull
    }

    @Test
    fun `should not load the opensearch jar when only the metrics switch is on`() {
        // given: the opensearch jar and only the metrics switch on
        PluginJarFixture.write(bundledDirectory, "opensearch-plugin.jar", FixturePluginOne::class)

        // when: the plugins are loaded
        val extensions = load(mapOf("INGOT_METRICS_ENABLED" to "true"))

        // then: the opensearch jar keeps its own switch and stays off
        assertThat(extensions.getPlugins()["fixture"]).describedAs("the metrics switch must not load the opensearch jar").isNull()
    }

}
