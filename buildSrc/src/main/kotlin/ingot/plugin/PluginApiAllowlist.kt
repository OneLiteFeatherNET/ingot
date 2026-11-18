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

// The allowlist of the slim plugin API jar, one fully qualified class name per line. Blank lines and lines starting with '#'
// are skipped. Only the new Ingot namespace is listed: the legacy com.reposilite.* types are provided by the server.
object PluginApiAllowlist {

    const val NAMESPACE = "net.onelitefeather.ingot."

    private val className = Regex("""[a-z][a-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+""")

    fun parse(text: String): List<String> {
        val names = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val malformed = names.filterNot { className.matches(it) }
        require(malformed.isEmpty()) {
            "plugin-api-classes.txt holds lines that are not fully qualified class names: ${malformed.joinToString()}"
        }
        val outside = names.filterNot { it.startsWith(NAMESPACE) }
        require(outside.isEmpty()) {
            "plugin-api-classes.txt lists classes outside $NAMESPACE: ${outside.joinToString()}"
        }
        return names
    }
}
