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

import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.BackendHealth
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/** The free-space floor of the index volume: a batch waits below it, and the health says why. The free space is injected. */
internal class LuceneFreeSpaceTest {

    @TempDir
    lateinit var root: Path

    private var free: Long = FLOOR * 2
    private val opened = mutableListOf<LuceneSearchBackend>()

    @AfterEach
    fun closeBackends() {
        opened.forEach { it.close() }
    }

    private fun open(): LuceneSearchBackend {
        val settings = LuceneSettings.read(
            mapOf(
                "INGOT_LUCENE_DIRECTORY" to root.resolve("index").toString(),
                "INGOT_LUCENE_MIN_FREE_BYTES" to FLOOR.toString(),
            ),
            root.resolve("unused"),
        )
        return LuceneSearchBackend(settings, usableSpace = { free }).also {
            opened += it
            it.open()
        }
    }

    @Test
    fun `a batch is busy for sixty seconds while the free space is below the floor and leaves no row`() {
        val backend = open()
        free = FLOOR - 1

        val busy = assertThrows<BackendBusyException> { backend.apply(listOf(IndexAction.Upsert(row()))) }

        assertThat(busy.retryAfter).`as`("retry delay below the floor").isEqualTo(Duration.ofSeconds(60))
        assertThat(backend.count()).`as`("documents written below the floor").isEqualTo(0L)
    }

    @Test
    fun `a batch is applied once the free space is at or above the floor again`() {
        val backend = open()
        free = FLOOR - 1
        assertThrows<BackendBusyException> { backend.apply(listOf(IndexAction.Upsert(row()))) }

        free = FLOOR
        backend.apply(listOf(IndexAction.Upsert(row())))

        assertThat(backend.count()).`as`("documents after the space came back").isEqualTo(1L)
    }

    @Test
    fun `health is down with the reason while the free space is below the floor`() {
        val backend = open()
        free = FLOOR - 1

        assertThat(backend.health())
            .`as`("health below the floor")
            .isEqualTo(BackendHealth.Down("low disk space"))
    }

    @Test
    fun `health is up while the free space is above the floor`() {
        val backend = open()

        assertThat(backend.health()).`as`("health above the floor").isEqualTo(BackendHealth.Up)
    }

    private fun row(): IndexRow {
        val parsed = ArtifactPathParser.parse(REPOSITORY, JAR) as ArtifactPathResult.Row
        return IndexRow(
            file = parsed.file,
            provenance = Provenance.HOSTED,
            sizeBytes = 10L,
            lastModified = NOW,
            sha1 = null,
            sha256 = null,
            indexedAt = NOW,
        )
    }

    private companion object {
        const val FLOOR: Long = 1024L
        const val REPOSITORY = "releases"
        const val JAR = "com/example/app/1.0/app-1.0.jar"
        val NOW: Long = Instant.parse("2026-10-17T10:00:00Z").toEpochMilli()
    }

}
