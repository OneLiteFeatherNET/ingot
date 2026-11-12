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

@file:OptIn(net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.opensearch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The properties that the status block of the backend shows (task 5.3). They never hold a URL, a user name or a password. */
internal class OpenSearchPropertiesTest {

    private val environment = mapOf(
        "INGOT_OPENSEARCH_URLS" to "https://search.test:9200",
        "INGOT_OPENSEARCH_USERNAME" to "ingot-service",
        "INGOT_OPENSEARCH_PASSWORD" to "not-in-properties",
    )

    private fun backend() = OpenSearchSearchBackend(
        OpenSearchSettings.read(environment),
        ManualClock(),
        sslContext = null,
        exchange = FakeExchange(),
    )

    @Test
    fun `the properties report the refresh interval of the index`() {
        assertThat(backend().properties()).`as`("properties").containsEntry("refreshInterval", "1s")
    }

    @Test
    fun `no property holds the URL, the user name or the password`() {
        val values = backend().properties().values.joinToString(separator = " ")

        assertThat(values).`as`("property values").doesNotContain("search.test", "ingot-service", "not-in-properties")
    }
}
