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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.opensearch

import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendFactory
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SEARCH_BACKEND_API_VERSION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** What the plugin registers with the search plugin, decided from the environment only. */
internal class OpenSearchRegistrationTest {

    private val cluster = "https://search.example.test:9200"

    private val noFileExpected: (String) -> String = { path -> error("the password file $path must not be read") }

    private fun registered(environment: Map<String, String>): List<ArtifactSearchBackendFactory> {
        val factories = mutableListOf<ArtifactSearchBackendFactory>()
        registerOpenSearch(environment, noFileExpected) { factory -> factories.add(factory) }
        return factories
    }

    @Test
    fun `should register nothing when the plugin switch is not set`() {
        // when: the cluster is configured but the switch is absent
        val factories = registered(mapOf("INGOT_OPENSEARCH_URLS" to cluster))

        // then: a disabled plugin registers no factory
        assertThat(factories).`as`("factories registered while the switch is unset").isEmpty()
    }

    @Test
    fun `should register nothing when the INGOT switch is false even if the legacy switch is true`() {
        // when: the INGOT switch is set to false and the legacy switch to true
        val factories = registered(
            mapOf(
                "INGOT_OPENSEARCH_URLS" to cluster,
                "INGOT_OPENSEARCH_ENABLED" to "false",
                "REPOSILITE_OPENSEARCH_ENABLED" to "true",
            )
        )

        // then: the set INGOT name decides, and nothing is registered
        assertThat(factories).`as`("factories registered while the INGOT switch is false").isEmpty()
    }

    @Test
    fun `should register one factory with the opensearch id and api version 1 when the switch is on`() {
        // when: the switch is on
        val factories = registered(mapOf("INGOT_OPENSEARCH_ENABLED" to "true", "INGOT_OPENSEARCH_URLS" to cluster))

        // then: exactly one factory of the current api version is registered
        assertThat(factories).`as`("factories registered with the switch on").hasSize(1)
        assertThat(factories.single().id).`as`("id of the registered factory").isEqualTo("opensearch")
        assertThat(factories.single().apiVersion).`as`("api version of the registered factory").isEqualTo(SEARCH_BACKEND_API_VERSION)
    }

}
