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

import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** The factory reads its configuration when a backend is created, and creating a backend sends no request (the core opens it). */
internal class MeilisearchBackendFactoryTest {

    private val noFileExpected: (String) -> String = { path -> error("the key file $path must not be read") }

    private fun factoryFor(context: FakeSearchContext, exchange: FakeMeilisearch): MeilisearchBackendFactory =
        MeilisearchBackendFactory(exchange = exchange) { log -> MeilisearchSettings.read(context.environment, noFileExpected, log) }

    @Test
    fun `should create a backend without sending any request`() {
        // given: a valid configuration and a server the test can inspect
        val context = FakeSearchContext(mapOf("INGOT_MEILISEARCH_URL" to "https://search.example.test"))
        val exchange = FakeMeilisearch(ManualClock())

        // when: the core creates the backend
        val backend = factoryFor(context, exchange).create(context)

        // then: a backend is returned, and the server was not contacted before the core opens it
        assertThat(backend.id).`as`("id of the created backend").isEqualTo("meilisearch")
        assertThat(exchange.calls).`as`("requests sent on create").isEmpty()
    }

    @Test
    fun `should report a refused configuration with its variable name`() {
        // given: no URL at all
        val context = FakeSearchContext(emptyMap())

        // when / then: the configuration error is the reason
        assertThatThrownBy { factoryFor(context, FakeMeilisearch(ManualClock())).create(context) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_URL")
    }

    @Test
    fun `should log the plain HTTP warning when the backend is created`() {
        // given: plain HTTP is accepted
        val context = FakeSearchContext(
            mapOf("INGOT_MEILISEARCH_URL" to "http://search.example.test", "INGOT_MEILISEARCH_ALLOW_HTTP" to "true"),
        )

        // when: the backend is created
        factoryFor(context, FakeMeilisearch(ManualClock())).create(context)

        // then: the warning of the read reached the log of the context
        assertThat(context.warnings).`as`("warnings of the context").containsExactly(MeilisearchSettings.PLAIN_HTTP_WARNING)
    }

}
