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

package net.onelitefeather.ingot.identity.login.ldap

/**
 * Builds LDAP search filters from administrator templates and user input. Every value is escaped as RFC 4515 requires, so a
 * user name such as `*)(uid=*` stays a literal value and cannot change the structure of the filter.
 */
object LdapFilters {

    private val PLACEHOLDER = Regex("\\{([a-zA-Z]+)}")

    /** Escapes a value for use inside a filter: `\`, `*`, `(`, `)`, NUL and every non-ASCII byte as `\hh`. */
    fun escape(value: String): String {
        val builder = StringBuilder(value.length)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xff
            val special = code == '\\'.code || code == '*'.code || code == '('.code || code == ')'.code || code == 0 || code > 0x7f
            if (special) {
                builder.append('\\').append("%02x".format(code))
            } else {
                builder.append(code.toChar())
            }
        }
        return builder.toString()
    }

    /**
     * Replaces every `{name}` of [template] with the escaped value of [name]. Placeholders are replaced in one pass, so a value
     * that contains a placeholder is not replaced again. A placeholder without a value stays as it is.
     */
    fun fill(template: String, values: Map<String, String>): String =
        PLACEHOLDER.replace(template) { match ->
            values[match.groupValues[1]]?.let(::escape) ?: match.value
        }

}
