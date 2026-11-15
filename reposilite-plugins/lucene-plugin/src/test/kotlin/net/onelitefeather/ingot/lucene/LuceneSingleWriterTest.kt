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
import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.SearchTokens
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.BackendHealth
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.backend.SearchUnavailableException
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.store.FSDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** The single-writer lease: one replica writes, the others report the reason and write nothing. Every replica has its own directory. */
internal class LuceneSingleWriterTest {

    @TempDir
    lateinit var root: Path

    private val clock = MutableClock()
    private val table = LeaseTable()
    private val opened = mutableListOf<LuceneSearchBackend>()

    @AfterEach
    fun closeBackends() {
        opened.forEach { it.close() }
    }

    private fun replica(name: String, log: RecordingLogger = RecordingLogger()): LuceneSearchBackend {
        val settings = LuceneSettings.read(
            mapOf("INGOT_LUCENE_DIRECTORY" to root.resolve(name).toString()),
            root.resolve("unused"),
        )
        return LuceneSearchBackend(
            settings = settings,
            leases = FakeLeases(table, owner = name, clock = clock),
            clock = clock,
            log = log,
            usableSpace = { Long.MAX_VALUE },
        ).also { opened += it }
    }

    @Test
    fun `a second replica that opens while another holds the lease writes nothing and reports the reason`() {
        replica("a").apply { open() }
        val second = replica("b").apply { open() }

        assertThat(Files.exists(root.resolve("b"))).`as`("directory of the replica without the lease").isFalse()
        assertThat(second.health()).`as`("health without the lease").isEqualTo(BackendHealth.Down(HELD_ELSEWHERE))
        assertThrows<BackendBusyException> { second.apply(listOf(IndexAction.Upsert(row()))) }
        assertThrows<SearchUnavailableException> { second.search(query(), ALL) }
    }

    @Test
    fun `a second replica reports the reason at its next acquisition attempt and not before`() {
        replica("a").apply { open() }
        val log = RecordingLogger()
        val second = replica("b", log).apply { open() }
        val afterOpen = log.lines.count { it.contains(HELD_ELSEWHERE) }

        clock.advance(Duration.ofSeconds(9))
        second.health()
        assertThat(log.lines.count { it.contains(HELD_ELSEWHERE) })
            .`as`("reports before the retry interval has passed")
            .isEqualTo(afterOpen)

        clock.advance(Duration.ofSeconds(1))
        second.health()
        assertThat(log.lines.count { it.contains(HELD_ELSEWHERE) })
            .`as`("reports at the next acquisition attempt")
            .isEqualTo(afterOpen + 1)
    }

    @Test
    fun `a holder that renews on its health tick keeps the others out`() {
        val first = replica("a").apply { open() }
        clock.advance(Duration.ofSeconds(30))
        first.health()
        clock.advance(Duration.ofSeconds(40))

        val second = replica("b").apply { open() }

        assertThat(second.health()).`as`("health of the replica refused after a renewal").isEqualTo(BackendHealth.Down(HELD_ELSEWHERE))
    }

    @Test
    fun `a holder that lost its lease rolls its batch back and writes nothing more`() {
        val first = replica("a").apply { open() }
        first.apply(listOf(IndexAction.Upsert(row(JAR))))
        clock.advance(Duration.ofSeconds(61))
        replica("b").apply { open() }

        assertThrows<BackendBusyException> { first.apply(listOf(IndexAction.Upsert(row(SOURCES)))) }
        assertThrows<BackendBusyException> { first.apply(listOf(IndexAction.Upsert(row(SOURCES)))) }

        assertThat(storedPaths(root.resolve("a")))
            .`as`("paths of the replica that lost its lease")
            .containsExactly(JAR)
    }

    private fun storedPaths(directory: Path): List<String> =
        DirectoryReader.open(FSDirectory.open(directory)).use { reader ->
            (0 until reader.maxDoc()).map { reader.storedFields().document(it).get(LuceneDocument.PATH) }
        }

    private fun row(path: String = JAR): IndexRow {
        val parsed = ArtifactPathParser.parse(REPOSITORY, path) as ArtifactPathResult.Row
        return IndexRow(parsed.file, Provenance.HOSTED, 10L, NOW, null, null, NOW)
    }

    private fun query() = net.onelitefeather.ingot.search.ArtifactSearchQuery(q = "app")

    private companion object {
        const val HELD_ELSEWHERE = "another replica holds the Lucene writer lease"
        const val REPOSITORY = "releases"
        const val JAR = "com/example/app/1.0/app-1.0.jar"
        const val SOURCES = "com/example/app/1.0/app-1.0-sources.jar"
        val NOW: Long = 0L
        val ALL = net.onelitefeather.ingot.search.access.SearchAccess(
            listOf(net.onelitefeather.ingot.search.access.RepositoryAccess(REPOSITORY, net.onelitefeather.ingot.search.access.PathPredicate.All)),
        )
    }

}
