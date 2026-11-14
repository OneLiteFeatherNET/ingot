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

package net.onelitefeather.ingot.meilisearch

import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendFactory
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SEARCH_BACKEND_API_VERSION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** What the plugin registers with the search plugin. The jar in the plugins directory is the switch, so one factory is always registered. */
internal class MeilisearchRegistrationTest {

    private val noFileExpected: (String) -> String = { path -> error("the key file $path must not be read at registration") }

    private fun registered(environment: Map<String, String>): List<ArtifactSearchBackendFactory> {
        val factories = mutableListOf<ArtifactSearchBackendFactory>()
        registerMeilisearch(environment, noFileExpected) { factory -> factories.add(factory) }
        return factories
    }

    @Test
    fun `should register one factory with the meilisearch id and api version 1 without any configuration`() {
        // when: the plugin registers with an empty environment
        val factories = registered(emptyMap())

        // then: exactly one factory of the current api version is registered
        assertThat(factories).`as`("factories registered").hasSize(1)
        assertThat(factories.single().id).`as`("id of the registered factory").isEqualTo("meilisearch")
        assertThat(factories.single().apiVersion).`as`("api version of the registered factory").isEqualTo(SEARCH_BACKEND_API_VERSION)
    }

    @Test
    fun `should not read the key file when the factory is registered`() {
        // when: a key file is configured, and registration must not read it (the reader fails the test if it is called)
        val factories = registered(mapOf("INGOT_MEILISEARCH_API_KEY_FILE" to "/run/secrets/meili"))

        // then: the factory is registered and nothing was read
        assertThat(factories).`as`("factories registered").hasSize(1)
    }

}
