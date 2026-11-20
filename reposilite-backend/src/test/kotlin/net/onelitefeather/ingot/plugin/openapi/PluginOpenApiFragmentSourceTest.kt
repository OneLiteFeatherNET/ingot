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

package net.onelitefeather.ingot.plugin.openapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

// Each test writes its own fixture jars into a temporary directory and opens them in their own class loader.
class PluginOpenApiFragmentSourceTest {

    @TempDir
    lateinit var tempDir: Path

    private fun jar(name: String, entries: Map<String, String>): java.net.URL {
        val file = tempDir.resolve(name)
        JarOutputStream(Files.newOutputStream(file)).use { out ->
            entries.forEach { (path, content) ->
                out.putNextEntry(JarEntry(path))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file.toUri().toURL()
    }

    @Test
    fun `source returns the bytes of the fragment named by the plugin id`() {
        URLClassLoader(arrayOf(jar("fixture.jar", mapOf("META-INF/ingot/openapi/fixture.json" to "{\"paths\":{}}"))), null).use { loader ->
            val fragments = PluginOpenApiFragmentSource.load(mapOf("fixture" to loader))

            assertThat(fragments.map { it.pluginId })
                .`as`("the fragment of the plugin fixture must be found in its jar")
                .containsExactly("fixture")
            assertThat(String(fragments.single().bytes))
                .`as`("the bytes of the fragment must be the bytes of the file in the jar")
                .isEqualTo("{\"paths\":{}}")
        }
    }

    @Test
    fun `source returns nothing for a plugin id whose loader holds only another fragment`() {
        URLClassLoader(arrayOf(jar("other.jar", mapOf("META-INF/ingot/openapi/other.json" to "{}"))), null).use { loader ->
            val fragments = PluginOpenApiFragmentSource.load(mapOf("fixture" to loader))

            assertThat(fragments)
                .`as`("a jar without META-INF/ingot/openapi/fixture.json must yield no fragment for fixture")
                .isEmpty()
        }
    }

    @Test
    fun `source gives each plugin of one shared loader only its own fragment`() {
        val shared = jar(
            "shared.jar",
            mapOf(
                "META-INF/ingot/openapi/alpha.json" to "{\"tag\":\"alpha\"}",
                "META-INF/ingot/openapi/beta.json" to "{\"tag\":\"beta\"}",
            )
        )
        URLClassLoader(arrayOf(shared), null).use { loader ->
            val fragments = PluginOpenApiFragmentSource.load(mapOf("alpha" to loader, "beta" to loader))

            assertThat(fragments.associate { it.pluginId to String(it.bytes) })
                .`as`("each plugin must get only the file named after its own id")
                .containsExactlyInAnyOrderEntriesOf(mapOf("alpha" to "{\"tag\":\"alpha\"}", "beta" to "{\"tag\":\"beta\"}"))
        }
    }

    @Test
    fun `source sorts the fragments by plugin id`() {
        val shared = jar(
            "sorted.jar",
            mapOf(
                "META-INF/ingot/openapi/zeta.json" to "{}",
                "META-INF/ingot/openapi/alpha.json" to "{}",
            )
        )
        URLClassLoader(arrayOf(shared), null).use { loader ->
            val fragments = PluginOpenApiFragmentSource.load(linkedMapOf("zeta" to loader, "alpha" to loader))

            assertThat(fragments.map { it.pluginId })
                .`as`("the fragments must be listed in ascending order of the plugin id")
                .containsExactly("alpha", "zeta")
        }
    }
}
