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
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import org.apache.lucene.index.SegmentInfos
import org.apache.lucene.store.FSDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

/** A takeover of the lease: the new holder starts from an index it can not vouch for, so the index is cleared and its identity changes. */
internal class LuceneTakeoverTest {

    @TempDir
    lateinit var root: Path

    private val clock = MutableClock()
    private val table = LeaseTable()
    private val opened = mutableListOf<LuceneSearchBackend>()

    @AfterEach
    fun closeBackends() {
        opened.forEach { it.close() }
    }

    private fun replica(name: String): LuceneSearchBackend {
        val settings = LuceneSettings.read(mapOf("INGOT_LUCENE_DIRECTORY" to root.resolve(name).toString()), root.resolve("unused"))
        return LuceneSearchBackend(
            settings = settings,
            leases = FakeLeases(table, owner = name, clock = clock),
            clock = clock,
            log = RecordingLogger(),
            usableSpace = { Long.MAX_VALUE },
        ).also { opened += it }
    }

    @Test
    fun `a new holder whose index was written under an older epoch starts a rebuild with a new identity and no documents`() {
        val earlier = replica("b").apply { open(); apply(listOf(IndexAction.Upsert(row(JAR)))) }
        val identityBefore = earlier.indexIdentity()
        earlier.close()
        clock.advance(Duration.ofSeconds(61))
        replica("a").apply { open() }
        clock.advance(Duration.ofSeconds(61))

        val takeover = replica("b").apply { open() }

        assertThat(takeover.indexIdentity())
            .`as`("identity after a takeover at a newer epoch")
            .isNotEqualTo(identityBefore)
        assertThat(takeover.count()).`as`("documents after a takeover, before the rebuild").isEqualTo(0L)
    }

    @Test
    fun `every commit records the epoch of the holder that made it`() {
        val holder = replica("a").apply { open() }

        holder.apply(listOf(IndexAction.Upsert(row(JAR))))

        val userData = SegmentInfos.readLatestCommit(FSDirectory.open(root.resolve("a"))).userData
        assertThat(userData[LuceneSearchBackend.EPOCH_KEY]).`as`("epoch recorded in the commit").isEqualTo("1")
    }

    @Test
    fun `a restart of the same holder within the lease keeps its index and its identity`() {
        val first = replica("a").apply { open(); apply(listOf(IndexAction.Upsert(row(JAR)))) }
        val identityBefore = first.indexIdentity()
        first.close()

        val restarted = replica("a").apply { open() }

        assertThat(restarted.indexIdentity()).`as`("identity after a restart of the same holder").isEqualTo(identityBefore)
        assertThat(restarted.count()).`as`("documents after a restart of the same holder").isEqualTo(1L)
    }

    @Test
    fun `a restart of the same holder after its lease expired is a new grant and rebuilds`() {
        val first = replica("a").apply { open(); apply(listOf(IndexAction.Upsert(row(JAR)))) }
        val identityBefore = first.indexIdentity()
        first.close()
        clock.advance(Duration.ofSeconds(61))

        val restarted = replica("a").apply { open() }

        assertThat(restarted.indexIdentity())
            .`as`("identity after a restart once the lease expired")
            .isNotEqualTo(identityBefore)
    }

    private fun row(path: String): IndexRow {
        val parsed = ArtifactPathParser.parse(REPOSITORY, path) as ArtifactPathResult.Row
        return IndexRow(parsed.file, Provenance.HOSTED, 10L, 0L, null, null, 0L)
    }

    private companion object {
        const val REPOSITORY = "releases"
        const val JAR = "com/example/app/1.0/app-1.0.jar"
    }

}
