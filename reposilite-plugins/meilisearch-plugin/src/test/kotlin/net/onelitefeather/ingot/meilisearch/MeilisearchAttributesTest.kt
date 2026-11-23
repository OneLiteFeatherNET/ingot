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

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.backend.SearchCapability
import net.onelitefeather.ingot.search.database.pathHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * The attributes of a row at the Meilisearch backend (search document attributes, D3, D4 and D6): the document carries them as one
 * object, an upsert without attributes keeps the stored ones, and the stored row reports the enricher versions. Against the
 * in-process fake server; no sleeps, no network.
 */
internal class MeilisearchAttributesTest {

    private val environment = mapOf(
        "INGOT_MEILISEARCH_URL" to "https://search.example.test",
        "INGOT_MEILISEARCH_API_KEY" to "test-key",
    )

    private fun backendWith(answers: FakeMeilisearch.() -> Unit): Pair<MeilisearchSearchBackend, FakeMeilisearch> {
        val clock = ManualClock()
        val server = FakeMeilisearch(clock).apply(answers)
        val backend = MeilisearchSearchBackend(
            MeilisearchSettings.read(environment, { error("no file expected") }),
            clock,
            server,
            ManualWaiter(clock, Duration.ZERO),
        )
        return backend to server
    }

    private fun FakeMeilisearch.acceptingWrites() {
        onDocuments(taskUid = 1)
        onTask(1, status = "succeeded")
    }

    private fun row(path: String): IndexRow = IndexRow(
        file = ArtifactFileRecord("releases", path, "com.example", "app", "1.0", null, "jar", false),
        provenance = Provenance.HOSTED,
        sizeBytes = 10,
        lastModified = 1_000,
        sha1 = null,
        sha256 = null,
        indexedAt = 2_000,
    )

    /** The documents of every document write the server received, in order. */
    private fun FakeMeilisearch.writtenDocuments(): List<JsonNode> = calls
        .filter { it.method == "POST" && it.uri.path.endsWith("/documents") }
        .flatMap { ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(it.body.orEmpty()).toList() }

    private fun JsonNode.attributes(): Map<String, List<String>>? = path("attributes").takeUnless { it.isMissingNode }
        ?.let { node -> node.properties().associate { (key, values) -> key to values.map { it.asText() } } }

    @Test
    fun `should declare that it stores attributes`() {
        val (backend, _) = backendWith {}

        assertThat(backend.capabilities).`as`("capabilities of the meilisearch backend").contains(SearchCapability.ATTRIBUTES)
    }

    @Test
    fun `should write the attributes of an upsert as one object with their order and case`() {
        val (backend, server) = backendWith { acceptingWrites() }

        backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST)))

        assertThat(server.writtenDocuments().single().attributes()).`as`("attributes of the written document").isEqualTo(FIRST)
    }

    @Test
    fun `should write no attributes object for an empty attribute set`() {
        val (backend, server) = backendWith {
            acceptingWrites()
            storedAttributes[pathHash("releases", JAR)] = FIRST
        }

        backend.apply(listOf(IndexAction.Upsert(row(JAR), emptyMap())))

        assertThat(server.writtenDocuments().single().attributes()).`as`("attributes of a document written with an empty set").isNull()
    }

    @Test
    fun `should carry the stored attributes into the document of an upsert without attributes`() {
        val (backend, server) = backendWith {
            acceptingWrites()
            storedAttributes[pathHash("releases", JAR)] = FIRST
        }

        backend.apply(listOf(IndexAction.Upsert(row(JAR))))

        assertThat(server.writtenDocuments().single().attributes())
            .`as`("attributes of a document that replaces a stored one")
            .isEqualTo(FIRST)
    }

    @Test
    fun `should write no attributes object for an upsert without attributes of a new row`() {
        val (backend, server) = backendWith { acceptingWrites() }

        backend.apply(listOf(IndexAction.Upsert(row(JAR))))

        assertThat(server.writtenDocuments().single().attributes()).`as`("attributes of a new document written without a set").isNull()
    }

    @Test
    fun `should keep the attributes of an earlier upsert of the same batch`() {
        val (backend, server) = backendWith {
            acceptingWrites()
            storedAttributes[pathHash("releases", JAR)] = SECOND
        }

        backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST), IndexAction.Upsert(row(JAR))))

        assertThat(server.writtenDocuments().map { it.attributes() })
            .`as`("attributes of both documents of the batch")
            .containsExactly(FIRST, FIRST)
    }

    @Test
    fun `should read the attributes again after a prefix removal of the same batch`() {
        val (backend, server) = backendWith {
            acceptingWrites()
            on("POST", "/indexes/ingot-artifacts/documents/delete", status = 202, body = """{"taskUid":1}""")
        }

        backend.apply(
            listOf(
                IndexAction.Upsert(row(JAR), FIRST),
                IndexAction.RemovePrefix("releases", "com/example/app/1.0", notAfter = Instant.ofEpochMilli(3_000)),
                IndexAction.Upsert(row(JAR)),
            ),
        )

        assertThat(server.writtenDocuments().map { it.attributes() })
            .`as`("attributes of the upsert before and of the upsert after the prefix removal")
            .containsExactly(FIRST, null)
    }

    @Test
    fun `should read the stored attributes of a batch in one request`() {
        val (backend, server) = backendWith { acceptingWrites() }

        backend.apply(listOf(IndexAction.Upsert(row(JAR)), IndexAction.Upsert(row(POM))))

        assertThat(server.calls.count { it.uri.path.endsWith("/documents/fetch") })
            .`as`("reads of the stored attributes of a batch of two upserts")
            .isEqualTo(1)
    }

    @Test
    fun `should report the version of each enricher stored with a row`() {
        val (backend, _) = backendWith { storedAttributes[pathHash("releases", JAR)] = FIRST }

        val stored = backend.existing("releases", listOf(JAR)).getValue(JAR)

        assertThat(stored.enricherVersions)
            .`as`("enricher versions of a row stored with a core.enricher key")
            .isEqualTo(mapOf("portal" to "1.2.0"))
    }

    @Test
    fun `should report no enricher version for a row stored without one`() {
        val (backend, _) = backendWith { storedAttributes[pathHash("releases", JAR)] = SECOND }

        val stored = backend.existing("releases", listOf(JAR)).getValue(JAR)

        assertThat(stored.enricherVersions).`as`("enricher versions of a row stored without a core.enricher key").isEmpty()
    }

    private companion object {
        const val JAR = "com/example/app/1.0/app-1.0.jar"
        const val POM = "com/example/app/1.0/app-1.0.pom"
        val FIRST = mapOf(
            "portal.platforms" to listOf("Paper", "Folia", "Paper"),
            "core.enricher.portal" to listOf("1.2.0"),
        )
        val SECOND = mapOf("portal.platforms" to listOf("Velocity"))
    }

}
