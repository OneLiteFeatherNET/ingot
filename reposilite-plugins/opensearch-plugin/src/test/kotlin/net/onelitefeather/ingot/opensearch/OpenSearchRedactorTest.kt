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

package net.onelitefeather.ingot.opensearch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The redaction of every text that leaves the plugin: a cluster may echo its credentials in an error body. */
internal class OpenSearchRedactorTest {

    private val redactor = OpenSearchRedactor(listOf("s3cret", "indexer"))

    @Test
    fun `should remove the password from an error text that echoes it`() {
        // when: the cluster echoes the password in its error
        val shown = redactor.redact("authentication failed for password s3cret")

        // then: the password is gone
        assertThat(shown).`as`("redacted text").doesNotContain("s3cret")
    }

    @Test
    fun `should remove the user name from an error text that echoes it`() {
        // when: the cluster echoes the user name in its error
        val shown = redactor.redact("user indexer is not authorized")

        // then: the user name is gone
        assertThat(shown).`as`("redacted text").doesNotContain("indexer")
    }

    @Test
    fun `should remove the user information of a URL in an error text`() {
        // when: a URL with credentials appears in the text
        val shown = redactor.redact("could not reach https://admin:s3cret@search.example.test:9200/_bulk")

        // then: neither the user name nor the password is shown, and the host stays readable
        assertThat(shown).`as`("redacted text").doesNotContain("admin").doesNotContain("s3cret").contains("search.example.test")
    }

    @Test
    fun `should leave the text unchanged when every configured secret is blank`() {
        // given: a redactor with blank secrets only, which must not match between every character
        val blankRedactor = OpenSearchRedactor(listOf("", "   "))

        // when: a plain text is redacted
        val shown = blankRedactor.redact("plain text")

        // then: nothing is replaced
        assertThat(shown).`as`("redacted text").isEqualTo("plain text")
    }

}
