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

package net.onelitefeather.ingot.elasticsearch

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException

/** The variable rules of the Elasticsearch plugin (design D4), read from an injected environment and an injected file reader. */
internal class ElasticsearchSettingsTest {

    private val https = "https://search.example.test:9200"

    private val apiKey = "QXBpS2V5SWQ6c2VjcmV0"

    private val noFileExpected: (String) -> String = { path -> error("the secret file $path must not be read") }

    @Test
    fun `should use the documented defaults when the URLs and an API key are set`() {
        // when: the settings are read with the URLs and a key
        val settings = ElasticsearchSettings.read(withUrl("INGOT_ELASTICSEARCH_API_KEY" to apiKey), noFileExpected)

        // then: every other value has its documented default
        assertThat(settings.index).`as`("index prefix").isEqualTo("ingot-artifacts")
        assertThat(settings.bulkSize).`as`("actions per bulk request").isEqualTo(500)
        assertThat(settings.timeoutSeconds).`as`("timeout of one request in seconds").isEqualTo(30)
        assertThat(settings.replicas).`as`("replicas of the index").isEqualTo(1)
        assertThat(settings.allowHttp).`as`("plain HTTP switch").isFalse()
        assertThat(settings.username).`as`("user name").isNull()
        assertThat(settings.password).`as`("password").isNull()
    }

    @Test
    fun `should prefer the INGOT variable over the REPOSILITE fallback`() {
        // when: both spellings are set for the index prefix
        val settings = ElasticsearchSettings.read(
            withUrl(
                "INGOT_ELASTICSEARCH_API_KEY" to apiKey,
                "INGOT_ELASTICSEARCH_INDEX" to "ingot-artifacts",
                "REPOSILITE_ELASTICSEARCH_INDEX" to "legacy-artifacts",
            ),
            noFileExpected,
        )

        // then: the INGOT value wins
        assertThat(settings.index).`as`("index prefix").isEqualTo("ingot-artifacts")
    }

    @Test
    fun `should read the REPOSILITE fallback when the INGOT variable is unset`() {
        // when: only the fallback spellings are set
        val settings = ElasticsearchSettings.read(
            mapOf(
                "REPOSILITE_ELASTICSEARCH_URLS" to https,
                "REPOSILITE_ELASTICSEARCH_API_KEY" to apiKey,
                "REPOSILITE_ELASTICSEARCH_INDEX" to "legacy-artifacts",
            ),
            noFileExpected,
        )

        // then: the fallback is used for the index prefix
        assertThat(settings.index).`as`("index prefix").isEqualTo("legacy-artifacts")
    }

    @Test
    fun `should split the URL list on commas and trim every entry`() {
        // when: the list has spaces around its entries
        val settings = ElasticsearchSettings.read(
            mapOf(
                "INGOT_ELASTICSEARCH_URLS" to " https://a.example.test:9200 , https://b.example.test:9200 ",
                "INGOT_ELASTICSEARCH_API_KEY" to apiKey,
            ),
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
        assertThatThrownBy { ElasticsearchSettings.read(mapOf("INGOT_ELASTICSEARCH_API_KEY" to apiKey), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_URLS")
    }

    @Test
    fun `should refuse a plain HTTP URL when the HTTP switch is unset and name the variable`() {
        // when / then: an http URL without the switch
        assertThatThrownBy {
            ElasticsearchSettings.read(
                mapOf("INGOT_ELASTICSEARCH_URLS" to "http://search.example.test:9200", "INGOT_ELASTICSEARCH_API_KEY" to apiKey),
                noFileExpected,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_ALLOW_HTTP")
    }

    @Test
    fun `should accept a plain HTTP URL when the HTTP switch is true and log a warning at every start`() {
        // given: a sink for the log lines of the read
        val logged = mutableListOf<String>()

        // when: the switch allows plain HTTP
        val settings = ElasticsearchSettings.read(
            mapOf(
                "INGOT_ELASTICSEARCH_URLS" to "http://search.example.test:9200",
                "INGOT_ELASTICSEARCH_ALLOW_HTTP" to "true",
                "INGOT_ELASTICSEARCH_API_KEY" to apiKey,
            ),
            noFileExpected,
            logged::add,
        )

        // then: the URL is accepted, the switch is recorded and the warning names the switch
        assertThat(settings.allowHttp).`as`("plain HTTP switch").isTrue()
        assertThat(settings.urls.single().scheme).`as`("scheme of the configured URL").isEqualTo("http")
        assertThat(logged).`as`("log lines of the read").hasSize(1)
        assertThat(logged.single())
            .`as`("warning of the plain HTTP switch")
            .contains("INGOT_ELASTICSEARCH_ALLOW_HTTP")
            .doesNotContain("search.example.test")
    }

    @Test
    fun `should refuse a URL with user information and not echo it`() {
        // when / then: the user information is part of the URL
        assertThatThrownBy {
            ElasticsearchSettings.read(
                mapOf(
                    "INGOT_ELASTICSEARCH_URLS" to "https://admin:s3cret@search.example.test:9200",
                    "INGOT_ELASTICSEARCH_API_KEY" to apiKey,
                ),
                noFileExpected,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_URLS")
            .satisfies({ failure ->
                assertThat(failure.message.orEmpty())
                    .`as`("the refusal must not echo the user name or the password of the URL")
                    .doesNotContain("admin")
                    .doesNotContain("s3cret")
            })
    }

    @Test
    fun `should refuse an API key together with a user name and name both variables without their values`() {
        // when / then: the key and the user name are both set
        assertThatThrownBy {
            ElasticsearchSettings.read(
                withUrl(
                    "INGOT_ELASTICSEARCH_API_KEY" to apiKey,
                    "INGOT_ELASTICSEARCH_USERNAME" to "indexer",
                    "INGOT_ELASTICSEARCH_PASSWORD" to "s3cret",
                ),
                noFileExpected,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_API_KEY")
            .hasMessageContaining("INGOT_ELASTICSEARCH_USERNAME")
            .satisfies({ failure ->
                assertThat(failure.message.orEmpty())
                    .`as`("the refusal must not echo the key, the user name or the password")
                    .doesNotContain(apiKey)
                    .doesNotContain("indexer")
                    .doesNotContain("s3cret")
            })
    }

    @Test
    fun `should read the API key from its file and strip the trailing line breaks`() {
        // given: a key file with trailing line breaks
        val reads = mutableListOf<String>()

        // when: only the file variable is set
        val settings = ElasticsearchSettings.read(
            withUrl("INGOT_ELASTICSEARCH_API_KEY_FILE" to "/run/secrets/elasticsearch-key"),
            { path -> reads.add(path); "$apiKey\r\n\n" },
        )

        // then: the file was read once and the key has no trailing line breaks
        assertThat(reads).`as`("reads of the key file").containsExactly("/run/secrets/elasticsearch-key")
        assertThat(settings.apiKey).`as`("API key from the file").isEqualTo(apiKey)
    }

    @Test
    fun `should use the plain API key and not read the file when both are set`() {
        // given: a plain key and a key file
        val reads = mutableListOf<String>()

        // when: the settings are read
        val settings = ElasticsearchSettings.read(
            withUrl("INGOT_ELASTICSEARCH_API_KEY" to apiKey, "INGOT_ELASTICSEARCH_API_KEY_FILE" to "/run/secrets/elasticsearch-key"),
            { path -> reads.add(path); "file-key" },
        )

        // then: the plain value wins and the file is not read at all
        assertThat(settings.apiKey).`as`("API key").isEqualTo(apiKey)
        assertThat(reads).`as`("reads of the key file").isEmpty()
    }

    @Test
    fun `should name the API key variable when its file cannot be read`() {
        // given: the key file cannot be read
        val unreadable: (String) -> String = { path -> throw IOException("cannot read $path") }

        // when / then: the start is refused and the message names the variable
        assertThatThrownBy {
            ElasticsearchSettings.read(withUrl("INGOT_ELASTICSEARCH_API_KEY_FILE" to "/run/secrets/missing"), unreadable)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_API_KEY_FILE")
    }

    @Test
    fun `should read the password file once and strip its trailing line breaks`() {
        // given: a password file with two trailing line breaks and a user name
        val reads = mutableListOf<String>()

        // when: the settings are read
        val settings = ElasticsearchSettings.read(
            withUrl("INGOT_ELASTICSEARCH_USERNAME" to "indexer", "INGOT_ELASTICSEARCH_PASSWORD_FILE" to "/run/secrets/elasticsearch"),
            { path -> reads.add(path); "s3cret\r\n\n" },
        )

        // then: the file was read once and the password has no trailing line breaks
        assertThat(reads).`as`("reads of the password file").containsExactly("/run/secrets/elasticsearch")
        assertThat(settings.password).`as`("password from the file").isEqualTo("s3cret")
    }

    @Test
    fun `should use the plain password and not read the file when both are set`() {
        // given: a plain password and a password file, with a user name
        val reads = mutableListOf<String>()

        // when: the settings are read
        val settings = ElasticsearchSettings.read(
            withUrl(
                "INGOT_ELASTICSEARCH_USERNAME" to "indexer",
                "INGOT_ELASTICSEARCH_PASSWORD" to "plain-secret",
                "INGOT_ELASTICSEARCH_PASSWORD_FILE" to "/run/secrets/elasticsearch",
            ),
            { path -> reads.add(path); "file-secret" },
        )

        // then: the plain value wins and the file is not read at all
        assertThat(settings.password).`as`("password").isEqualTo("plain-secret")
        assertThat(reads).`as`("reads of the password file").isEmpty()
    }

    @Test
    fun `should name the password file variable when the file cannot be read`() {
        // given: the file cannot be read
        val unreadable: (String) -> String = { path -> throw IOException("cannot read $path") }

        // when / then: the start is refused and the message names the variable
        assertThatThrownBy {
            ElasticsearchSettings.read(
                withUrl("INGOT_ELASTICSEARCH_USERNAME" to "indexer", "INGOT_ELASTICSEARCH_PASSWORD_FILE" to "/run/secrets/missing"),
                unreadable,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_PASSWORD_FILE")
    }

    @Test
    fun `should refuse a user name without a password and name the password variable`() {
        // when / then: the user name has no password
        assertThatThrownBy { ElasticsearchSettings.read(withUrl("INGOT_ELASTICSEARCH_USERNAME" to "indexer"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_PASSWORD")
    }

    @Test
    fun `should refuse a password without a user name and not echo the password`() {
        // when / then: the password has no user name
        assertThatThrownBy { ElasticsearchSettings.read(withUrl("INGOT_ELASTICSEARCH_PASSWORD" to "s3cret"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_USERNAME")
            .satisfies({ failure ->
                assertThat(failure.message.orEmpty()).`as`("the refusal must not echo the password").doesNotContain("s3cret")
            })
    }

    @Test
    fun `should refuse a cluster without any credential unless plain HTTP is switched on`() {
        // when / then: neither a key nor a user name, and the switch is unset
        assertThatThrownBy { ElasticsearchSettings.read(withUrl(), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_ALLOW_HTTP")
    }

    @Test
    fun `should accept a cluster without any credential when plain HTTP is switched on`() {
        // when: the cluster runs without security and the switch allows it
        val settings = ElasticsearchSettings.read(
            mapOf("INGOT_ELASTICSEARCH_URLS" to "http://search.example.test:9200", "INGOT_ELASTICSEARCH_ALLOW_HTTP" to "true"),
            noFileExpected,
            {},
        )

        // then: the start is accepted without credentials
        assertThat(settings.apiKey).`as`("API key").isNull()
        assertThat(settings.username).`as`("user name").isNull()
    }

    @Test
    fun `should refuse a bulk size above the allowed range and name the variable`() {
        // when / then: 5001 actions is above the limit of 5000
        assertThatThrownBy { ElasticsearchSettings.read(withKey("INGOT_ELASTICSEARCH_BULK_SIZE" to "5001"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_BULK_SIZE")
    }

    @Test
    fun `should refuse a timeout that is not a whole number and name the variable`() {
        // when / then: the timeout must be a number of seconds
        assertThatThrownBy { ElasticsearchSettings.read(withKey("INGOT_ELASTICSEARCH_TIMEOUT_SECONDS" to "soon"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_TIMEOUT_SECONDS")
    }

    @Test
    fun `should refuse a replica count above the allowed range and name the variable`() {
        // when / then: 11 replicas is above the limit of 10
        assertThatThrownBy { ElasticsearchSettings.read(withKey("INGOT_ELASTICSEARCH_REPLICAS" to "11"), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_REPLICAS")
    }

    @ParameterizedTest
    @ValueSource(strings = ["Ingot-Artifacts", "ingot artifacts", "ingot.artifacts", "-leading"])
    fun `should refuse an index name outside the allowed characters and name the variable`(name: String) {
        // when / then: the name breaks the rule of lower case letters, digits, dashes and underscores
        assertThatThrownBy { ElasticsearchSettings.read(withKey("INGOT_ELASTICSEARCH_INDEX" to name), noFileExpected) }
            .describedAs("index name '$name' must be refused")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_INDEX")
    }

    @Test
    fun `should refuse an index name longer than 64 characters`() {
        // when / then: 65 characters is one too many
        assertThatThrownBy { ElasticsearchSettings.read(withKey("INGOT_ELASTICSEARCH_INDEX" to "a".repeat(65)), noFileExpected) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_INDEX")
    }

    @Test
    fun `should build the ApiKey authorization from the key when a key is set`() {
        // when: the settings are read with a key
        val settings = ElasticsearchSettings.read(withKey(), noFileExpected)

        // then: the header is the ApiKey scheme with the key as it was given
        assertThat(settings.authorization).`as`("authorization header").isEqualTo("ApiKey $apiKey")
    }

    @Test
    fun `should build the basic authorization from the user and the password when no key is set`() {
        // when: the settings are read with a user and a password, and plain HTTP off
        val settings = ElasticsearchSettings.read(
            withUrl("INGOT_ELASTICSEARCH_USERNAME" to "indexer", "INGOT_ELASTICSEARCH_PASSWORD" to "s3cret"),
            noFileExpected,
        )

        // then: the header is the basic scheme of the user and the password
        assertThat(settings.authorization)
            .`as`("authorization header")
            .isEqualTo("Basic " + java.util.Base64.getEncoder().encodeToString("indexer:s3cret".toByteArray()))
    }

    @Test
    fun `should send no authorization when the cluster runs without security`() {
        // when: plain HTTP is switched on and no credential is set
        val settings = ElasticsearchSettings.read(
            mapOf("INGOT_ELASTICSEARCH_URLS" to "http://search.example.test:9200", "INGOT_ELASTICSEARCH_ALLOW_HTTP" to "true"),
            noFileExpected,
        )

        // then: no authorization header is built
        assertThat(settings.authorization).`as`("authorization header").isNull()
    }

    private fun withUrl(vararg extra: Pair<String, String>): Map<String, String> =
        mapOf("INGOT_ELASTICSEARCH_URLS" to https) + extra

    private fun withKey(vararg extra: Pair<String, String>): Map<String, String> =
        withUrl("INGOT_ELASTICSEARCH_API_KEY" to apiKey) + extra
}
