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

/** The warning of plain HTTP (design D2): a captured sink receives it at every read, and no secret reaches it. */
internal class OpenSearchPlainHttpWarningTest {

    private val noFileExpected: (String) -> String = { path -> error("the password file $path must not be read") }

    private val messages = mutableListOf<String>()

    @Test
    fun `should warn through the log sink when plain HTTP is accepted`() {
        // when: the settings are read with the plain HTTP switch on
        OpenSearchSettings.read(
            mapOf("INGOT_OPENSEARCH_URLS" to "http://search.example.test:9200", "INGOT_OPENSEARCH_ALLOW_HTTP" to "true"),
            noFileExpected,
            log = messages::add,
        )

        // then: exactly one warning names the switch
        assertThat(messages).`as`("warnings of one start").hasSize(1)
        assertThat(messages.single()).`as`("warning").contains("INGOT_OPENSEARCH_ALLOW_HTTP")
    }

    @Test
    fun `should warn again at every read so that every start shows the warning`() {
        // when: the settings are read twice, as two starts do
        val environment = mapOf("INGOT_OPENSEARCH_URLS" to "https://search.example.test:9200", "INGOT_OPENSEARCH_ALLOW_HTTP" to "true")
        OpenSearchSettings.read(environment, noFileExpected, log = messages::add)
        OpenSearchSettings.read(environment, noFileExpected, log = messages::add)

        // then: both starts warn
        assertThat(messages).`as`("warnings of two starts").hasSize(2)
    }

    @Test
    fun `should not warn when plain HTTP is not accepted`() {
        // when: the settings are read with HTTPS only
        OpenSearchSettings.read(
            mapOf("INGOT_OPENSEARCH_URLS" to "https://search.example.test:9200"),
            noFileExpected,
            log = messages::add,
        )

        // then: no warning is written
        assertThat(messages).`as`("warnings").isEmpty()
    }

    @Test
    fun `should keep the password and the user name out of the warning`() {
        // when: plain HTTP is accepted with credentials
        OpenSearchSettings.read(
            mapOf(
                "INGOT_OPENSEARCH_URLS" to "https://search.example.test:9200",
                "INGOT_OPENSEARCH_ALLOW_HTTP" to "true",
                "INGOT_OPENSEARCH_USERNAME" to "indexer-marker",
                "INGOT_OPENSEARCH_PASSWORD" to "s3cret-marker",
            ),
            noFileExpected,
            log = messages::add,
        )

        // then: the warning carries neither of them
        assertThat(messages.joinToString("\n"))
            .`as`("warning text")
            .doesNotContain("s3cret-marker", "indexer-marker", "search.example.test")
    }

}
