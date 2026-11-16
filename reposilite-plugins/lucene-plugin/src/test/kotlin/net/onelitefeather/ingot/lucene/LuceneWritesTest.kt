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
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.MultiDocValues
import org.apache.lucene.index.SegmentInfos
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.FilterDirectory
import org.apache.lucene.store.IOContext
import org.apache.lucene.store.IndexOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * The write side of the Lucene index: upserts, removals, touches, reorigins, the commit of every batch and its failures. Each test
 * opens its own index in its own temporary directory.
 */
internal class LuceneWritesTest {

    @TempDir
    lateinit var root: Path

    private val opened = mutableListOf<LuceneSearchBackend>()

    @AfterEach
    fun closeBackends() {
        opened.forEach { it.close() }
    }

    private fun open(
        directory: Path = root.resolve("index"),
        openDirectory: (Path) -> Directory = { FSDirectory.open(it) },
    ): LuceneSearchBackend {
        val settings = LuceneSettings.read(mapOf("INGOT_LUCENE_DIRECTORY" to directory.toString()), root.resolve("unused"))
        return LuceneSearchBackend(settings, openDirectory).also {
            opened += it
            it.open()
        }
    }

    /** A directory whose file creation fails on demand, on the index directory of this test. */
    private fun faulty(directory: Path = root.resolve("index")): FaultyDirectory {
        Files.createDirectories(directory)
        return FaultyDirectory(FSDirectory.open(directory))
    }

    @Test
    fun `an upsert replayed twice leaves one document`() {
        val backend = open()
        val upsert = IndexAction.Upsert(row(JAR))

        backend.apply(listOf(upsert))
        backend.apply(listOf(upsert))

        assertThat(backend.count()).`as`("documents after a replayed upsert").isEqualTo(1L)
    }

    @Test
    fun `a removal of a path that has no row does nothing`() {
        val backend = open()
        backend.apply(listOf(IndexAction.Upsert(row(JAR))))

        backend.apply(listOf(IndexAction.RemovePath(REPOSITORY, SOURCES)))

        assertThat(backend.count()).`as`("documents after a removal of a missing path").isEqualTo(1L)
    }

    @Test
    fun `a prefix removal keeps a row that was indexed after its cut-off`() {
        val backend = open()
        backend.apply(
            listOf(
                IndexAction.Upsert(row(JAR, indexedAt = BEFORE_CUTOFF)),
                IndexAction.Upsert(row(SOURCES, indexedAt = AFTER_CUTOFF)),
            ),
        )

        backend.apply(listOf(IndexAction.RemovePrefix(REPOSITORY, DIRECTORY, notAfter = Instant.ofEpochMilli(CUTOFF))))

        assertThat(backend.existing(REPOSITORY, listOf(JAR, SOURCES)).keys)
            .`as`("paths left after the prefix removal")
            .containsExactly(SOURCES)
    }

    @Test
    fun `a touch changes the indexed time of the row and nothing else`() {
        val backend = open()
        backend.apply(listOf(IndexAction.Upsert(row(JAR, indexedAt = BEFORE_CUTOFF))))

        val touched = backend.touch(REPOSITORY, listOf(JAR), AFTER_CUTOFF)

        assertThat(touched).`as`("rows that exist and were touched").isEqualTo(1)
        assertThat(indexedAtOf(root.resolve("index"), JAR)).`as`("indexed time after the touch").isEqualTo(AFTER_CUTOFF)
        assertThat(backend.existing(REPOSITORY, listOf(JAR))[JAR]?.sizeBytes).`as`("size after the touch").isEqualTo(10L)
    }

    @Test
    fun `a reorigin changes the origin of the rows below its directory and of no other`() {
        val backend = open()
        backend.apply(listOf(IndexAction.Upsert(row(MIRRORED_JAR)), IndexAction.Upsert(row(HOSTED_JAR))))

        backend.apply(listOf(IndexAction.Reorigin(REPOSITORY, MIRRORED_DIRECTORY, Provenance(Origin.MIRROR, UPSTREAM))))

        val origins = liveDocuments(root.resolve("index")).associate { it.get(LuceneDocument.PATH) to it.get(LuceneDocument.ORIGIN) }
        assertThat(origins[MIRRORED_JAR]).`as`("origin of the reoriginated row").isEqualTo(Origin.MIRROR.name)
        assertThat(origins[HOSTED_JAR]).`as`("origin of a row in another directory").isEqualTo(Origin.HOSTED.name)
    }

    @Test
    fun `apply returns only after the commit, and a reader opened afterwards sees the batch`() {
        val backend = open()

        backend.apply(listOf(IndexAction.Upsert(row(JAR)), IndexAction.Upsert(row(SOURCES))))

        val committed = DirectoryReader.open(FSDirectory.open(root.resolve("index"))).use { it.numDocs() }
        assertThat(committed).`as`("documents seen by a reader after apply").isEqualTo(2)
    }

    @Test
    fun `every commit keeps the mapping version and the identity of the index`() {
        val backend = open()

        backend.apply(listOf(IndexAction.Upsert(row(JAR))))

        val userData = SegmentInfos.readLatestCommit(FSDirectory.open(root.resolve("index"))).userData
        assertThat(userData[LuceneSearchBackend.MAPPING_VERSION_KEY]).`as`("mapping version after a write").isEqualTo("1")
        assertThat(userData[LuceneSearchBackend.IDENTITY_KEY]).`as`("identity after a write").isEqualTo(backend.indexIdentity())
    }

    @Test
    fun `a commit that fails for lack of disk space is busy for sixty seconds and leaves no row`() {
        val faults = faulty()
        val backend = open(openDirectory = { faults })
        faults.failure = IOException("No space left on device")

        val busy = assertThrows<BackendBusyException> { backend.apply(listOf(IndexAction.Upsert(row(JAR)))) }

        assertThat(busy.retryAfter).`as`("retry delay on a full disk").isEqualTo(Duration.ofSeconds(60))
        faults.failure = null
        backend.apply(listOf(IndexAction.Upsert(row(SOURCES))))
        assertThat(liveDocuments(root.resolve("index")).map { it.get(LuceneDocument.PATH) })
            .`as`("paths after the writer was reopened from the last commit")
            .containsExactly(SOURCES)
    }

    @Test
    fun `another I O error fails the batch without a busy answer and the next batch still applies`() {
        val faults = faulty()
        val backend = open(openDirectory = { faults })
        faults.failure = IOException("the disk is damaged")

        assertThrows<IOException> { backend.apply(listOf(IndexAction.Upsert(row(JAR)))) }

        faults.failure = null
        backend.apply(listOf(IndexAction.Upsert(row(SOURCES))))
        assertThat(backend.count()).`as`("documents after the writer was reopened").isEqualTo(1L)
    }

    @Test
    fun `a batch applied again after a stop between the commit and the outbox removal leaves the same documents`() {
        val directory = root.resolve("index")
        val batch = listOf(IndexAction.Upsert(row(JAR)), IndexAction.Upsert(row(SOURCES)))
        val crashed = open(directory)
        crashed.apply(batch)
        crashed.close()

        val restarted = open(directory)
        restarted.apply(batch)

        assertThat(restarted.count()).`as`("documents after the replay").isEqualTo(2L)
        assertThat(restarted.existing(REPOSITORY, listOf(JAR, SOURCES)).keys)
            .`as`("paths after the replay")
            .containsExactlyInAnyOrder(JAR, SOURCES)
    }

    private fun row(path: String, indexedAt: Long = NOW_MILLIS): IndexRow {
        val parsed = ArtifactPathParser.parse(REPOSITORY, path) as ArtifactPathResult.Row
        return IndexRow(
            file = parsed.file,
            provenance = if (path.startsWith(MIRRORED_DIRECTORY)) Provenance(Origin.MIRROR, UPSTREAM) else Provenance.HOSTED,
            sizeBytes = 10L,
            lastModified = NOW_MILLIS,
            sha1 = null,
            sha256 = null,
            indexedAt = indexedAt,
        )
    }

    private fun liveDocuments(directory: Path): List<Document> =
        DirectoryReader.open(FSDirectory.open(directory)).use { reader ->
            val searcher = IndexSearcher(reader)
            searcher.search(MatchAllDocsQuery(), maxOf(1, reader.maxDoc())).scoreDocs.map { hit ->
                searcher.storedFields().document(hit.doc)
            }
        }

    private fun indexedAtOf(directory: Path, path: String): Long =
        DirectoryReader.open(FSDirectory.open(directory)).use { reader ->
            val searcher = IndexSearcher(reader)
            val hit = searcher.search(TermQuery(Term(LuceneDocument.PATH, path)), 1).scoreDocs.single()
            val values = checkNotNull(MultiDocValues.getNumericValues(reader, LuceneDocument.INDEXED_AT))
            values.advanceExact(hit.doc)
            values.longValue()
        }

    private companion object {
        const val REPOSITORY = "releases"
        const val DIRECTORY = "com/example/app/1.0"
        const val JAR = "com/example/app/1.0/app-1.0.jar"
        const val SOURCES = "com/example/app/1.0/app-1.0-sources.jar"
        const val MIRRORED_DIRECTORY = "com/example/mirrored/1.0"
        const val MIRRORED_JAR = "com/example/mirrored/1.0/mirrored-1.0.jar"
        const val HOSTED_JAR = "com/example/hosted/1.0/hosted-1.0.jar"
        const val UPSTREAM = "upstream.example"
        const val NOW = "2026-10-17T10:00:00Z"
        val NOW_MILLIS: Long = Instant.parse(NOW).toEpochMilli()
        val CUTOFF: Long = Instant.parse("2026-10-17T12:00:00Z").toEpochMilli()
        val BEFORE_CUTOFF: Long = Instant.parse("2026-10-17T11:00:00Z").toEpochMilli()
        val AFTER_CUTOFF: Long = Instant.parse("2026-10-17T13:00:00Z").toEpochMilli()
    }

}

/** A directory whose file creation throws [failure] while it is set, to test the failures of a commit. */
internal class FaultyDirectory(delegate: Directory) : FilterDirectory(delegate) {

    @Volatile
    var failure: IOException? = null

    override fun createOutput(name: String, context: IOContext): IndexOutput {
        failure?.let { throw it }
        return super.createOutput(name, context)
    }

}
