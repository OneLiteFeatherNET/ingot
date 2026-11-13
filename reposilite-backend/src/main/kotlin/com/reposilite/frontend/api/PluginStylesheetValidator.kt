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
package com.reposilite.frontend.api

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Checks a stylesheet that a plugin registers, as a pure function, and wraps the accepted text in the plugin layer.
 * A sheet without a problem cannot make a request, cannot end its layer early and cannot spell a forbidden keyword
 * with a CSS escape, because every backslash outside a string is refused before a function name is looked at.
 *
 * The rules are documented for plugin authors in `docs/gitbook/how-to/extend-the-dashboard-from-a-plugin.md`.
 */
internal object PluginStylesheetValidator {

    /** The name of the cascade layer that holds every plugin rule. The dashboard declares it after its own layers. */
    const val LAYER: String = "ingot-plugins"

    /** The largest sheet, in bytes. The same bound as the manifest. */
    const val MAX_BYTES: Int = 64 * 1024

    private val PLUGIN_NAME = Regex("[a-z0-9-]{1,32}")
    private val BYTE_ORDER_MARK = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val FORBIDDEN_AT_RULE = Regex("@(import|font-face|namespace|charset)\\b", RegexOption.IGNORE_CASE)
    private val URL_CALL = Regex("(?<![a-z0-9_-])url\\s*\\(\\s*['\"]?", RegexOption.IGNORE_CASE)
    private val LOADING_FUNCTION = Regex("(?<![a-z0-9_-])((?:-webkit-)?(?:src|image-set))\\s*\\(", RegexOption.IGNORE_CASE)
    private val LEGACY_HOOK = listOf(
        Regex("(?<![a-z0-9_-])expression\\s*\\(", RegexOption.IGNORE_CASE) to "expression(), which runs script in old browsers",
        Regex("(?<![a-z0-9_-])behavior\\s*:", RegexOption.IGNORE_CASE) to "behavior:, which attaches a script behaviour in old browsers",
        Regex("-moz-binding", RegexOption.IGNORE_CASE) to "-moz-binding, which binds an XBL document",
    )

    /** The problems of a sheet of [plugin]. An empty list means the sheet is accepted. */
    fun problems(plugin: String, sheet: ByteArray): List<String> {
        if (!PLUGIN_NAME.matches(plugin)) {
            return listOf("plugin name '$plugin' must match [a-z0-9-] and be at most 32 characters, so its path can be served")
        }
        if (sheet.size > MAX_BYTES) {
            return listOf("the stylesheet of plugin '$plugin' is ${sheet.size} bytes, the limit is 64 KiB")
        }
        if (sheet.size >= BYTE_ORDER_MARK.size && sheet.copyOf(BYTE_ORDER_MARK.size).contentEquals(BYTE_ORDER_MARK)) {
            return listOf("the stylesheet of plugin '$plugin' starts with a byte order mark, which is not allowed")
        }
        val text = decode(sheet) ?: return listOf("the stylesheet of plugin '$plugin' is not valid UTF-8")
        if (text.isBlank()) {
            return listOf("the stylesheet of plugin '$plugin' is empty")
        }
        return buildList {
            val code = tokenize(text, this)
            FORBIDDEN_AT_RULE.findAll(code).forEach { add("the stylesheet of plugin '$plugin' uses ${it.value.lowercase()}, which is not allowed") }
            URL_CALL.findAll(code).forEach { match ->
                if (!code.regionMatches(match.range.last + 1, "data:image/", 0, "data:image/".length, ignoreCase = true)) {
                    add("the stylesheet of plugin '$plugin' uses url(), which loads a resource unless its argument is a data:image URI")
                }
            }
            LOADING_FUNCTION.findAll(code).forEach { add("the stylesheet of plugin '$plugin' uses ${it.groupValues[1]}(), which loads a resource") }
            LEGACY_HOOK.forEach { (pattern, reason) ->
                if (pattern.containsMatchIn(code)) add("the stylesheet of plugin '$plugin' uses $reason")
            }
        }
    }

    /** The text of an accepted sheet, inside the plugin layer. */
    fun wrap(sheet: String): String = "@layer $LAYER {\n$sheet\n}\n"

    private fun decode(sheet: ByteArray): String? = try {
        UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(sheet))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /**
     * Reads the sheet once: comments are removed, strings are kept (a url() argument may be quoted), and the braces are
     * counted outside strings and comments. A backslash outside a string is a problem of its own, so an escape can never
     * spell a keyword that the later checks look for.
     */
    private fun tokenize(css: String, problems: MutableList<String>): String {
        val code = StringBuilder(css.length)
        var depth = 0
        var closedTooMuch = false
        var index = 0
        while (index < css.length) {
            val char = css[index]
            when {
                css.startsWith("/*", index) -> {
                    val end = css.indexOf("*/", index + 2)
                    if (end < 0) {
                        problems += "the stylesheet has a comment that is never closed"
                        return code.toString()
                    }
                    code.append(' ')
                    index = end + 2
                }
                char == '"' || char == '\'' -> {
                    val end = stringEnd(css, index)
                    if (end < 0) {
                        problems += "the stylesheet has a string that is never closed"
                        return code.toString()
                    }
                    code.append(css, index, end)
                    index = end
                }
                char == '\\' -> {
                    problems += "the stylesheet contains a backslash outside a string, which can spell a forbidden keyword"
                    code.append(char)
                    index++
                }
                char == '{' -> {
                    depth++
                    code.append(char)
                    index++
                }
                char == '}' -> {
                    depth--
                    if (depth < 0) closedTooMuch = true
                    code.append(char)
                    index++
                }
                else -> {
                    code.append(char)
                    index++
                }
            }
        }
        if (closedTooMuch) problems += "the stylesheet closes a brace that is not open, which could end the layer early"
        else if (depth != 0) problems += "the stylesheet has unbalanced braces"
        return code.toString()
    }

    /** The index after the closing quote of the string that starts at [start], or -1 when it never closes on its line. */
    private fun stringEnd(css: String, start: Int): Int {
        val quote = css[start]
        var index = start + 1
        while (index < css.length) {
            when (css[index]) {
                '\\' -> index += 2
                quote -> return index + 1
                '\n' -> return -1
                else -> index++
            }
        }
        return -1
    }
}
