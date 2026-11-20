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

package ingot.plugin

import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PluginApiFragmentsTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun jarWithItsOwnFragmentAndSchemaPasses() {
        val jar = jar("META-INF/ingot/openapi/cleanup.json", "json-schemes/a.B")

        val violations = PluginApiFragments.violations(jar, pluginId = "cleanup")

        assertThat(violations).describedAs("the fragment of the plugin id and a schema per class are the expected layout").isEmpty()
    }

    @Test
    fun jarWithProcessorIndexFailsAndNamesTheEntry() {
        val jar = jar("META-INF/ingot/openapi/cleanup.json", "openapi-plugin/.index")

        val violations = PluginApiFragments.violations(jar, pluginId = "cleanup")

        assertThat(violations).describedAs("one violation for the index that would shadow the server's").hasSize(1)
        assertThat(violations.single()).describedAs("the message names the entry").contains("openapi-plugin/.index")
    }

    @Test
    fun jarWithSchemaIndexFailsAndNamesTheEntry() {
        val jar = jar("json-schemes/a.B", "json-schemes/index")

        val violations = PluginApiFragments.violations(jar, pluginId = "cleanup")

        assertThat(violations).describedAs("one violation for the schema index that would shadow the server's").hasSize(1)
        assertThat(violations.single()).describedAs("the message names the entry").contains("json-schemes/index")
    }

    @Test
    fun jarWithFragmentOfAnotherIdFailsAndNamesTheEntryAndTheExpectedId() {
        val jar = jar("META-INF/ingot/openapi/other.json")

        val violations = PluginApiFragments.violations(jar, pluginId = "cleanup")

        assertThat(violations).describedAs("one violation for the fragment the server would never read").hasSize(1)
        assertThat(violations.single()).describedAs("the message names the entry").contains("META-INF/ingot/openapi/other.json")
        assertThat(violations.single()).describedAs("the message names the expected file").contains("META-INF/ingot/openapi/cleanup.json")
    }

    @Test
    fun jarWithoutFragmentPasses() {
        val jar = jar("com/example/CleanupPlugin.class")

        val violations = PluginApiFragments.violations(jar, pluginId = "cleanup")

        assertThat(violations).describedAs("a module without routes ships no fragment").isEmpty()
    }

    @Test
    fun relocateRenamesTheDocumentAndRemovesBothIndexes() {
        val output = dir.resolve("kapt").toFile()
        write(output, "openapi-plugin/.index", "openapi-default.json")
        write(output, "openapi-plugin/openapi-default.json", "{\"paths\":{}}")
        write(output, "json-schemes/index", "a.B")
        write(output, "json-schemes/a.B", "{}")

        PluginApiFragments.relocate(output, pluginId = "cleanup")

        assertThat(output.resolve("META-INF/ingot/openapi/cleanup.json"))
            .describedAs("the document is renamed to the plugin id").hasContent("{\"paths\":{}}")
        assertThat(output.resolve("openapi-plugin")).describedAs("the processor directory is gone").doesNotExist()
        assertThat(output.resolve("json-schemes/index")).describedAs("the schema index is gone").doesNotExist()
        assertThat(output.resolve("json-schemes/a.B")).describedAs("the schema of the class stays").hasContent("{}")
    }

    private fun jar(vararg entries: String): File {
        val file = dir.resolve("plugin.jar").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    private fun write(root: File, path: String, content: String) {
        root.resolve(path).apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }
}
