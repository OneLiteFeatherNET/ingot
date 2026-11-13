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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException

/** The variable rules of the OpenSearch plugin (design D2), read from an injected environment and an injected file reader. */
internal class OpenSearchSettingsTest {

    private val https = "https://search.example.test:9200"

    private val noFileExpected: (String) -> String = { path -> error("the password file $path must not be read") }

    @Test
    fun `should use the documented defaults when only the URLs are set`() {
        // when: the settings are read with the URLs alone
        val settings = OpenSearchSettings.read(mapOf("INGOT_OPENSEARCH_URLS" to https), noFileExpected)

        // then: every other value has its documented default
        assertThat(settings.index).`as`("index prefix").isEqualTo("ingot-artifacts")
        assertThat(settings.bulkSize).`as`("actions per bulk request").isEqualTo(500)
        assertThat(settings.timeoutSeconds).`as`("timeout of one request in seconds").isEqualTo(30)
        assertThat(settings.replicas).`as`("replicas of the index").isEqualTo(1)
        assertThat(settings.allowHttp).`as`("plain HTTP switch").isFalse()
        assertThat(settings.username).`as`("user name").isNull()
    }

    @Test
    fun `should prefer the INGOT variable over the REPOSILITE fallback`() {
        // when: both spellings are set for the index prefix
        val settings = OpenSearchSettings.read(
            mapOf(
                "INGOT_OPENSEARCH_URLS" to https,
                "INGOT_OPENSEARCH_INDEX" to "ingot-artifacts",
                "REPOSILITE_OPENSEARCH_INDEX" to "legacy-artifacts",
            ),
            noFileExpected,
        )

        // then: the INGOT value wins
        assertThat(settings.index).`as`("index prefix").isEqualTo("ingot-artifacts")
    }

    @Test
    fun `should read the REPOSILITE fallback when the INGOT variable is unset`() {
        // when: only the fallback spelling is set for the index prefix
        val settings = OpenSearchSettings.read(
            mapOf("REPOSILITE_OPENSEARCH_URLS" to https, "REPOSILITE_OPENSEARCH_INDEX" to "legacy-artifacts"),
            noFileExpected,
        )

        // then: the fallback is used
        assertThat(settings.index).`as`("index prefix").isEqualTo("legacy-artifacts")
    }

    @Test
    fun `should split the URL list on commas and trim every entry`() {
        // when: the list has spaces around its entries
        val settings = OpenSearchSettings.read(
            mapOf("INGOT_OPENSEARCH_URLS" to " https://a.example.test:9200 , https://b.example.test:9200 "),
            noFileExpected,
        )

        // then: both entries are read without the spaces
        assertThat(settings.urls.map { it.toString() })
            .`as`("the configured cluster URLs")
            .containsExactly("https://a.example.test:9200", "https://b.example.test:9200")
    }

    @Test
    fun `should refuse a missing URL list and name the variable`() {
        // when / then: no URL is configured
        assertThatThrownBy { OpenSearchSettings.read(emptyMap(), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_OPENSEARCH_URLS")
    }

    @Test
    fun `should refuse a plain HTTP URL when the HTTP switch is unset and name the variable`() {
        // when / then: an http URL without the switch
        assertThatThrownBy {
            OpenSearchSettings.read(mapOf("INGOT_OPENSEARCH_URLS" to "http://search.example.test:9200"), noFileExpected)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_OPENSEARCH_URLS")
    }

    @Test
    fun `should accept a plain HTTP URL when the HTTP switch is true`() {
        // when: the switch allows plain HTTP
        val settings = OpenSearchSettings.read(
            mapOf("INGOT_OPENSEARCH_URLS" to "http://search.example.test:9200", "INGOT_OPENSEARCH_ALLOW_HTTP" to "true"),
            noFileExpected,
        )

        // then: the URL is accepted and the switch is recorded
        assertThat(settings.allowHttp).`as`("plain HTTP switch").isTrue()
        assertThat(settings.urls.single().scheme).`as`("scheme of the configured URL").isEqualTo("http")
    }

    @Test
    fun `should refuse a URL with user information and not echo it`() {
        // when / then: the user information is part of the URL
        assertThatThrownBy {
            OpenSearchSettings.read(mapOf("INGOT_OPENSEARCH_URLS" to "https://admin:s3cret@search.example.test:9200"), noFileExpected)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_OPENSEARCH_URLS")
            .satisfies({ failure ->
                assertThat(failure.message.orEmpty())
                    .`as`("the refusal must not echo the user name or the password of the URL")
                    .doesNotContain("admin")
                    .doesNotContain("s3cret")
            })
    }

    @Test
    fun `should refuse a malformed URL without echoing its content`() {
        // when / then: the URL does not parse and carries a password
        assertThatThrownBy {
            OpenSearchSettings.read(mapOf("INGOT_OPENSEARCH_URLS" to "https://admin:s3cret@bad host.example.test"), noFileExpected)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_OPENSEARCH_URLS")
            .satisfies({ failure ->
                assertThat(failure.message.orEmpty()).`as`("the refusal must not echo the URL").doesNotContain("s3cret")
            })
    }

    @Test
    fun `should refuse a bulk size above the allowed range and name the variable`() {
        // when / then: 5001 actions is above the limit of 5000
        assertThatThrownBy { OpenSearchSettings.read(withUrl("INGOT_OPENSEARCH_BULK_SIZE" to "5001"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_OPENSEARCH_BULK_SIZE")
    }

    @Test
    fun `should refuse a timeout that is not a whole number and name the variable`() {
        // when / then: the timeout must be a number of seconds
        assertThatThrownBy { OpenSearchSettings.read(withUrl("INGOT_OPENSEARCH_TIMEOUT_SECONDS" to "soon"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_OPENSEARCH_TIMEOUT_SECONDS")
    }

    @Test
    fun `should refuse a replica count above the allowed range and name the variable`() {
        // when / then: 11 replicas is above the limit of 10
        assertThatThrownBy { OpenSearchSettings.read(withUrl("INGOT_OPENSEARCH_REPLICAS" to "11"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_OPENSEARCH_REPLICAS")
    }

    @ParameterizedTest
    @ValueSource(strings = ["Ingot-Artifacts", "ingot artifacts", "ingot.artifacts", "-leading"])
    fun `should refuse an index name outside the allowed characters and name the variable`(name: String) {
        // when / then: the name breaks the rule of lower case letters, digits, dashes and underscores
        assertThatThrownBy { OpenSearchSettings.read(withUrl("INGOT_OPENSEARCH_INDEX" to name), noFileExpected) }
            .describedAs("index name '$name' must be refused")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_OPENSEARCH_INDEX")
    }

    @Test
    fun `should refuse an index name longer than 64 characters`() {
        // when / then: 65 characters is one too many
        assertThatThrownBy { OpenSearchSettings.read(withUrl("INGOT_OPENSEARCH_INDEX" to "a".repeat(65)), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_OPENSEARCH_INDEX")
    }

    @Test
    fun `should use the plain password and not read the file when both are set`() {
        // given: a plain password and a password file, with a user name
        val reads = mutableListOf<String>()

        // when: the settings are read
        val settings = OpenSearchSettings.read(
            withUrl(
                "INGOT_OPENSEARCH_USERNAME" to "indexer",
                "INGOT_OPENSEARCH_PASSWORD" to "plain-secret",
                "INGOT_OPENSEARCH_PASSWORD_FILE" to "/run/secrets/opensearch",
            ),
            { path -> reads.add(path); "file-secret" },
        )

        // then: the plain value wins and the file is not read at all
        assertThat(settings.password).`as`("password").isEqualTo("plain-secret")
        assertThat(reads).`as`("reads of the password file").isEmpty()
    }

    @Test
    fun `should read the password file once and strip its trailing line breaks`() {
        // given: a password file with two trailing line breaks and a user name
        val reads = mutableListOf<String>()

        // when: the settings are read
        val settings = OpenSearchSettings.read(
            withUrl("INGOT_OPENSEARCH_USERNAME" to "indexer", "INGOT_OPENSEARCH_PASSWORD_FILE" to "/run/secrets/opensearch"),
            { path -> reads.add(path); "s3cret\r\n\n" },
        )

        // then: the file was read once and the password has no trailing line breaks
        assertThat(reads).`as`("reads of the password file").containsExactly("/run/secrets/opensearch")
        assertThat(settings.password).`as`("password from the file").isEqualTo("s3cret")
    }

    @Test
    fun `should name the password file variable when the file cannot be read`() {
        // given: the file cannot be read
        val unreadable: (String) -> String = { path -> throw IOException("cannot read $path") }

        // when / then: the start is refused and the message names the variable
        assertThatThrownBy {
            OpenSearchSettings.read(
                withUrl("INGOT_OPENSEARCH_USERNAME" to "indexer", "INGOT_OPENSEARCH_PASSWORD_FILE" to "/run/secrets/missing"),
                unreadable,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_OPENSEARCH_PASSWORD_FILE")
    }

    @Test
    fun `should refuse a user name without a password and name the password variable`() {
        // when / then: the user name has no password
        assertThatThrownBy { OpenSearchSettings.read(withUrl("INGOT_OPENSEARCH_USERNAME" to "indexer"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_OPENSEARCH_PASSWORD")
    }

    @Test
    fun `should refuse a password without a user name and name the user name variable`() {
        // when / then: the password has no user name
        assertThatThrownBy { OpenSearchSettings.read(withUrl("INGOT_OPENSEARCH_PASSWORD" to "s3cret"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_OPENSEARCH_USERNAME")
            .satisfies({ failure ->
                assertThat(failure.message.orEmpty()).`as`("the refusal must not echo the password").doesNotContain("s3cret")
            })
    }

    @Test
    fun `should accept neither a user name nor a password and mark the cluster as unauthenticated`() {
        // when: no credentials are configured
        val settings = OpenSearchSettings.read(withUrl(), noFileExpected)

        // then: the start is accepted and the plugin can warn about it
        assertThat(settings.unauthenticated).`as`("no credentials configured").isTrue()
    }

    private fun withUrl(vararg extra: Pair<String, String>): Map<String, String> =
        mapOf("INGOT_OPENSEARCH_URLS" to https) + extra
}
