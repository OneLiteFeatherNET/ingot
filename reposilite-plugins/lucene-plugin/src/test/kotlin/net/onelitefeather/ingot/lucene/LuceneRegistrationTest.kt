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

package net.onelitefeather.ingot.lucene

import com.reposilite.audit.MutableClock
import com.reposilite.scheduling.ScheduledJobClaims
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendFactory
import net.onelitefeather.ingot.search.backend.BackendLogger
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SEARCH_BACKEND_API_VERSION
import net.onelitefeather.ingot.search.backend.SearchBackendContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock

/** The factory of the lucene backend and its registration with the search plugin. */
internal class LuceneRegistrationTest {

    @TempDir
    lateinit var root: Path

    @Test
    fun `the factory answers to the lucene id at the current backend api version`() {
        val factory = LuceneBackendFactory { LuceneSettings.read(emptyMap(), root.resolve("unused")) }

        assertThat(factory.id).`as`("id of the factory").isEqualTo("lucene")
        assertThat(factory.apiVersion).`as`("api version of the factory").isEqualTo(SEARCH_BACKEND_API_VERSION)
    }

    @Test
    fun `a created backend takes its lease from the lease store of its context`() {
        val clock = MutableClock()
        val leases = FakeLeases(LeaseTable(), "instance-a", clock)
        val factory = LuceneBackendFactory { LuceneSettings.read(mapOf("INGOT_LUCENE_DIRECTORY" to root.resolve("index").toString()), root) }

        val backend = factory.create(FakeContext(clock, leases))
        backend.open()
        try {
            assertThat(backend.indexIdentity()).`as`("identity of the holder created from the context").isNotNull()
        } finally {
            backend.close()
        }
    }

    @Test
    fun `a context without a lease store is refused, because one writer cannot be guaranteed without it`() {
        val factory = LuceneBackendFactory { LuceneSettings.read(emptyMap(), root.resolve("unused")) }

        assertThrows<IllegalStateException> { factory.create(FakeContext(MutableClock(), leases = null)) }
    }

    @Test
    fun `a refused setting fails the create call and names the variable`() {
        val factory = LuceneBackendFactory {
            LuceneSettings.read(mapOf("INGOT_LUCENE_MIN_FREE_BYTES" to "-1"), root.resolve("unused"))
        }

        val refused = assertThrows<IllegalArgumentException> { factory.create(FakeContext(MutableClock(), FakeLeases(LeaseTable(), "a", MutableClock()))) }

        assertThat(refused.message).`as`("message of the refused setting").contains("INGOT_LUCENE_MIN_FREE_BYTES")
    }

    @Test
    fun `registration hands the factory to the registry through accept`() {
        val accepted = mutableListOf<ArtifactSearchBackendFactory>()

        registerLucene(emptyMap(), root.resolve("unused")) { accepted += it }

        assertThat(accepted.map { it.id }).`as`("ids of the registered factories").containsExactly("lucene")
    }

    private class FakeContext(
        override val clock: Clock,
        override val leases: ScheduledJobClaims?,
    ) : SearchBackendContext {
        override val instanceId: String = "instance-a"
        override fun environment(name: String): String? = null
        override fun secret(name: String): String? = null
        override fun logger(): BackendLogger = RecordingLogger()
    }

}
