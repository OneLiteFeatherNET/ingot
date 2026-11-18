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
import java.util.jar.Manifest
import java.util.zip.ZipInputStream

// The check of the plugin jars that the backend shadow jar embeds (see openspec change add-embedded-plugin-jars). Each named
// module contributes exactly one entry embedded-plugins/<jar name>, and that jar carries the manifest attribute
// Implementation-Version, which the loader reads for the version rule. Nothing else may sit under embedded-plugins/.
object EmbeddedPluginJars {

    const val DIRECTORY = "embedded-plugins/"
    const val VERSION_ATTRIBUTE = "Implementation-Version"

    // A module named in embeddedPluginProjects, with the file name of its shadow jar.
    data class Expected(val module: String, val jarName: String)

    // One message per violation. An empty list means the server jar embeds exactly the expected plugin jars.
    fun violations(serverJar: File, expected: Collection<Expected>): List<String> {
        val embedded = embeddedJars(serverJar)
        val violations = mutableListOf<String>()

        expected.groupBy { it.jarName }.filterValues { it.size > 1 }.forEach { (jarName, modules) ->
            violations += "Modules ${modules.joinToString { it.module }} share the jar name $jarName, so only one of them can be embedded"
        }

        expected.forEach { plugin ->
            val entry = DIRECTORY + plugin.jarName
            val bytes = embedded[plugin.jarName]
            when {
                bytes == null -> violations += "Module ${plugin.module} is named in embeddedPluginProjects but $entry is missing from ${serverJar.name}"
                implementationVersion(bytes) == null ->
                    violations += "$entry of module ${plugin.module} has no $VERSION_ATTRIBUTE manifest attribute"
            }
        }

        val declared = expected.map { it.jarName }.toSet()
        (embedded.keys - declared).sorted().forEach { name ->
            violations += "$DIRECTORY$name in ${serverJar.name} is not declared by any module in embeddedPluginProjects"
        }
        return violations
    }

    // The entries under embedded-plugins/, keyed by the name after the directory, with the bytes of each entry.
    private fun embeddedJars(serverJar: File): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(serverJar.inputStream().buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (!entry.isDirectory && entry.name.startsWith(DIRECTORY)) {
                    result[entry.name.removePrefix(DIRECTORY)] = zip.readBytes()
                }
            }
        }
        return result
    }

    // The main attribute Implementation-Version of a nested jar, or null when the jar or the attribute is missing or blank.
    private fun implementationVersion(jarBytes: ByteArray): String? =
        ZipInputStream(jarBytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name.equals("META-INF/MANIFEST.MF", ignoreCase = true) }
                ?.let { Manifest(zip).mainAttributes.getValue(VERSION_ATTRIBUTE) }
                ?.takeIf { it.isNotBlank() }
        }
}
