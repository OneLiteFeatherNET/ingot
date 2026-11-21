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

package net.onelitefeather.ingot.search.backend

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant

/**
 * The attribute storage every backend that declares [SearchCapability.ATTRIBUTES] shares (search document attributes, D4 and
 * D6). Each test opens a fresh, empty backend through [open]. The subclass also says how the stored attributes of one row are
 * read, because the search SPI has no read of them yet.
 */
abstract class AttributeStorageContract {

    @TempDir
    lateinit var directory: Path

    /** A backend, the reader of the attributes it stores for a row, and what closes it after its test. */
    protected class Opened(
        val backend: ArtifactSearchBackend,
        val attributes: (repository: String, path: String) -> Map<String, List<String>>,
        val close: () -> Unit,
    )

    /** Opens a fresh, empty backend that reads time from [clock]. */
    protected abstract fun open(directory: Path, clock: Clock): Opened

    private fun withBackend(block: (Opened) -> Unit) {
        val opened = open(directory, MutableClock(Instant.parse(NOW)))
        try {
            block(opened)
        } finally {
            opened.close()
        }
    }

    @Test
    fun `the backend declares that it stores attributes`() = withBackend { opened ->
        assertThat(opened.backend.capabilities)
            .`as`("capabilities of a backend that runs the attribute contract")
            .contains(SearchCapability.ATTRIBUTES)
    }

    @Test
    fun `an upsert with attributes stores them with their order and case`() = withBackend { opened ->
        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST)))

        assertThat(opened.attributes(REPOSITORY, JAR)).`as`("attributes after the upsert").isEqualTo(FIRST)
    }

    @Test
    fun `an upsert with other attributes replaces the stored ones`() = withBackend { opened ->
        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST)))

        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), SECOND)))

        assertThat(opened.attributes(REPOSITORY, JAR)).`as`("attributes after a second upsert").isEqualTo(SECOND)
    }

    @Test
    fun `an upsert with an empty attribute set removes the stored ones`() = withBackend { opened ->
        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST)))

        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), emptyMap())))

        assertThat(opened.attributes(REPOSITORY, JAR)).`as`("attributes after an upsert with an empty set").isEmpty()
    }

    @Test
    fun `an upsert without attributes keeps the stored ones`() = withBackend { opened ->
        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST)))

        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR))))

        assertThat(opened.attributes(REPOSITORY, JAR)).`as`("attributes after an upsert that carries none").isEqualTo(FIRST)
    }

    @Test
    fun `an upsert replayed twice leaves one set of attributes`() = withBackend { opened ->
        val upsert = IndexAction.Upsert(row(JAR), FIRST)

        opened.backend.apply(listOf(upsert))
        opened.backend.apply(listOf(upsert))

        assertThat(opened.attributes(REPOSITORY, JAR)).`as`("attributes after a replayed upsert").isEqualTo(FIRST)
    }

    @Test
    fun `the attributes of one row leave the other rows alone`() = withBackend { opened ->
        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST), IndexAction.Upsert(row(SIBLING), SECOND)))

        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), emptyMap())))

        assertThat(opened.attributes(REPOSITORY, SIBLING)).`as`("attributes of the other row").isEqualTo(SECOND)
    }

    @Test
    fun `a touch keeps the attributes`() = withBackend { opened ->
        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST)))

        opened.backend.touch(REPOSITORY, listOf(JAR), LATER)

        assertThat(opened.attributes(REPOSITORY, JAR)).`as`("attributes after a touch").isEqualTo(FIRST)
    }

    @Test
    fun `a reorigin keeps the attributes`() = withBackend { opened ->
        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST)))

        opened.backend.apply(listOf(IndexAction.Reorigin(REPOSITORY, DIRECTORY, Provenance(Origin.MIRROR, UPSTREAM))))

        assertThat(opened.attributes(REPOSITORY, JAR)).`as`("attributes after a reorigin").isEqualTo(FIRST)
    }

    @Test
    fun `removing a path removes its attributes`() = withBackend { opened ->
        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST)))

        opened.backend.apply(listOf(IndexAction.RemovePath(REPOSITORY, JAR)))

        assertThat(attributesAfterReturn(opened)).`as`("attributes of a removed path that returns").isEmpty()
    }

    @Test
    fun `a prefix removal removes the attributes of the rows it removes`() = withBackend { opened ->
        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST)))

        opened.backend.apply(listOf(IndexAction.RemovePrefix(REPOSITORY, DIRECTORY, notAfter = Instant.ofEpochMilli(LATER))))

        assertThat(attributesAfterReturn(opened)).`as`("attributes of a prefix-removed path that returns").isEmpty()
    }

    @Test
    fun `a stale deletion removes the attributes of the rows it deletes`() = withBackend { opened ->
        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST)))

        opened.backend.deleteStale(REPOSITORY, before = LATER, limit = 10)

        assertThat(attributesAfterReturn(opened)).`as`("attributes of a stale-deleted path that returns").isEmpty()
    }

    @Test
    fun `a repository deletion removes the attributes of the rows it deletes`() = withBackend { opened ->
        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR), FIRST)))

        opened.backend.deleteRepository(REPOSITORY, limit = 10)

        assertThat(attributesAfterReturn(opened)).`as`("attributes of a path of a deleted repository that returns").isEmpty()
    }

    /** Writes the row of [JAR] again without attributes, so that only attributes that survived a deletion could show. */
    private fun attributesAfterReturn(opened: Opened): Map<String, List<String>> {
        opened.backend.apply(listOf(IndexAction.Upsert(row(JAR))))
        return opened.attributes(REPOSITORY, JAR)
    }

    private fun row(path: String): IndexRow {
        val parsed = ArtifactPathParser.parse(REPOSITORY, path) as ArtifactPathResult.Row
        return IndexRow(
            file = parsed.file,
            provenance = Provenance.HOSTED,
            sizeBytes = 10L,
            lastModified = NOW_MILLIS,
            sha1 = null,
            sha256 = null,
            indexedAt = NOW_MILLIS,
        )
    }

    private companion object {
        const val NOW = "2026-10-17T10:00:00Z"
        val NOW_MILLIS: Long = Instant.parse(NOW).toEpochMilli()
        val LATER: Long = Instant.parse("2026-10-17T12:00:00Z").toEpochMilli()
        const val REPOSITORY = "releases"
        const val DIRECTORY = "com/example/app/1.0"
        const val JAR = "com/example/app/1.0/app-1.0.jar"
        const val SIBLING = "com/example/app/1.0/app-1.0.pom"
        const val UPSTREAM = "upstream.example"
        val FIRST = mapOf(
            "portal.platforms" to listOf("Paper", "Folia", "Paper"),
            "core.enricher.portal" to listOf("1.2.0"),
        )
        val SECOND = mapOf("portal.platforms" to listOf("Velocity"))
    }

}
