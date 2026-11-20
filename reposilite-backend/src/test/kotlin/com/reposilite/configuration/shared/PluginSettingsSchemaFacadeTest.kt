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

package com.reposilite.configuration.shared

import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.plugin.schema.PluginSettingsFixtureJar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.mutableReference
import java.nio.file.Path

/**
 * A plugin whose jar carries the schema of its settings class has that schema served as it is, without the runtime generator and
 * without the warning. A plugin without the file keeps today's path: one warning and a schema generated at runtime.
 */
internal class PluginSettingsSchemaFacadeTest {

    private object NoDocument : SharedConfigurationProvider {
        override fun updateConfiguration(content: String) = Unit
        override fun fetchConfiguration(): String = "{}"
        override fun isUpdateRequired(): Boolean = false
        override fun isMutable(): Boolean = false
        override fun name(): String = "memory"
    }

    @TempDir
    lateinit var tempDir: Path

    private val journalist = InMemoryLogger()

    private fun facade(type: Class<out SharedSettings>): SharedConfigurationFacade =
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(type to mutableReference(type.getConstructor().newInstance()))),
            sharedConfigurationProvider = NoDocument,
            extensions = Extensions(journalist),
        )

    private fun SharedConfigurationFacade.servedSchema(): String =
        getSettingsReference<SharedSettings>("fixture_plugin")!!.schema.get().use { String(it.readBytes()) }

    private fun schemeWarnings(): List<String> =
        journalist.messages.map { it.value }.filter { it.contains("Cannot find scheme") }

    @Test
    fun `the schema in the plugin jar is served and no warning is logged`() {
        val schema = """{"type":"object","title":"from the plugin jar"}"""
        PluginSettingsFixtureJar.open(PluginSettingsFixtureJar.write(tempDir, schema)).use { loader ->
            val facade = facade(PluginSettingsFixtureJar.settingsType(loader))

            assertThat(facade.servedSchema())
                .`as`("the schema of json-schemes/<class name> in the plugin jar must be served unchanged")
                .isEqualTo(schema)
            assertThat(schemeWarnings())
                .`as`("a plugin that ships its schema must not log a Cannot find scheme line")
                .isEmpty()
        }
    }

    @Test
    fun `a plugin jar without the schema logs one warning and serves the runtime schema`() {
        PluginSettingsFixtureJar.open(PluginSettingsFixtureJar.write(tempDir, schema = null)).use { loader ->
            val facade = facade(PluginSettingsFixtureJar.settingsType(loader))

            assertThat(schemeWarnings())
                .`as`("a plugin without json-schemes/<class name> must log exactly one Cannot find scheme line")
                .hasSize(1)
            assertThat(DEFAULT_OBJECT_MAPPER.readTree(facade.servedSchema()).path("properties").has("endpoint"))
                .`as`("the runtime schema must describe the endpoint property of the settings class")
                .isTrue()
        }
    }
}
