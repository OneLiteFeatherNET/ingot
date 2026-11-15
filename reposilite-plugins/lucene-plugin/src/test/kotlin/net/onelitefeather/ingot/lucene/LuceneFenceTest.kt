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
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.store.FSDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

/**
 * The fence of a commit: the lease check of a batch and its commit are not one atomic step, so the commit is only made while the lease
 * is still valid for the whole commit. A takeover can happen only once the lease has expired, and a commit that would end after the
 * expiry minus a margin is not made.
 */
internal class LuceneFenceTest {

    @TempDir
    lateinit var root: Path

    private val clock = MutableClock()
    private val table = LeaseTable()
    private val opened = mutableListOf<LuceneSearchBackend>()

    @AfterEach
    fun closeBackends() {
        opened.forEach { it.close() }
    }

    @Test
    fun `a takeover between the lease check and the commit stops the batch before it is committed`() {
        val directory = root.resolve("holder")
        var rival: LuceneSearchBackend? = null
        val holder = backend(directory, owner = "holder", beforeCommit = {
            clock.advance(Duration.ofSeconds(61))
            rival = backend(root.resolve("rival"), owner = "rival").also { it.open() }
        })
        holder.open()

        assertThrows<BackendBusyException> { holder.apply(listOf(IndexAction.Upsert(row(JAR)))) }

        assertThat(rival?.indexIdentity()).`as`("rival took the lease in the window").isNotNull()
        assertThat(storedPaths(directory)).`as`("paths committed by the replaced holder").isEmpty()
    }

    @Test
    fun `a commit that ends inside the lease window is written`() {
        val directory = root.resolve("holder")
        val holder = backend(directory, owner = "holder", beforeCommit = { clock.advance(Duration.ofSeconds(10)) })
        holder.open()

        holder.apply(listOf(IndexAction.Upsert(row(JAR))))

        assertThat(storedPaths(directory)).`as`("paths committed inside the window").containsExactly(JAR)
    }

    private fun backend(directory: Path, owner: String, beforeCommit: () -> Unit = {}): LuceneSearchBackend {
        val settings = LuceneSettings.read(mapOf("INGOT_LUCENE_DIRECTORY" to directory.toString()), root.resolve("unused"))
        return LuceneSearchBackend(
            settings = settings,
            leases = FakeLeases(table, owner = owner, clock = clock),
            clock = clock,
            log = RecordingLogger(),
            usableSpace = { Long.MAX_VALUE },
            beforeCommit = beforeCommit,
        ).also { opened += it }
    }

    private fun storedPaths(directory: Path): List<String> =
        DirectoryReader.open(FSDirectory.open(directory)).use { reader ->
            (0 until reader.maxDoc()).map { reader.storedFields().document(it).get(LuceneDocument.PATH) }
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
