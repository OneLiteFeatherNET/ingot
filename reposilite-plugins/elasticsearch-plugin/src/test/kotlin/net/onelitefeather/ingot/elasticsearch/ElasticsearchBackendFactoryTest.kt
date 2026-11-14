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

import net.onelitefeather.ingot.search.backend.BackendLogger
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SearchBackendContext
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** What the factory does when the core creates its backend: a refused setting stops the create and names the variable. */
internal class ElasticsearchBackendFactoryTest {

    @Test
    fun `should refuse to create the backend when the URL list is missing and name the variable`() {
        // given: a factory whose settings come from an environment without any URL
        val factory = ElasticsearchBackendFactory(settings = { log -> ElasticsearchSettings.read(emptyMap(), log = log) })

        // when / then: the create is refused and the message names the variable
        assertThatThrownBy { factory.create(context()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_ELASTICSEARCH_URLS")
    }

    private fun context(): SearchBackendContext = object : SearchBackendContext {
        override val clock: Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        override val instanceId: String = "replica-test"
        override fun environment(name: String): String? = null
        override fun secret(name: String): String? = null
        override fun logger(): BackendLogger = object : BackendLogger {
            override fun info(message: String) = Unit
            override fun warn(message: String) = Unit
            override fun error(message: String) = Unit
        }
    }
}
