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

package net.onelitefeather.ingot.plugin.embedded

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * The plugin jars that the running server embeds under [DIRECTORY]. The code source is the server jar in production, or the
 * classes directory when the server runs from classes. A code source that is missing or unreadable embeds nothing.
 */
class EmbeddedPluginSource(private val codeSource: Path?) {

    /** The jars that the code source embeds, in the order of their names. An empty list when there are none. */
    fun jars(): List<EmbeddedJar> {
        val source = codeSource ?: return emptyList()
        if (!source.exists()) {
            return emptyList()
        }
        return if (source.isDirectory()) fromDirectory(source) else fromJar(source)
    }

    private fun fromDirectory(classes: Path): List<EmbeddedJar> {
        val directory = classes.resolve(DIRECTORY)
        if (!directory.isDirectory()) {
            return emptyList()
        }
        return directory.listDirectoryEntries("*.jar")
            .filter { it.isRegularFile() }
            .sortedBy { it.fileName.toString() }
            .map { file -> EmbeddedJar(file.fileName.toString()) { target -> copyFile(file, target) } }
    }

    private fun fromJar(jar: Path): List<EmbeddedJar> =
        try {
            JarFile(jar.toFile()).use { jarFile ->
                jarFile.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith(DIRECTORY) && it.endsWith(".jar") && '/' !in it.removePrefix(DIRECTORY) }
                    .sorted()
                    .map { name -> EmbeddedJar(name.removePrefix(DIRECTORY)) { target -> copyEntry(jar, name, target) } }
                    .toList()
            }
        } catch (exception: IOException) {
            emptyList()
        }

    private fun copyFile(file: Path, target: Path) {
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun copyEntry(jar: Path, name: String, target: Path) {
        JarFile(jar.toFile()).use { jarFile ->
            val entry = checkNotNull(jarFile.getJarEntry(name)) { "Entry $name is missing from ${jar.fileName}" }
            jarFile.getInputStream(entry).use { input ->
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    companion object {
        /** The directory of the server jar or of the classes directory that holds the embedded jars. */
        const val DIRECTORY: String = "embedded-plugins/"

        /** The embedded source of a class, read from the code source of the class. */
        fun ofCodeSource(type: Class<*>): EmbeddedPluginSource =
            EmbeddedPluginSource(runCatching { Path.of(type.protectionDomain.codeSource.location.toURI()) }.getOrNull())
    }

}

/** One jar embedded in the server. [writeTo] copies its bytes to a target, which is replaced when it exists. */
class EmbeddedJar internal constructor(
    /** The file name of the jar, such as `javadoc-plugin.jar`. */
    val fileName: String,
    private val content: (Path) -> Unit,
) {

    fun writeTo(target: Path) {
        content(target)
    }

}
