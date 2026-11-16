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

import net.onelitefeather.ingot.search.ArtifactFilePage
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.IndexStatus
import net.onelitefeather.ingot.search.SearchFacets
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.BackendHealth
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.backend.SamplePage
import net.onelitefeather.ingot.search.backend.StoredRow
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.SegmentInfos
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.random.Random

/**
 * The `lucene` backend (design D2 to D6): an index of artifact search in one directory on the local disk of this replica. The
 * index is a derived copy of the database. Its commits record the mapping version and the identity of the index, so that
 * [open] can refuse an index it does not write and the core can reset the rebuild cursor of a new index.
 *
 * Only [open], [close], [health], [indexIdentity] and [count] are implemented here. The writes and the reads land in the following
 * pull requests of this change; until then the other members stop with a message that says so.
 */
internal class LuceneSearchBackend(private val settings: LuceneSettings) : ArtifactSearchBackend {

    override val id: String = ID

    /** The index lives outside the database transaction, so the rebuild applies a page first and commits its cursor after. */
    override val joinsDatabaseTransaction: Boolean = false

    @Volatile
    private var store: Directory? = null

    @Volatile
    private var writer: IndexWriter? = null

    @Volatile
    private var identity: String? = null

    /**
     * Creates the directory when it is missing, then reads the commit of an existing index. An index whose mapping version is not
     * [MAPPING_VERSION] is refused, and so is one that cannot be read. Nothing is deleted or rewritten in either case.
     */
    override fun open() {
        val directory = settings.directory
        check(!Files.isRegularFile(directory)) {
            "${LuceneSettings.DIRECTORY_VARIABLE} names a file, it must name a directory: $directory"
        }
        Files.createDirectories(directory)
        val index = FSDirectory.open(directory)
        try {
            val userData = readCommitData(index, directory)
            val opened = if (userData == null) createIndex(index) else reopenIndex(index, userData)
            store = index
            writer = opened
        } catch (failure: Exception) {
            index.close()
            throw failure
        }
    }

    private fun readCommitData(index: Directory, directory: Path): Map<String, String>? {
        return try {
            if (DirectoryReader.indexExists(index)) SegmentInfos.readLatestCommit(index).userData else null
        } catch (failure: IOException) {
            throw IllegalStateException("the Lucene index at $directory cannot be read: ${failure.javaClass.simpleName}", failure)
        }
    }

    private fun createIndex(index: Directory): IndexWriter {
        val opened = IndexWriter(index, IndexWriterConfig(StandardAnalyzer()))
        val created = UUID.randomUUID().toString()
        opened.setLiveCommitData(
            mapOf(MAPPING_VERSION_KEY to MAPPING_VERSION.toString(), IDENTITY_KEY to created).entries,
        )
        opened.commit()
        identity = created
        return opened
    }

    private fun reopenIndex(index: Directory, userData: Map<String, String>): IndexWriter {
        val version = userData[MAPPING_VERSION_KEY]
        check(version == MAPPING_VERSION.toString()) {
            "the index at ${settings.directory} records mapping version $version, this plugin writes mapping version $MAPPING_VERSION"
        }
        identity = checkNotNull(userData[IDENTITY_KEY]) { "the index at ${settings.directory} records no identity" }
        return IndexWriter(index, IndexWriterConfig(StandardAnalyzer()))
    }

    override fun close() {
        writer?.close()
        store?.close()
        writer = null
        store = null
    }

    override fun health(): BackendHealth =
        if (writer != null) BackendHealth.Up else BackendHealth.Down("the index is not open")

    override fun indexIdentity(): String? = identity

    override fun count(): Long {
        val index = checkNotNull(store) { "the index is not open" }
        return DirectoryReader.open(index).use { reader -> reader.numDocs().toLong() }
    }

    override fun apply(actions: List<IndexAction>): Unit = TODO("the writes land in the write pull request")

    override fun pathsAt(repository: String, prefix: String, after: String?, limit: Int): List<String> =
        TODO("the reads land in the query pull request")

    override fun existing(repository: String, paths: Collection<String>): Map<String, StoredRow> =
        TODO("the reads land in the query pull request")

    override fun touch(repository: String, paths: Collection<String>, indexedAt: Long): Int =
        TODO("the writes land in the write pull request")

    override fun deleteStale(repository: String, before: Long, limit: Int): Int =
        TODO("the writes land in the write pull request")

    override fun deleteRepository(repository: String, limit: Int): Int =
        TODO("the writes land in the write pull request")

    override fun repositories(): List<String> = TODO("the reads land in the query pull request")

    override fun sampleStart(random: Random): String? = TODO("the reads land in the query pull request")

    override fun sample(cursor: String?, limit: Int): SamplePage = TODO("the reads land in the query pull request")

    override fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage =
        TODO("the queries land in the query pull request")

    override fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets =
        TODO("the queries land in the query pull request")

    override fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage =
        TODO("the queries land in the query pull request")

    override fun status(): IndexStatus = TODO("the status lands in the query pull request")

    companion object {

        const val ID: String = "lucene"

        /** The version of the document layout this plugin writes. An index of another version is refused at open. */
        const val MAPPING_VERSION: Int = 1

        const val MAPPING_VERSION_KEY: String = "ingot_mapping_version"
        const val IDENTITY_KEY: String = "ingot_index_identity"
    }

}
