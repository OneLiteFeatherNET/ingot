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

package net.onelitefeather.ingot.search.application

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendFactory
import net.onelitefeather.ingot.search.backend.BackendResolution
import net.onelitefeather.ingot.search.backend.CoreSearchBackendContext
import net.onelitefeather.ingot.search.backend.FakeArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.SEARCH_BACKEND_API_VERSION
import net.onelitefeather.ingot.search.backend.SearchBackendContext
import net.onelitefeather.ingot.search.backend.SearchBackendHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** The status of a backend that the Maintenance block shows, with the properties the backend reports (task 5.3). */
internal class SearchBackendRuntimeTest {

    private val clock = MutableClock(Instant.parse("2026-11-13T10:00:00Z"))

    /** A backend that reports [properties] and otherwise behaves like the in-memory fake. */
    private fun backendReporting(properties: Map<String, String>): ArtifactSearchBackend =
        object : ArtifactSearchBackend by FakeArtifactSearchBackend() {
            override fun properties(): Map<String, String> = properties
        }

    /** A runtime whose holder has opened [backend], with the redaction of [context]. */
    private fun runtimeFor(backend: ArtifactSearchBackend, context: CoreSearchBackendContext): SearchBackendRuntime {
        val factory = object : ArtifactSearchBackendFactory {
            override val id: String = "fake"
            override val apiVersion: Int = SEARCH_BACKEND_API_VERSION
            override fun create(context: SearchBackendContext): ArtifactSearchBackend = backend
        }
        val holder = SearchBackendHolder(
            resolution = BackendResolution.Installed(factory),
            clock = clock,
            log = {},
            createBackend = { backend },
            redact = context::redact,
        )
        holder.tick()
        return SearchBackendRuntime(
            holder = holder,
            agreement = null,
            clock = clock,
            backendId = "fake",
            redact = context::redact,
            accessMismatches = { 0L },
        )
    }

    @Test
    fun `the status carries the properties the backend reports`() {
        val context = CoreSearchBackendContext(clock, "instance", "fake", { null }) { _, _ -> }
        val runtime = runtimeFor(backendReporting(mapOf("refreshInterval" to "1s")), context)

        assertThat(runtime.status().properties).`as`("properties of the backend").isEqualTo(mapOf("refreshInterval" to "1s"))
    }

    @Test
    fun `a secret handed out to the backend is replaced in a property value`() {
        val environment = mapOf("OPENSEARCH_PASSWORD" to "hunter2-secret")
        val context = CoreSearchBackendContext(clock, "instance", "fake", environment::get) { _, _ -> }
        context.secret("OPENSEARCH_PASSWORD")
        val runtime = runtimeFor(backendReporting(mapOf("cluster" to "answered with hunter2-secret")), context)

        assertThat(runtime.status().properties).`as`("properties with the secret").isEqualTo(mapOf("cluster" to "answered with ***"))
    }

    @Test
    fun `user information of a URL in a property value is removed`() {
        val context = CoreSearchBackendContext(clock, "instance", "fake", { null }) { _, _ -> }
        val runtime = runtimeFor(backendReporting(mapOf("endpoint" to "https://ingot:not-shown@search.test:9200")), context)

        assertThat(runtime.status().properties).`as`("properties with the URL").isEqualTo(mapOf("endpoint" to "https://***@search.test:9200"))
    }
}
