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

package net.onelitefeather.ingot.plugin.description

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PluginDescriptionNormaliserTest {

    @Test
    fun `should remove leading and trailing whitespace`() {
        // when: a description is surrounded by spaces and tabs
        val result = PluginDescriptionNormaliser.normalise("  \tVerifies checksums\t  ")

        // then: only the text between them is kept
        assertThat(result.text)
            .describedAs("normalised text")
            .isEqualTo("Verifies checksums")
        assertThat(result.cut)
            .describedAs("cut flag of a short description")
            .isFalse()
    }

    @Test
    fun `should remove line breaks without inserting a space`() {
        // when: a description spans two lines
        val result = PluginDescriptionNormaliser.normalise("  First line\nsecond line  ")

        // then: the line break is removed and the text is joined as it is
        assertThat(result.text)
            .describedAs("normalised text of a two line description")
            .isEqualTo("First linesecond line")
    }

    @Test
    fun `should remove control characters other than line breaks`() {
        // when: a description contains a carriage return, a bell and a null character
        val result = PluginDescriptionNormaliser.normalise("ab\rc\u0007d\u0000e")

        // then: every control character is gone and the visible characters remain in order
        assertThat(result.text)
            .describedAs("normalised text without control characters")
            .isEqualTo("abcde")
    }

    @Test
    fun `should keep markup as plain characters`() {
        // when: a description looks like HTML
        val result = PluginDescriptionNormaliser.normalise("<b>bold</b>")

        // then: the characters are kept, because the description is text and never markup
        assertThat(result.text)
            .describedAs("normalised text of a markup like description")
            .isEqualTo("<b>bold</b>")
    }

    @Test
    fun `should read an empty result for an empty input`() {
        // when: the description is empty
        val result = PluginDescriptionNormaliser.normalise("")

        // then: the result is the empty text, which means no description
        assertThat(result.text)
            .describedAs("normalised text of an empty input")
            .isEmpty()
        assertThat(result.cut)
            .describedAs("cut flag of an empty input")
            .isFalse()
    }

    @Test
    fun `should read an empty result for whitespace and control characters only`() {
        // when: the description contains nothing but spaces and line breaks
        val result = PluginDescriptionNormaliser.normalise(" \n\t \r\n ")

        // then: the result is the empty text, and it is not reported as cut
        assertThat(result.text)
            .describedAs("normalised text of whitespace only")
            .isEmpty()
        assertThat(result.cut)
            .describedAs("cut flag of whitespace only")
            .isFalse()
    }

    @Test
    fun `should keep a description of exactly 280 characters unchanged`() {
        // given: a description at the limit
        val raw = "a".repeat(280)

        // when: it is normalised
        val result = PluginDescriptionNormaliser.normalise(raw)

        // then: nothing is cut
        assertThat(result.text)
            .describedAs("normalised text at the limit")
            .isEqualTo(raw)
        assertThat(result.cut)
            .describedAs("cut flag at the limit")
            .isFalse()
    }

    @Test
    fun `should cut a description of 300 characters to its first 280`() {
        // given: a description over the limit, whose first 280 characters are known
        val raw = "a".repeat(280) + "b".repeat(20)

        // when: it is normalised
        val result = PluginDescriptionNormaliser.normalise(raw)

        // then: the first 280 characters are kept and the cut is reported
        assertThat(result.text)
            .describedAs("normalised text over the limit")
            .isEqualTo("a".repeat(280))
        assertThat(result.cut)
            .describedAs("cut flag over the limit")
            .isTrue()
    }

    @Test
    fun `should count code points and never split a surrogate pair at the limit`() {
        // given: 281 emoji, each one code point but two UTF-16 units
        val raw = "😀".repeat(281)

        // when: it is normalised
        val result = PluginDescriptionNormaliser.normalise(raw)

        // then: exactly 280 code points remain, with every emoji whole
        assertThat(result.text.codePointCount(0, result.text.length))
            .describedAs("code points after the cut")
            .isEqualTo(280)
        assertThat(result.text)
            .describedAs("normalised text of emoji over the limit")
            .isEqualTo("😀".repeat(280))
        assertThat(result.cut)
            .describedAs("cut flag of emoji over the limit")
            .isTrue()
    }

    @Test
    fun `should apply the limit after the control characters are removed`() {
        // given: 280 visible characters with a line break in the middle, which is 281 characters before the removal
        val raw = "a".repeat(140) + "\n" + "a".repeat(140)

        // when: it is normalised
        val result = PluginDescriptionNormaliser.normalise(raw)

        // then: the line break does not count, so nothing is cut
        assertThat(result.text)
            .describedAs("normalised text of 280 visible characters with a line break")
            .isEqualTo("a".repeat(280))
        assertThat(result.cut)
            .describedAs("cut flag after the removal of the line break")
            .isFalse()
    }

}
