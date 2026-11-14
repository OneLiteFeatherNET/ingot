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

package net.onelitefeather.ingot.meilisearch

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.IOException

/** The variable rules of the Meilisearch plugin (design D2), read from an injected environment and an injected file reader. */
internal class MeilisearchSettingsTest {

    private val https = "https://search.example.test"

    private val noFileExpected: (String) -> String = { path -> error("the key file $path must not be read") }

    private fun withUrl(vararg pairs: Pair<String, String>): Map<String, String> =
        mapOf("INGOT_MEILISEARCH_URL" to https) + pairs.toMap()

    @Test
    fun `should use the documented defaults when only the URL is set`() {
        // when: the settings are read with the URL alone
        val settings = MeilisearchSettings.read(mapOf("INGOT_MEILISEARCH_URL" to https), noFileExpected)

        // then: every other value has its documented default
        assertThat(settings.index).`as`("index uid").isEqualTo("ingot-artifacts")
        assertThat(settings.timeoutSeconds).`as`("timeout of one request in seconds").isEqualTo(30)
        assertThat(settings.taskWaitSeconds).`as`("bound of the task wait in seconds").isEqualTo(20)
        assertThat(settings.batchSize).`as`("documents per request").isEqualTo(1000)
        assertThat(settings.allowHttp).`as`("plain HTTP switch").isFalse()
        assertThat(settings.apiKey).`as`("API key").isNull()
    }

    @Test
    fun `should prefer the INGOT variable over the REPOSILITE fallback`() {
        // when: both spellings are set for the index uid
        val settings = MeilisearchSettings.read(
            withUrl("INGOT_MEILISEARCH_INDEX" to "ingot-artifacts", "REPOSILITE_MEILISEARCH_INDEX" to "legacy-artifacts"),
            noFileExpected,
        )

        // then: the INGOT value wins
        assertThat(settings.index).`as`("index uid").isEqualTo("ingot-artifacts")
    }

    @Test
    fun `should read the REPOSILITE fallback when the INGOT variable is unset`() {
        // when: only the fallback spellings are set
        val settings = MeilisearchSettings.read(
            mapOf("REPOSILITE_MEILISEARCH_URL" to https, "REPOSILITE_MEILISEARCH_INDEX" to "legacy-artifacts"),
            noFileExpected,
        )

        // then: the fallback is used for the URL and the index
        assertThat(settings.url.toString()).`as`("configured URL").isEqualTo(https)
        assertThat(settings.index).`as`("index uid").isEqualTo("legacy-artifacts")
    }

    @Test
    fun `should refuse a missing URL and name the variable`() {
        // when / then: no URL is configured
        assertThatThrownBy { MeilisearchSettings.read(emptyMap(), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_URL")
    }

    @Test
    fun `should refuse a plain HTTP URL when the HTTP switch is unset and name the switch`() {
        // when / then: an http URL without the switch
        assertThatThrownBy { MeilisearchSettings.read(mapOf("INGOT_MEILISEARCH_URL" to "http://search.example.test"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_ALLOW_HTTP")
    }

    @Test
    fun `should accept a plain HTTP URL when the HTTP switch is true and warn once per read`() {
        // when: the switch allows plain HTTP
        val warnings = mutableListOf<String>()
        val settings = MeilisearchSettings.read(
            mapOf("INGOT_MEILISEARCH_URL" to "http://search.example.test", "INGOT_MEILISEARCH_ALLOW_HTTP" to "true"),
            noFileExpected,
            warnings::add,
        )

        // then: the URL is accepted and one warning names the switch without the host
        assertThat(settings.allowHttp).`as`("plain HTTP switch").isTrue()
        assertThat(warnings).`as`("warnings of the read").containsExactly(MeilisearchSettings.PLAIN_HTTP_WARNING)
        assertThat(MeilisearchSettings.PLAIN_HTTP_WARNING)
            .`as`("warning text")
            .contains("INGOT_MEILISEARCH_ALLOW_HTTP")
            .doesNotContain("search.example.test")
    }

    @Test
    fun `should refuse a URL with user information and not echo it`() {
        // when / then: the user information is part of the URL
        assertThatThrownBy { MeilisearchSettings.read(mapOf("INGOT_MEILISEARCH_URL" to "https://admin:s3cret@search.example.test"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_URL")
            .satisfies({ failure ->
                assertThat(failure.message.orEmpty())
                    .`as`("the refusal must not echo the user name or the password")
                    .doesNotContain("admin")
                    .doesNotContain("s3cret")
            })
    }

    @Test
    fun `should refuse a URL with a scheme other than https or http`() {
        // when / then: an ftp URL
        assertThatThrownBy { MeilisearchSettings.read(mapOf("INGOT_MEILISEARCH_URL" to "ftp://search.example.test"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_URL")
    }

    @Test
    fun `should use the plain key variable when it is set`() {
        // when: the key is set in the variable
        val settings = MeilisearchSettings.read(withUrl("INGOT_MEILISEARCH_API_KEY" to "plain-key"), noFileExpected)

        // then: the key is the value of the variable
        assertThat(settings.apiKey).`as`("API key").isEqualTo("plain-key")
    }

    @Test
    fun `should read the key from the file without its trailing line breaks`() {
        // when: the key is read from a file that ends with line breaks
        val settings = MeilisearchSettings.read(
            withUrl("INGOT_MEILISEARCH_API_KEY_FILE" to "/run/secrets/meili"),
            readFile = { "file-key\r\n\n" },
        )

        // then: the key has no trailing line breaks
        assertThat(settings.apiKey).`as`("API key read from the file").isEqualTo("file-key")
    }

    @Test
    fun `should not read the key file when the plain key variable is set`() {
        // when: both the plain variable and the file are set
        val settings = MeilisearchSettings.read(
            withUrl("INGOT_MEILISEARCH_API_KEY" to "plain-key", "INGOT_MEILISEARCH_API_KEY_FILE" to "/run/secrets/meili"),
            noFileExpected,
        )

        // then: the plain variable wins and the file was not read
        assertThat(settings.apiKey).`as`("API key").isEqualTo("plain-key")
    }

    @Test
    fun `should refuse an unreadable key file and name the variable`() {
        // when / then: the file cannot be read
        assertThatThrownBy {
            MeilisearchSettings.read(withUrl("INGOT_MEILISEARCH_API_KEY_FILE" to "/missing"), readFile = { throw IOException("missing") })
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_API_KEY_FILE")
    }

    @Test
    fun `should refuse a key file that holds only line breaks and name the variable`() {
        // when / then: the file is empty after the trailing line breaks are removed
        assertThatThrownBy {
            MeilisearchSettings.read(withUrl("INGOT_MEILISEARCH_API_KEY_FILE" to "/run/secrets/meili"), readFile = { "\n" })
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_API_KEY_FILE")
    }

    @Test
    fun `should not echo the API key in a refusal of another variable`() {
        // when / then: the timeout is refused while a key is configured
        assertThatThrownBy {
            MeilisearchSettings.read(
                withUrl("INGOT_MEILISEARCH_API_KEY" to "k-secret-123", "INGOT_MEILISEARCH_TIMEOUT_SECONDS" to "soon"),
                noFileExpected,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_TIMEOUT_SECONDS")
            .satisfies({ failure ->
                assertThat(failure.message.orEmpty()).`as`("the refusal must not echo the key").doesNotContain("k-secret-123")
            })
    }

    @Test
    fun `should refuse a task wait above 60 seconds and name the variable`() {
        // when / then: the wait must not outlast the outbox lease
        assertThatThrownBy { MeilisearchSettings.read(withUrl("INGOT_MEILISEARCH_TASK_WAIT_SECONDS" to "61"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_TASK_WAIT_SECONDS")
    }

    @Test
    fun `should refuse a task wait of zero seconds`() {
        // when / then: the wait has a lower bound of one second
        assertThatThrownBy { MeilisearchSettings.read(withUrl("INGOT_MEILISEARCH_TASK_WAIT_SECONDS" to "0"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_TASK_WAIT_SECONDS")
    }

    @Test
    fun `should refuse a batch size above 10000 and name the variable`() {
        // when / then: 10001 documents is above the limit
        assertThatThrownBy { MeilisearchSettings.read(withUrl("INGOT_MEILISEARCH_BATCH_SIZE" to "10001"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_BATCH_SIZE")
    }

    @Test
    fun `should refuse a timeout above 300 seconds and name the variable`() {
        // when / then: 301 seconds is above the limit
        assertThatThrownBy { MeilisearchSettings.read(withUrl("INGOT_MEILISEARCH_TIMEOUT_SECONDS" to "301"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_TIMEOUT_SECONDS")
    }

    @Test
    fun `should refuse an index uid with a space and name the variable`() {
        // when / then: a space is not allowed in an index uid
        assertThatThrownBy { MeilisearchSettings.read(withUrl("INGOT_MEILISEARCH_INDEX" to "ingot artifacts"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_INDEX")
    }

    @Test
    fun `should refuse an index uid longer than 64 characters`() {
        // when / then: 65 characters is one too many
        assertThatThrownBy { MeilisearchSettings.read(withUrl("INGOT_MEILISEARCH_INDEX" to "a".repeat(65)), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_INDEX")
    }

}
