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

import com.reposilite.configuration.local.LocalConfigurationFileException
import com.reposilite.configuration.local.LocalSettingFiles
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A reference that cannot be resolved. The message names the kind of reference and its name or path, never a resolved value.
 */
class SecretReferenceException(message: String) : IllegalArgumentException(message)

/**
 * Resolves the references of a `@Sensitive` value: `${env:NAME}` to the value of an allowed variable and `${file:PATH}` to the content
 * of a file in an allowed directory. `$${` stands for a literal `${`, and every other `${` text stays as it is.
 *
 * The environment is injected, so the resolver reads no variable that the caller did not pass in. The allowlists are checked for
 * every reference, and a file path is canonicalised before it is checked, so `..` and symbolic links cannot leave an allowed directory.
 */
class SecretReferenceResolver(
    private val environment: Map<String, String>,
    private val variables: Set<String>,
    directories: List<Path>,
) {

    private val directories: List<Path> = directories.map { canonical(it) }

    /**
     * @throws SecretReferenceException when a reference is malformed, not allowed, not set or cannot be read
     */
    fun resolve(value: String): String {
        val segments = parse(value)
        if (segments.none { it is Segment.Reference }) {
            return segments.joinToString(separator = "") { (it as Segment.Literal).text }
        }

        return buildString {
            segments.forEach { segment ->
                when (segment) {
                    is Segment.Literal -> append(segment.text)
                    is Segment.Environment -> append(variable(segment.name))
                    is Segment.File -> append(file(segment.path))
                }
            }
        }
    }

    private fun variable(name: String): String {
        if (name.isEmpty() || name.any { it.isWhitespace() || it == '=' }) {
            throw SecretReferenceException("A reference names an invalid variable name")
        }
        if (name !in variables) {
            throw SecretReferenceException("Variable $name is not listed in INGOT_SETTINGS_SECRET_ENV")
        }
        return environment[name] ?: throw SecretReferenceException("Variable $name is listed but not set")
    }

    private fun file(path: String): String {
        if (path.isEmpty()) {
            throw SecretReferenceException("A file reference has no path")
        }
        val requested = try {
            Paths.get(path)
        } catch (exception: InvalidPathException) {
            throw SecretReferenceException("A file reference names a path that is not valid")
        }
        if (!requested.isAbsolute) {
            throw SecretReferenceException("A file reference names $path, which is not an absolute path")
        }

        val file = canonical(requested)
        if (directories.none { file.startsWith(it) }) {
            throw SecretReferenceException("A file reference names $path, which is outside INGOT_SETTINGS_SECRET_FILES")
        }

        return try {
            LocalSettingFiles.readContent("a file reference", file)
        } catch (exception: LocalConfigurationFileException) {
            throw SecretReferenceException(exception.message ?: "A file reference cannot be read")
        }
    }

    internal sealed interface Segment {
        data class Literal(val text: String) : Segment
        sealed interface Reference : Segment
        data class Environment(val name: String) : Reference
        data class File(val path: String) : Reference
    }

    companion object {

        private const val ENVIRONMENT_PREFIX = "\${env:"
        private const val FILE_PREFIX = "\${file:"

        /**
         * Splits [value] into literal text and references. Nothing is read here.
         *
         * @throws SecretReferenceException when a reference is not closed
         */
        internal fun parse(value: String): List<Segment> {
            val segments = mutableListOf<Segment>()
            val literal = StringBuilder()
            var index = 0
            while (index < value.length) {
                val reference = when {
                    value.startsWith("\$\${", index) -> {
                        literal.append("\${")
                        index += 3
                        continue
                    }
                    value.startsWith(ENVIRONMENT_PREFIX, index) -> ENVIRONMENT_PREFIX
                    value.startsWith(FILE_PREFIX, index) -> FILE_PREFIX
                    else -> null
                }

                if (reference == null) {
                    literal.append(value[index])
                    index++
                    continue
                }

                val close = value.indexOf('}', index + reference.length)
                if (close < 0) {
                    throw SecretReferenceException("A reference is not closed with '}'")
                }

                if (literal.isNotEmpty()) {
                    segments += Segment.Literal(literal.toString())
                    literal.clear()
                }
                val body = value.substring(index + reference.length, close)
                segments += if (reference == ENVIRONMENT_PREFIX) Segment.Environment(body) else Segment.File(body)
                index = close + 1
            }

            if (literal.isNotEmpty() || segments.isEmpty()) {
                segments += Segment.Literal(literal.toString())
            }
            return segments
        }

        /**
         * True when [value] holds at least one reference and nothing but references, so it names a secret without containing one.
         * A value that is malformed, or that carries literal text next to a reference, is not one.
         */
        internal fun isReferenceOnly(value: String): Boolean {
            val segments = runCatching { parse(value) }.getOrNull() ?: return false
            return segments.any { it is Segment.Reference } && segments.none { it is Segment.Literal && it.text.isNotEmpty() }
        }

        private fun canonical(path: Path): Path =
            runCatching { path.toRealPath() }.getOrElse { path.toAbsolutePath().normalize() }
    }

}
