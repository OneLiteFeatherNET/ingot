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

/** The factory reads its configuration when a backend is created, and refuses to serve until the backend is built. */
internal class MeilisearchBackendFactoryTest {

    private val noFileExpected: (String) -> String = { path -> error("the key file $path must not be read") }

    private fun factoryFor(context: FakeSearchContext): MeilisearchBackendFactory =
        MeilisearchBackendFactory { log -> MeilisearchSettings.read(context.environment, noFileExpected, log) }

    @Test
    fun `should refuse to create a backend while the backend is not available in this build`() {
        // given: a valid configuration
        val context = FakeSearchContext(mapOf("INGOT_MEILISEARCH_URL" to "https://search.example.test"))

        // when / then: the create call refuses, so the core reports the backend as OPEN_FAILED with this reason
        assertThatThrownBy { factoryFor(context).create(context) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("the meilisearch backend is not available in this build yet")
    }

    @Test
    fun `should report a refused configuration with its variable name before the unavailability`() {
        // given: no URL at all
        val context = FakeSearchContext(emptyMap())

        // when / then: the configuration error is the reason
        assertThatThrownBy { factoryFor(context).create(context) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_URL")
    }

    @Test
    fun `should log the plain HTTP warning when the backend is created`() {
        // given: plain HTTP is accepted
        val context = FakeSearchContext(
            mapOf("INGOT_MEILISEARCH_URL" to "http://search.example.test", "INGOT_MEILISEARCH_ALLOW_HTTP" to "true"),
        )

        // when: the create call runs and then refuses
        runCatching { factoryFor(context).create(context) }

        // then: the warning of the read reached the log of the context
        assertThat(context.warnings).`as`("warnings of the context").containsExactly(MeilisearchSettings.PLAIN_HTTP_WARNING)
    }

}
