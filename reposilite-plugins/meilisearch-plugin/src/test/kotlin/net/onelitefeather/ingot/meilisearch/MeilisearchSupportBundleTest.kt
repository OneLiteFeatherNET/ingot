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

import net.onelitefeather.ingot.support.Redaction
import net.onelitefeather.ingot.support.RedactionTally
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The support bundle must not carry the key or the credentials of the Meilisearch server. The environment of the bundle keeps the
 * values of allowlisted names only, so the key and the URL of this plugin are withheld, and the withheld values are scrubbed from the
 * log lines.
 */
internal class MeilisearchSupportBundleTest {

    private val key = "bundle-key-7f3a9c"
    private val url = "https://bundle-user:bundle-pass@search.example.test"

    private val environment = mapOf(
        "INGOT_MEILISEARCH_URL" to url,
        "INGOT_MEILISEARCH_API_KEY" to key,
        "INGOT_MEILISEARCH_API_KEY_FILE" to "/run/secrets/meili",
        "INGOT_MEILISEARCH_INDEX" to "ingot-artifacts",
    )

    @Test
    fun `should withhold the key and the URL of the server from the environment of the bundle`() {
        // when: the environment is redacted for the bundle
        val redacted = Redaction.environment(environment, RedactionTally())

        // then: neither the key nor the credentials of the URL appear in any value
        assertThat(redacted.values.joinToString())
            .`as`("environment values of the bundle")
            .doesNotContain(key)
            .doesNotContain("bundle-pass")
            .doesNotContain("search.example.test")
    }

    @Test
    fun `should scrub the key and the URL from the log lines of the bundle`() {
        // when: the values the bundle withholds are collected
        val withheld = Redaction.withheldValues(environment, emptyMap(), emptyList())

        // then: the key is among them, so the log scrubber removes it from every line
        assertThat(withheld).`as`("values withheld from the bundle").contains(key, url)
    }

}
