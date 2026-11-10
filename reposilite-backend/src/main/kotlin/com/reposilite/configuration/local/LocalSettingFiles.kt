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

package com.reposilite.configuration.local

import com.reposilite.journalist.Journalist
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * A file named by a `_FILE` variable or a `.file` property cannot supply its setting. The message names the variable as the
 * operator set it and the path, and never the content of the file.
 */
class LocalConfigurationFileException(message: String) : IllegalArgumentException(message)

internal object LocalSettingFiles {

    private class Entry(val rawName: String, val value: String)

    /**
     * Strips the first matching prefix off the names of [entries] and resolves the file variables among them. The prefixes are
     * ordered most preferred first, so the first prefix that carries a setting wins, as it does for plain variables.
     *
     * A file variable of a known setting gives the value of that setting unless the same source sets the plain variable too. In
     * that case the plain variable is used, the file is not read and one warning names both variables. A file variable of an
     * unknown setting is passed on under its own name, so the caller reports it as unknown and its file is not read.
     *
     * [fold] is the case folding of the source, [fileSuffix] is the suffix of a file variable after folding, and the names in
     * [knownSettings] are lower case.
     */
    fun resolve(
        entries: Map<String, String>,
        prefixes: List<String>,
        fold: (String) -> String,
        fileSuffix: String,
        knownSettings: Set<String>,
        workingDirectory: Path,
        journalist: Journalist?,
    ): Map<String, String> {
        val suffix = fold(fileSuffix)
        val plain = linkedMapOf<String, Entry>()
        val files = linkedMapOf<String, Entry>()
        val unknownFiles = linkedMapOf<String, String>()

        for (prefix in prefixes.map(fold)) {
            for ((rawName, value) in entries) {
                val name = fold(rawName)
                if (!name.startsWith(prefix)) continue

                val setting = name.substringAfter(prefix)
                when {
                    !setting.endsWith(suffix) -> plain.putIfAbsent(setting, Entry(rawName, value))
                    setting.dropLast(suffix.length).lowercase() in knownSettings ->
                        files.putIfAbsent(setting.dropLast(suffix.length), Entry(rawName, value))
                    else -> unknownFiles.putIfAbsent(setting, value)
                }
            }
        }

        val resolved = linkedMapOf<String, String>()
        resolved.putAll(unknownFiles)
        plain.forEach { (setting, entry) -> resolved[setting] = entry.value }

        files.forEach { (setting, file) ->
            val plainEntry = plain[setting]
            if (plainEntry != null) {
                journalist?.logger?.warn("Local configuration | ${plainEntry.rawName} and ${file.rawName} are both set, ${file.rawName} is ignored")
                return@forEach
            }

            resolved[setting] = readSettingFile(file.rawName, file.value, workingDirectory)
            journalist?.logger?.info("Local configuration | $setting is read from ${file.rawName}")
        }

        return resolved
    }

    private fun readSettingFile(variable: String, value: String, workingDirectory: Path): String {
        val file = try {
            workingDirectory.resolve(value)
        } catch (exception: InvalidPathException) {
            throw LocalConfigurationFileException("$variable names $value, which is not a valid path")
        }
        return readContent(variable, file)
    }

    /**
     * Reads the content of [file] as UTF-8 and removes the line breaks at its end. Nothing else is changed.
     */
    internal fun readContent(variable: String, file: Path): String {
        fun refuse(reason: String): Nothing = throw LocalConfigurationFileException("$variable names $file, which $reason")

        if (!Files.exists(file)) refuse("does not exist")
        if (Files.isDirectory(file)) refuse("is a directory")
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) refuse("is not a readable file")

        val bytes = try {
            Files.readAllBytes(file)
        } catch (exception: IOException) {
            refuse("cannot be read")
        }
        val content = try {
            UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (exception: CharacterCodingException) {
            refuse("does not contain valid UTF-8 text")
        }
        return content.trimEnd('\n', '\r')
    }

}
