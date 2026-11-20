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

package net.onelitefeather.ingot.plugin.schema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

// Each test writes its own fixture jar into a temporary directory and opens it in its own class loader.
class PluginSettingsSchemasTest {

    @TempDir
    lateinit var tempDir: Path

    private val serverLoader: ClassLoader = PluginSettingsSchemasTest::class.java.classLoader

    @Test
    fun `lookup returns the schema the plugin jar holds for its settings class`() {
        val schema = """{"type":"object","properties":{"endpoint":{"type":"string"}}}"""
        PluginSettingsFixtureJar.open(PluginSettingsFixtureJar.write(tempDir, schema)).use { loader ->
            val type = PluginSettingsFixtureJar.settingsType(loader)

            assertThat(PluginSettingsSchemas.find(type, serverLoader)?.let { String(it) })
                .`as`("the bytes of json-schemes/${type.name} in the plugin jar must be returned")
                .isEqualTo(schema)
        }
    }

    @Test
    fun `lookup returns null when the plugin jar holds no schema for its settings class`() {
        PluginSettingsFixtureJar.open(PluginSettingsFixtureJar.write(tempDir, schema = null)).use { loader ->
            val type = PluginSettingsFixtureJar.settingsType(loader)

            assertThat(PluginSettingsSchemas.find(type, serverLoader))
                .`as`("a jar without json-schemes/${type.name} must yield no schema")
                .isNull()
        }
    }

    @Test
    fun `lookup does not read a schema for a type on the server class loader`() {
        PluginSettingsFixtureJar.open(PluginSettingsFixtureJar.write(tempDir, schema = "{}")).use { loader ->
            val type = PluginSettingsFixtureJar.settingsType(loader)

            assertThat(PluginSettingsSchemas.find(type, serverLoader = loader))
                .`as`("a type whose loader is the server's loader must keep the server's path, even when the file exists")
                .isNull()
        }
    }
}
