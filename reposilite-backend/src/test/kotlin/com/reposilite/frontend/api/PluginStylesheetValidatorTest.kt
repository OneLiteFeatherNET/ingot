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

import java.nio.charset.StandardCharsets.UTF_8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PluginStylesheetValidatorTest {

    private val plugin = "example"

    private fun problemsOf(css: String): String =
        PluginStylesheetValidator.problems(plugin, css.toByteArray(UTF_8)).joinToString(separator = "\n")

    private fun problemsOfBytes(bytes: ByteArray): String =
        PluginStylesheetValidator.problems(plugin, bytes).joinToString(separator = "\n")

    @Test
    fun `should accept a valid sheet that styles an anchor`() {
        // when: a sheet selects a part of a component by its stable name
        val problems = problemsOf("[data-ingot-component=\"plugin-page\"] [data-ingot-part=\"header\"] { color: red; }")

        // then: no problem is reported
        assertThat(problems).describedAs("problems of a valid sheet").isEmpty()
    }

    @Test
    fun `should accept a data image in url`() {
        // when: a rule uses a raster image that is inside the sheet
        val problems = problemsOf(".banner { background: url(data:image/png;base64,iVBORw0KGgo=); }")

        // then: the rule is accepted, because the image cannot load anything from the network
        assertThat(problems).describedAs("problems of a data image").isEmpty()
    }

    @Test
    fun `should accept a quoted data image in url`() {
        // when: the same image is given with quotes
        val problems = problemsOf(".banner { background: url(\"data:image/svg+xml;utf8,<svg/>\"); }")

        // then: the rule is accepted
        assertThat(problems).describedAs("problems of a quoted data image").isEmpty()
    }

    @Test
    fun `should accept a sheet of exactly 64 KiB`() {
        // given: a sheet that fills the limit to the byte
        val rule = "a{color:red}"
        val sheet = rule + " ".repeat(PluginStylesheetValidator.MAX_BYTES - rule.length)

        // when: it is validated
        val problems = problemsOf(sheet)

        // then: the limit is inclusive
        assertThat(problems).describedAs("problems of a sheet at the limit").isEmpty()
    }

    @Test
    fun `should refuse a sheet of 64 KiB plus one byte with a message naming the limit`() {
        // given: a sheet one byte over the limit
        val rule = "a{color:red}"
        val sheet = rule + " ".repeat(PluginStylesheetValidator.MAX_BYTES + 1 - rule.length)

        // when: it is validated
        val problems = problemsOf(sheet)

        // then: the size is named
        assertThat(problems).describedAs("problems").contains("64 KiB")
    }

    @Test
    fun `should refuse a sheet that starts with a byte order mark`() {
        // given: the UTF-8 byte order mark before the first rule
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "a{color:red}".toByteArray(UTF_8)

        // when: it is validated
        val problems = problemsOfBytes(bytes)

        // then: the mark is named as the problem
        assertThat(problems).describedAs("problems").contains("byte order mark")
    }

    @Test
    fun `should refuse bytes that are not UTF-8`() {
        // given: an invalid UTF-8 sequence
        val bytes = byteArrayOf('a'.code.toByte(), 0xFF.toByte(), '{'.code.toByte(), '}'.code.toByte())

        // when: it is validated
        val problems = problemsOfBytes(bytes)

        // then: the encoding is named
        assertThat(problems).describedAs("problems").contains("UTF-8")
    }

    @Test
    fun `should refuse a charset rule because the server writes the charset`() {
        // when: a sheet declares its own charset
        val problems = problemsOf("@charset \"UTF-8\";\na{color:red}")

        // then: the rule is named
        assertThat(problems).describedAs("problems").contains("@charset")
    }

    @Test
    fun `should refuse a sheet with a block that is never closed`() {
        // when: the last rule misses its closing brace
        val problems = problemsOf("a{color:red}\nb{color:blue")

        // then: the braces are named as unbalanced
        assertThat(problems).describedAs("problems").contains("unbalanced braces")
    }

    @Test
    fun `should refuse a brace that closes below the top level, which could end the layer early`() {
        // when: a sheet closes one brace more than it opens
        val problems = problemsOf("a{color:red}}\nbody{margin:0}")

        // then: the early close is named
        assertThat(problems).describedAs("problems").contains("brace")
    }

    @Test
    fun `should refuse an import rule`() {
        // when: a sheet imports another sheet
        val problems = problemsOf("@import \"https://example.org/x.css\";")

        // then: the import is named
        assertThat(problems).describedAs("problems").contains("@import")
    }

    @Test
    fun `should refuse a font-face rule`() {
        // when: a sheet declares a font
        val problems = problemsOf("@font-face { font-family: X; src: local(X); }")

        // then: the rule is named
        assertThat(problems).describedAs("problems").contains("@font-face")
    }

    @Test
    fun `should refuse a namespace rule`() {
        // when: a sheet declares a namespace
        val problems = problemsOf("@namespace svg url(http://www.w3.org/2000/svg);")

        // then: the rule is named
        assertThat(problems).describedAs("problems").contains("@namespace")
    }

    @Test
    fun `should refuse a url that points to https`() {
        // when: a rule loads a remote image
        val problems = problemsOf(".banner { background: url(https://example.org/x.png); }")

        // then: the url is named as a resource load
        assertThat(problems).describedAs("problems").contains("url(")
    }

    @Test
    fun `should refuse a quoted url that points to https`() {
        // when: the same remote image is quoted
        val problems = problemsOf(".banner { background: url(\"https://example.org/x.png\"); }")

        // then: the quotes do not hide it
        assertThat(problems).describedAs("problems").contains("url(")
    }

    @Test
    fun `should refuse a url that points to a path of the server`() {
        // when: a rule loads an API path of the same origin
        val problems = problemsOf(".banner { background: url(/api/tokens); }")

        // then: the url is refused, because same origin requests are requests too
        assertThat(problems).describedAs("problems").contains("url(")
    }

    @Test
    fun `should refuse a url with a data text css argument`() {
        // when: a data url carries a stylesheet, which would load further rules
        val problems = problemsOf(".banner { background: url(data:text/css,a{}); }")

        // then: only data images are allowed
        assertThat(problems).describedAs("problems").contains("url(")
    }

    @Test
    fun `should refuse the src function`() {
        // when: a rule uses src()
        val problems = problemsOf(".banner { background: src(\"https://example.org/x.png\"); }")

        // then: the function is named
        assertThat(problems).describedAs("problems").contains("src(")
    }

    @Test
    fun `should refuse the image-set function`() {
        // when: a rule uses image-set()
        val problems = problemsOf(".banner { background: image-set(\"a.png\" 1x); }")

        // then: the function is named
        assertThat(problems).describedAs("problems").contains("image-set(")
    }

    @Test
    fun `should refuse the prefixed image-set function`() {
        // when: a rule uses -webkit-image-set()
        val problems = problemsOf(".banner { background: -webkit-image-set(\"a.png\" 1x); }")

        // then: the prefixed function is named
        assertThat(problems).describedAs("problems").contains("image-set(")
    }

    @Test
    fun `should refuse a backslash outside a string`() {
        // when: a selector uses a CSS escape
        val problems = problemsOf(".a\\:hover { color: red; }")

        // then: the escape is named
        assertThat(problems).describedAs("problems").contains("backslash")
    }

    @Test
    fun `should refuse an escape that spells url`() {
        // when: the escape \75 is the letter u, so the rule is a url() call to the server
        val problems = problemsOf(".banner { background: \\75rl(https://example.org/x.png); }")

        // then: the escape is refused before the function name is checked
        assertThat(problems).describedAs("problems").contains("backslash")
    }

    @Test
    fun `should refuse an escape that spells import`() {
        // when: the at-keyword is written with an escape
        val problems = problemsOf("@\\69mport \"https://example.org/x.css\";")

        // then: the escape is refused
        assertThat(problems).describedAs("problems").contains("backslash")
    }

    @Test
    fun `should accept a backslash inside a string`() {
        // when: a content string holds a character as an escape
        val problems = problemsOf(".label::before { content: \"\\201C\"; }")

        // then: a string cannot name a function, so the escape is accepted
        assertThat(problems).describedAs("problems of a string escape").isEmpty()
    }

    @Test
    fun `should refuse the expression function of old browsers`() {
        // when: a width is computed by script
        val problems = problemsOf(".a { width: expression(alert(1)); }")

        // then: the function is named
        assertThat(problems).describedAs("problems").contains("expression(")
    }

    @Test
    fun `should refuse the behavior property of old browsers`() {
        // when: a rule attaches an HTC behaviour
        val problems = problemsOf(".a { behavior: url(x.htc); }")

        // then: the property is named
        assertThat(problems).describedAs("problems").contains("behavior:")
    }

    @Test
    fun `should refuse the moz-binding property`() {
        // when: a rule binds an XBL document
        val problems = problemsOf(".a { -moz-binding: url(x.xml#y); }")

        // then: the property is named
        assertThat(problems).describedAs("problems").contains("-moz-binding")
    }

    @Test
    fun `should refuse a comment that is not closed`() {
        // when: an unterminated comment would hide the rest of the sheet from a reader
        val problems = problemsOf("a{color:red} /* never closed")

        // then: the comment is named
        assertThat(problems).describedAs("problems").contains("comment")
    }

    @Test
    fun `should refuse a plugin name that the served path cannot carry`() {
        // when: the plugin name has a capital letter, which the path pattern does not allow
        val problems = PluginStylesheetValidator.problems("Example", "a{color:red}".toByteArray(UTF_8))
            .joinToString(separator = "\n")

        // then: the name is named as the problem
        assertThat(problems).describedAs("problems").contains("plugin name 'Example'")
    }

    @Test
    fun `should wrap the accepted rules in the ingot-plugins layer`() {
        // when: a sheet is wrapped
        val wrapped = PluginStylesheetValidator.wrap("a{color:red}")

        // then: the rules sit inside the layer and nothing else is added
        assertThat(wrapped).describedAs("wrapped sheet").isEqualTo("@layer ingot-plugins {\na{color:red}\n}\n")
    }
}
