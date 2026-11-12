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

package net.onelitefeather.ingot.search.backend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The registration rules of the backend registry (search backend registration, D2 and D4), without any database. */
internal class ArtifactSearchBackendRegistryTest {

    private val warnings = mutableListOf<String>()

    private val builtIn = FakeFactory(id = DATABASE_ID)

    private fun registry() = ArtifactSearchBackendRegistry(builtIn = builtIn, warn = warnings::add)

    @Test
    fun `a factory of the current api version registers and resolves to its id`() {
        val registry = registry()

        val accepted = registry.register(FakeFactory(id = "opensearch"))

        assertThat(accepted).`as`("registration of a factory of the current api version").isTrue()
        assertThat(registry.resolve("opensearch")).`as`("resolution of the registered id")
            .isInstanceOf(BackendResolution.Installed::class.java)
        assertThat(warnings).`as`("warnings of an accepted factory").isEmpty()
    }

    @Test
    fun `a factory of another api version is refused and the warning names its class and both versions`() {
        val registry = registry()
        val otherVersion = SEARCH_BACKEND_API_VERSION + 1

        val accepted = registry.register(FakeFactory(id = "opensearch", apiVersion = otherVersion))

        assertThat(accepted).`as`("registration of a factory of another api version").isFalse()
        assertThat(warnings).`as`("warnings of the refused factory").hasSize(1)
        assertThat(warnings.single()).`as`("warning text")
            .contains(FakeFactory::class.java.name)
            .contains("apiVersion=$otherVersion")
            .contains("expected $SEARCH_BACKEND_API_VERSION")
    }

    @Test
    fun `a refused factory leaves its id refused rather than not installed`() {
        val registry = registry()
        registry.register(FakeFactory(id = "opensearch", apiVersion = SEARCH_BACKEND_API_VERSION + 1))

        val resolution = registry.resolve("opensearch")

        assertThat(resolution).`as`("resolution of an id whose only factory was refused")
            .isEqualTo(BackendResolution.Unavailable(SearchBackendState.REFUSED, reason(resolution)))
    }

    @Test
    fun `a second factory with an existing id is refused and the warning names both classes`() {
        val registry = registry()
        registry.register(FakeFactory(id = "opensearch"))

        val accepted = registry.register(AnotherFakeFactory(id = "opensearch"))

        assertThat(accepted).`as`("registration of a duplicate id").isFalse()
        assertThat(warnings.single()).`as`("warning text")
            .contains(FakeFactory::class.java.name)
            .contains(AnotherFakeFactory::class.java.name)
    }

    @Test
    fun `the first registered factory of an id wins a duplicate`() {
        val registry = registry()
        val first = FakeFactory(id = "opensearch")
        registry.register(first)

        registry.register(AnotherFakeFactory(id = "opensearch"))

        assertThat(registry.resolve("opensearch")).`as`("resolution after a refused duplicate")
            .isEqualTo(BackendResolution.Installed(first))
    }

    @Test
    fun `a plugin cannot replace the database backend`() {
        val registry = registry()

        val accepted = registry.register(AnotherFakeFactory(id = DATABASE_ID))

        assertThat(accepted).`as`("registration of a factory with the id database").isFalse()
        assertThat(registry.resolve(DATABASE_ID)).`as`("resolution of database after the refusal")
            .isEqualTo(BackendResolution.Installed(builtIn))
    }

    @Test
    fun `an id no factory has registered resolves to not installed`() {
        val registry = registry()

        val resolution = registry.resolve("opensearch")

        assertThat(resolution).`as`("resolution of an unknown id")
            .isEqualTo(BackendResolution.Unavailable(SearchBackendState.NOT_INSTALLED, reason(resolution)))
    }

    @Test
    fun `the registration event registers the factory in the registry`() {
        val registry = registry()
        val event = ArtifactSearchBackendRegistrationEvent(registry)
        val factory = FakeFactory(id = "opensearch")

        event.register(factory)

        assertThat(registry.resolve("opensearch")).`as`("resolution after registration through the event")
            .isEqualTo(BackendResolution.Installed(factory))
    }

    private fun reason(resolution: BackendResolution): String =
        (resolution as? BackendResolution.Unavailable)?.reason
            ?: throw AssertionError("expected an unavailable resolution but was $resolution")

    private open class FakeFactory(
        override val id: String,
        override val apiVersion: Int = SEARCH_BACKEND_API_VERSION,
    ) : ArtifactSearchBackendFactory {
        override fun create(context: SearchBackendContext): ArtifactSearchBackend =
            throw UnsupportedOperationException("the registry test does not open backends")
    }

    private class AnotherFakeFactory(id: String) : FakeFactory(id)

    private companion object {
        const val DATABASE_ID = "database"
    }

}
