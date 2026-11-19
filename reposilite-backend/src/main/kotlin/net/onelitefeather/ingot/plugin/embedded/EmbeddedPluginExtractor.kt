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
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/** One extracted jar: its file name and the path it is loaded from. */
class ExtractedJar(val fileName: String, val path: Path)

/**
 * Writes the embedded jars to disk at each start, so that a jar the server no longer embeds does not stay behind.
 *
 * The extraction directory is removed and written again on every start. When it cannot be written, the jars go to a directory of
 * [fallbackRoot] named after the working directory, and one warning names both. When neither can be written, one error names the
 * jar for each jar, nothing is loaded from it, and the start goes on. A regular file on the path is not removed, because the
 * extractor only removes what it created.
 */
class EmbeddedPluginExtractor(
    private val embeddedPluginsDirectory: Path,
    private val fallbackRoot: Path,
    private val warn: (String) -> Unit,
    private val error: (String) -> Unit,
) {

    fun extract(jars: List<EmbeddedJar>): List<ExtractedJar> {
        val primaryFailure = try {
            return writeTo(embeddedPluginsDirectory, jars)
        } catch (exception: IOException) {
            exception
        }

        val fallback = fallbackDirectoryOf(fallbackRoot, embeddedPluginsDirectory)
        try {
            val extracted = writeTo(fallback, jars)
            warn("Embedded plugins cannot be written to $embeddedPluginsDirectory (${primaryFailure.message}), they are extracted to $fallback instead")
            return extracted
        } catch (exception: IOException) {
            jars.forEach {
                error("Embedded plugin ${it.fileName} is not loaded: neither $embeddedPluginsDirectory (${primaryFailure.message}) nor $fallback (${exception.message}) can be written")
            }
            return emptyList()
        }
    }

    /** Removes the directory, then writes each jar as a partial file that is moved to its final name atomically. */
    private fun writeTo(directory: Path, jars: List<EmbeddedJar>): List<ExtractedJar> {
        if (jars.isEmpty()) {
            if (directory.isDirectory()) {
                removeDirectory(directory)
            }
            return emptyList()
        }
        if (directory.exists() && !directory.isDirectory()) {
            throw IOException("$directory is not a directory")
        }
        if (directory.isDirectory()) {
            removeDirectory(directory)
        }
        directory.createDirectories()

        return jars.map { jar ->
            val partial = directory.resolve("${jar.fileName}.partial")
            jar.writeTo(partial)
            val target = directory.resolve(jar.fileName)
            Files.move(partial, target, ATOMIC_MOVE)
            ExtractedJar(jar.fileName, target)
        }
    }

    private fun removeDirectory(directory: Path) {
        if (!directory.toFile().deleteRecursively()) {
            throw IOException("$directory cannot be removed")
        }
    }

    companion object {

        /**
         * The fallback directory of a working directory: `<fallbackRoot>/ingot-embedded-plugins-<hash>`, where the hash is the first
         * twelve hexadecimal digits of the SHA-256 of the absolute working directory, the parent of [embeddedPluginsDirectory].
         */
        fun fallbackDirectoryOf(fallbackRoot: Path, embeddedPluginsDirectory: Path): Path {
            val workingDirectory = checkNotNull(embeddedPluginsDirectory.toAbsolutePath().parent) { "The extraction directory has a parent" }
            val digest = MessageDigest.getInstance("SHA-256").digest(workingDirectory.toString().toByteArray(Charsets.UTF_8))
            val hash = digest.joinToString("") { "%02x".format(it) }.take(12)
            return fallbackRoot.resolve("ingot-embedded-plugins-$hash")
        }

    }

}
