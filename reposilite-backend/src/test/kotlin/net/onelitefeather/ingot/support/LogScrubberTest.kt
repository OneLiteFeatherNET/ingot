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

package net.onelitefeather.ingot.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class LogScrubberTest {

    @Test
    fun `should replace a known secret value with the redacted marker`() {
        // given: a scrubber that knows one secret
        val scrubber = LogScrubber(listOf("planted-value-1"))
        val tally = RedactionTally()

        // when: a line carries the secret
        val line = scrubber.scrub("upload failed for planted-value-1 on node", tally)

        // then: the secret is replaced and counted
        assertThat(line).`as`("scrubbed line").isEqualTo("upload failed for [redacted] on node")
        assertThat(tally.count).`as`("redactions in the line").isEqualTo(1)
    }

    @Test
    fun `should replace the longest of two overlapping secrets first`() {
        // given: a short secret that is a prefix of a longer one
        val scrubber = LogScrubber(listOf("planted", "planted-long-value"))

        // when: the line carries the longer one
        val line = scrubber.scrub("value planted-long-value end", RedactionTally())

        // then: the whole longer value is replaced, not only its prefix
        assertThat(line).`as`("scrubbed line").isEqualTo("value [redacted] end")
    }

    @Test
    fun `should not replace a known value shorter than four characters`() {
        // given: a secret of three characters
        val scrubber = LogScrubber(listOf("abc"))

        // when: a line carries the same characters
        val line = scrubber.scrub("abc stays", RedactionTally())

        // then: the value scrub leaves it (the guard still refuses such a value as a whole word)
        assertThat(line).`as`("line with a short value").isEqualTo("abc stays")
    }

    @Test
    fun `should replace the whole authorization header`() {
        val line = LogScrubber(emptyList()).scrub("GET / Authorization: Basic dXNlcjpwYXNz", RedactionTally())

        assertThat(line).`as`("scrubbed header").isEqualTo("GET / Authorization: [redacted]")
    }

    @Test
    fun `should replace a bearer token`() {
        val line = LogScrubber(emptyList()).scrub("retry with Bearer abc.def-ghi_jkl", RedactionTally())

        assertThat(line).`as`("scrubbed bearer").isEqualTo("retry with Bearer [redacted]")
    }

    @Test
    fun `should replace the credentials of a URL`() {
        val line = LogScrubber(emptyList()).scrub("fetch https://deploy:pass123@repo.example/path", RedactionTally())

        assertThat(line).`as`("scrubbed URL").isEqualTo("fetch https://[redacted]@repo.example/path")
    }

    @Test
    fun `should replace the value of a key assignment in plain text`() {
        val line = LogScrubber(emptyList()).scrub("password=hunter22 secret: xyz token=abc", RedactionTally())

        assertThat(line).`as`("scrubbed assignments").isEqualTo("password=[redacted] secret: [redacted] token=[redacted]")
    }

    @Test
    fun `should replace the value of a key in a structured line and keep the quotes`() {
        val line = LogScrubber(emptyList()).scrub("""{"secret_key":"abc-def","password":"p4ss","id":"7"}""", RedactionTally())

        assertThat(line).`as`("scrubbed JSON line").isEqualTo("""{"secret_key":"[redacted]","password":"[redacted]","id":"7"}""")
    }

    @Test
    fun `should leave a line without a secret unchanged and count nothing`() {
        val tally = RedactionTally()

        val line = LogScrubber(listOf("planted-value-1")).scrub("GET /releases/org/example 200", tally)

        assertThat(line).`as`("untouched line").isEqualTo("GET /releases/org/example 200")
        assertThat(tally.count).`as`("redactions in an untouched line").isZero()
    }
}
