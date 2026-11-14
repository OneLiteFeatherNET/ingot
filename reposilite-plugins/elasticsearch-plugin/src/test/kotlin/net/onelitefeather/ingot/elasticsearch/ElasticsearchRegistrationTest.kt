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

package net.onelitefeather.ingot.elasticsearch

import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendFactory
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SEARCH_BACKEND_API_VERSION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** What the plugin registers with the search plugin. The jar is its own switch, so the registration reads no variable. */
internal class ElasticsearchRegistrationTest {

    private fun registered(environment: Map<String, String>): List<ArtifactSearchBackendFactory> {
        val factories = mutableListOf<ArtifactSearchBackendFactory>()
        registerElasticsearch(environment) { factory -> factories.add(factory) }
        return factories
    }

    @Test
    fun `should register one factory with the elasticsearch id and api version 1 when the jar is installed`() {
        // when: the plugin registers with no variable set at all
        val factories = registered(emptyMap())

        // then: exactly one factory of the current api version is registered
        assertThat(factories).`as`("factories registered by an installed jar").hasSize(1)
        assertThat(factories.single().id).`as`("id of the registered factory").isEqualTo("elasticsearch")
        assertThat(factories.single().apiVersion).`as`("api version of the registered factory").isEqualTo(SEARCH_BACKEND_API_VERSION)
    }

    @Test
    fun `should register the factory even when its settings are incomplete so the refusal shows as a failed open`() {
        // when: the URL list is missing
        val factories = registered(mapOf("INGOT_ELASTICSEARCH_API_KEY" to "QXBpS2V5SWQ6c2VjcmV0"))

        // then: the factory is registered and the refusal is raised when the backend is created, not at registration
        assertThat(factories).`as`("factories registered with incomplete settings").hasSize(1)
    }
}
