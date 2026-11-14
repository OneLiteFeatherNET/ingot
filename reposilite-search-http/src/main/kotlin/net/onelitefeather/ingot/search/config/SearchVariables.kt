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

package net.onelitefeather.ingot.search.config

import java.nio.file.Files
import java.nio.file.Path

/**
 * The `INGOT_<PRODUCT>_*` variables of one search plugin (design D3 of the Elasticsearch change). Each variable falls back to its
 * `REPOSILITE_<PRODUCT>_*` spelling, and the `INGOT_` spelling wins when both are set. A blank value counts as unset. An invalid value
 * is refused with an [IllegalArgumentException] that names the variable and never its value, so that no message carries a secret or a
 * URL with user information.
 *
 * [product] is the upper case product name, for example `OPENSEARCH`. A secret may come from a plain variable or, read once, from the
 * file named by its `_FILE` variable; the plain variable wins. [readFile] reads such a file and is injected so that the rules can be
 * tested without a file system.
 */
class SearchVariables(
    val product: String,
    private val environment: Map<String, String>,
    private val readFile: (String) -> String = { path -> Files.readString(Path.of(path)) },
) {

    /** The `INGOT_` name of a variable of this product, for example `INGOT_OPENSEARCH_URLS`. Used in messages. */
    fun name(suffix: String): String = "INGOT_${product}_$suffix"

    /** The variable that was read and its raw value, or null when neither spelling is set to a non-blank value. */
    fun lookup(suffix: String): Pair<String, String>? =
        listOf(name(suffix), "REPOSILITE_${product}_$suffix").firstNotNullOfOrNull { variable ->
            environment[variable]?.takeIf { it.isNotBlank() }?.let { variable to it }
        }

    /** A switch that must be `true` or `false`. Unset means `false`. */
    fun flag(suffix: String): Boolean {
        val (variable, raw) = lookup(suffix) ?: return false
        return when (raw.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("$variable must be true or false")
        }
    }

    /** A whole number in [range], or [default] when unset. */
    fun integer(suffix: String, default: Int, range: IntRange): Int {
        val (variable, raw) = lookup(suffix) ?: return default
        val value = raw.trim().toIntOrNull()
        require(value != null && value in range) {
            "$variable must be a whole number from ${range.first} to ${range.last}, got '${raw.trim()}'"
        }
        return value
    }

    /**
     * A secret: the plain variable when it is set, otherwise the file named by the `_FILE` variable, read once. The trailing line
     * breaks of the file are removed. Null when neither is set.
     */
    fun secret(suffix: String, kind: String = "password"): String? {
        lookup(suffix)?.let { (_, value) -> return value }
        val (variable, path) = lookup("${suffix}_FILE") ?: return null
        val content = try {
            readFile(path.trim())
        } catch (failure: Exception) {
            throw IllegalArgumentException("$variable cannot be read: ${failure.javaClass.simpleName}")
        }
        val secret = content.trimEnd('\r', '\n')
        require(secret.isNotEmpty()) { "$variable names a file without a $kind" }
        return secret
    }

    /**
     * The user name and the password of basic authentication. A user name needs a password and a password needs a user name, so
     * that a half configured pair is refused before any request is sent.
     */
    fun credentials(): Pair<String?, String?> {
        val username = lookup(USERNAME)?.second
        val password = secret(PASSWORD)
        require(username == null || password != null) {
            "${name(USERNAME)} is set without ${name(PASSWORD)} or ${name("${PASSWORD}_FILE")}"
        }
        require(password == null || username != null) {
            "${name(PASSWORD)} is set without ${name(USERNAME)}"
        }
        return username to password
    }

    private companion object {
        const val USERNAME = "USERNAME"
        const val PASSWORD = "PASSWORD"
    }

}
