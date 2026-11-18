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

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class EmbeddedPluginJarsTest {

    @TempDir
    lateinit var dir: Path

    private val fixture = EmbeddedPluginJars.Expected(module = ":reposilite-plugins:fixture-plugin", jarName = "fixture-plugin.jar")

    @Test
    fun serverJarWithoutEmbeddedEntriesPasses() {
        val server = serverJar(embedded = emptyMap())

        val violations = EmbeddedPluginJars.violations(server, expected = emptyList())

        assertThat(violations).describedAs("a server jar without embedded plugins needs no embedded entry").isEmpty()
    }

    @Test
    fun namedModuleWithEntryAndVersionPasses() {
        val server = serverJar(embedded = mapOf("embedded-plugins/fixture-plugin.jar" to jarBytes(version = "1.0.0")))

        val violations = EmbeddedPluginJars.violations(server, expected = listOf(fixture))

        assertThat(violations).describedAs("one entry per named module, with a version, is accepted").isEmpty()
    }

    @Test
    fun namedModuleWithoutEntryFailsAndNamesTheModule() {
        val server = serverJar(embedded = emptyMap())

        val violations = EmbeddedPluginJars.violations(server, expected = listOf(fixture))

        assertThat(violations).describedAs("one violation for the missing entry").hasSize(1)
        assertThat(violations.single()).describedAs("the message names the module").contains(":reposilite-plugins:fixture-plugin")
    }

    @Test
    fun embeddedEntryWithoutImplementationVersionFailsAndNamesTheModule() {
        val server = serverJar(embedded = mapOf("embedded-plugins/fixture-plugin.jar" to jarBytes(version = null)))

        val violations = EmbeddedPluginJars.violations(server, expected = listOf(fixture))

        assertThat(violations).describedAs("one violation for the missing version").hasSize(1)
        assertThat(violations.single()).describedAs("the message names the module").contains(":reposilite-plugins:fixture-plugin")
        assertThat(violations.single()).describedAs("the message names the manifest attribute").contains("Implementation-Version")
    }

    @Test
    fun undeclaredEmbeddedEntryFailsAndNamesTheEntry() {
        val server = serverJar(embedded = mapOf("embedded-plugins/other.jar" to jarBytes(version = "1.0.0")))

        val violations = EmbeddedPluginJars.violations(server, expected = emptyList())

        assertThat(violations).describedAs("one violation for the undeclared entry").hasSize(1)
        assertThat(violations.single()).describedAs("the message names the entry").contains("embedded-plugins/other.jar")
    }

    private fun serverJar(embedded: Map<String, ByteArray>): File {
        val file = dir.resolve("ingot.jar").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("net/onelitefeather/ingot/Server.class"))
            zip.write(ByteArray(0))
            zip.closeEntry()
            embedded.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun jarBytes(version: String?): ByteArray {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            if (version != null) {
                mainAttributes[Attributes.Name("Implementation-Version")] = version
            }
        }
        val out = ByteArrayOutputStream()
        JarOutputStream(out, manifest).use { }
        return out.toByteArray()
    }
}
