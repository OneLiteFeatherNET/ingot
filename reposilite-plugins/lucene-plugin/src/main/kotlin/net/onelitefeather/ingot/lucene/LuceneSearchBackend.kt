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
import net.onelitefeather.ingot.search.IndexState
import net.onelitefeather.ingot.search.IndexStatus
import net.onelitefeather.ingot.search.SearchFacets
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.BackendHealth
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.backend.SamplePage
import net.onelitefeather.ingot.search.backend.StoredRow
import net.onelitefeather.ingot.search.database.pathHash
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.MultiDocValues
import org.apache.lucene.index.SegmentInfos
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.TermInSetQuery
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.random.Random

/**
 * The `lucene` backend (design D2 to D6): an index of artifact search in one directory on the local disk of this replica. The
 * index is a derived copy of the database. Its commits record the mapping version and the identity of the index, so that [open]
 * can refuse an index it does not write and the core can reset the rebuild cursor of a new index.
 *
 * Every write is one batch: it is committed before [apply] returns, and a failed batch rolls the writer back and opens it again from
 * the last commit. A commit that fails for lack of disk space is busy for [DISK_FULL_RETRY], and any other failure is passed on.
 *
 * [openDirectory] opens the directory of the index. Production uses the file system; the tests inject a directory that fails on
 * demand.
 *
 * The reads of the search, the facets and the files, the sample and the status land in the query pull request of this change; until
 * then those members stop with a message that says so.
 */
internal class LuceneSearchBackend(
    private val settings: LuceneSettings,
    private val openDirectory: (Path) -> Directory = { FSDirectory.open(it) },
    /** The usable bytes of the volume that holds [LuceneSettings.directory]. Production asks the file store; the tests inject it. */
    private val usableSpace: (Path) -> Long = { Files.getFileStore(it).usableSpace },
) : ArtifactSearchBackend {

    override val id: String = ID

    /** The index lives outside the database transaction, so the rebuild applies a page first and commits its cursor after. */
    override val joinsDatabaseTransaction: Boolean = false

    @Volatile
    private var store: Directory? = null

    @Volatile
    private var writer: IndexWriter? = null

    @Volatile
    private var searchers: SearcherManager? = null

    @Volatile
    private var identity: String? = null

    private val reader = LuceneReader(indexStatus = { status() })

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
        val index = openDirectory(directory)
        try {
            val existing = readCommitData(index, directory)
            val opened = IndexWriter(index, config())
            try {
                identity = if (existing == null) createIdentity(opened) else verifiedIdentity(existing)
            } catch (failure: Exception) {
                opened.close()
                throw failure
            }
            store = index
            install(opened)
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

    /** Writes the first commit of a new index: its mapping version and a random identity. */
    private fun createIdentity(opened: IndexWriter): String {
        val created = UUID.randomUUID().toString()
        opened.setLiveCommitData(
            mapOf(MAPPING_VERSION_KEY to MAPPING_VERSION.toString(), IDENTITY_KEY to created).entries,
        )
        opened.commit()
        return created
    }

    private fun verifiedIdentity(userData: Map<String, String>): String {
        val version = userData[MAPPING_VERSION_KEY]
        check(version == MAPPING_VERSION.toString()) {
            "the index at ${settings.directory} records mapping version $version, this plugin writes mapping version $MAPPING_VERSION"
        }
        return checkNotNull(userData[IDENTITY_KEY]) { "the index at ${settings.directory} records no identity" }
    }

    private fun config(): IndexWriterConfig = IndexWriterConfig(StandardAnalyzer())

    private fun install(opened: IndexWriter) {
        writer = opened
        searchers = SearcherManager(opened, null)
    }

    override fun close() {
        runCatching { searchers?.close() }
        runCatching { writer?.close() }
        runCatching { store?.close() }
        searchers = null
        writer = null
        store = null
    }

    override fun health(): BackendHealth = when {
        writer == null -> BackendHealth.Down("the index is not open")
        usableSpace(settings.directory) < settings.minFreeBytes -> BackendHealth.Down(LOW_SPACE)
        else -> BackendHealth.Up
    }

    override fun indexIdentity(): String? = identity

    override fun count(): Long {
        val manager = checkNotNull(searchers) { "the index is not open" }
        val searcher = manager.acquire()
        try {
            return searcher.indexReader.numDocs().toLong()
        } finally {
            manager.release(searcher)
        }
    }

    override fun apply(actions: List<IndexAction>) = transaction { current ->
        actions.forEach { action -> applyAction(current, action) }
    }

    /** One action of a batch. Removals and reorigins read the rows of the writer's current state, so a batch sees its own writes. */
    private fun applyAction(current: IndexWriter, action: IndexAction) {
        when (action) {
            is IndexAction.Upsert -> current.updateDocument(
                idTerm(action.row.file.repository, action.row.file.path),
                LuceneDocument.of(action.row),
            )
            is IndexAction.RemovePath -> current.deleteDocuments(idTerm(action.repository, action.path))
            is IndexAction.RemovePrefix -> {
                val notAfter = action.notAfter.toEpochMilli()
                matching(current, action.repository, action.prefix)
                    .filter { hit -> hit.indexedAt <= notAfter }
                    .forEach { hit -> current.deleteDocuments(idTerm(action.repository, pathOf(hit))) }
            }
            is IndexAction.Reorigin -> matching(current, action.repository, action.directory).forEach { hit ->
                val row = LuceneDocument.toRow(hit.document, hit.indexedAt).copy(provenance = action.provenance)
                current.updateDocument(idTerm(row.file.repository, row.file.path), LuceneDocument.of(row))
            }
        }
    }

    override fun pathsAt(repository: String, prefix: String, after: String?, limit: Int): List<String> =
        withSearcher { searcher -> reader.pathsAt(searcher, repository, prefix, after, limit) }

    /** The stored state of the rows of [paths] in [repository], read from the committed index. */
    override fun existing(repository: String, paths: Collection<String>): Map<String, StoredRow> {
        if (paths.isEmpty()) return emptyMap()
        val manager = checkNotNull(searchers) { "the index is not open" }
        val ids = paths.map { path -> BytesRef(pathHash(repository, path)) }
        val searcher = manager.acquire()
        try {
            val hits = searcher.search(TermInSetQuery(LuceneDocument.ID, ids), ids.size)
            return hits.scoreDocs.associate { hit ->
                val document = searcher.storedFields().document(hit.doc)
                checkNotNull(document.get(LuceneDocument.PATH)) { "a document without a path" } to LuceneDocument.storedRow(document)
            }
        } finally {
            manager.release(searcher)
        }
    }

    /** Sets the indexed time of the rows that exist, by an update of their doc value: no document is written again. */
    override fun touch(repository: String, paths: Collection<String>, indexedAt: Long): Int {
        val present = existing(repository, paths).keys
        if (present.isEmpty()) return 0
        transaction { current ->
            present.forEach { path ->
                current.updateNumericDocValue(idTerm(repository, path), LuceneDocument.INDEXED_AT, indexedAt)
            }
        }
        return present.size
    }

    override fun deleteStale(repository: String, before: Long, limit: Int): Int = transaction { current ->
        val stale = inRepository(current, repository).filter { it.indexedAt < before }.take(limit)
        stale.forEach { hit -> current.deleteDocuments(idTerm(repository, pathOf(hit))) }
        stale.size
    }

    override fun deleteRepository(repository: String, limit: Int): Int = transaction { current ->
        val rows = inRepository(current, repository).take(limit)
        rows.forEach { hit -> current.deleteDocuments(idTerm(repository, pathOf(hit))) }
        rows.size
    }

    override fun repositories(): List<String> = withSearcher { searcher -> reader.repositories(searcher) }

    override fun sampleStart(random: Random): String? = withSearcher { searcher ->
        val rows = reader.sampleRows(searcher)
        if (rows.isEmpty()) null else rows[random.nextInt(rows.size)].let { cursorOf(it.repository, it.path) }
    }

    /**
     * Up to [limit] rows after [cursor] in the order of repository and path. When the rows after the cursor end before the limit, the
     * sample continues with the rows at or before the cursor, from the start, so a small index is covered completely.
     */
    override fun sample(cursor: String?, limit: Int): SamplePage = withSearcher { searcher ->
        val all = reader.sampleRows(searcher)
        val after = cursor?.let { all.indexOfFirst { row -> cursorOf(row.repository, row.path) > it } }?.takeIf { it >= 0 }
        val rows = (if (cursor == null) all else after?.let { all.subList(it, all.size) } ?: emptyList()).take(limit).toMutableList()
        if (cursor != null && rows.size < limit) {
            rows += all.filter { cursorOf(it.repository, it.path) <= cursor }.take(limit - rows.size)
        }
        SamplePage(rows, rows.lastOrNull()?.let { cursorOf(it.repository, it.path) })
    }

    override fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage =
        withSearcher { searcher -> reader.search(query, access, searcher) }

    override fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets =
        withSearcher { searcher -> reader.facets(query, access, searcher) }

    override fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage =
        withSearcher { searcher -> reader.files(query, access, searcher) }

    /** The state of the index: missing when it holds no row, ready otherwise. The core keeps the outbox and rebuild counters. */
    override fun status(): IndexStatus = IndexStatus(
        state = if (count() == 0L) IndexState.MISSING else IndexState.READY,
        lagSeconds = 0L,
        lastFullRebuild = null,
        deadLetters = 0L,
        droppedEnqueues = 0L,
        pendingEntries = 0L,
        tokensComplete = true,
    )

    private fun <T> withSearcher(block: (IndexSearcher) -> T): T {
        val manager = checkNotNull(searchers) { "the index is not open" }
        val searcher = manager.acquire()
        try {
            return block(searcher)
        } finally {
            manager.release(searcher)
        }
    }

    private fun cursorOf(repository: String, path: String): String = "$repository\n$path"

    // ---- transactions ---------------------------------------------------------------------------------------------------

    /**
     * Runs [block] on the writer, commits, and refreshes the searcher before it returns, so a reader sees the batch at once. A failure
     * reopens the writer from the last commit. A commit that fails for lack of disk space becomes [BackendBusyException].
     */
    private fun <T> transaction(block: (IndexWriter) -> T): T {
        val current = checkNotNull(writer) { "the index is not open" }
        if (usableSpace(settings.directory) < settings.minFreeBytes) {
            throw BackendBusyException(LOW_SPACE, DISK_FULL_RETRY)
        }
        return try {
            val result = block(current)
            current.commit()
            checkNotNull(searchers).maybeRefreshBlocking()
            result
        } catch (failure: Exception) {
            reopenFromLastCommit()
            throw failureOf(failure)
        }
    }

    private fun reopenFromLastCommit() {
        runCatching { searchers?.close() }
        runCatching { writer?.rollback() }
        install(IndexWriter(checkNotNull(store) { "the index is not open" }, config()))
    }

    private fun failureOf(failure: Exception): Exception =
        if (failure is IOException && failure.message?.contains(NO_SPACE) == true) {
            BackendBusyException("the index volume is full, the batch waits for space", DISK_FULL_RETRY)
        } else {
            failure
        }

    // ---- lookups for writes ---------------------------------------------------------------------------------------------

    /** A stored document with the indexed time of its doc value. */
    private class Hit(val document: Document, val indexedAt: Long)

    /** Every row of [repository], read from the writer's current state. */
    private fun inRepository(current: IndexWriter, repository: String): List<Hit> = matching(current, repository, prefix = null)

    /** The rows of [repository] at [prefix] and below it, or every row of the repository when [prefix] is null. */
    private fun matching(current: IndexWriter, repository: String, prefix: String?): List<Hit> =
        DirectoryReader.open(current).use { reader ->
            val searcher = IndexSearcher(reader)
            val query = BooleanQuery.Builder()
                .add(TermQuery(Term(LuceneDocument.REPOSITORY, repository)), BooleanClause.Occur.FILTER)
                .apply {
                    if (prefix != null) {
                        add(
                            BooleanQuery.Builder()
                                .add(TermQuery(Term(LuceneDocument.PATH, prefix)), BooleanClause.Occur.SHOULD)
                                .add(PrefixQuery(Term(LuceneDocument.PATH, "$prefix/")), BooleanClause.Occur.SHOULD)
                                .build(),
                            BooleanClause.Occur.FILTER,
                        )
                    }
                }
                .build()
            val docs = searcher.search(query, maxOf(1, reader.maxDoc())).scoreDocs.sortedBy { it.doc }
            val indexedAt = MultiDocValues.getNumericValues(reader, LuceneDocument.INDEXED_AT)
            docs.map { hit ->
                checkNotNull(indexedAt).advanceExact(hit.doc)
                Hit(searcher.storedFields().document(hit.doc), indexedAt.longValue())
            }
        }

    private fun pathOf(hit: Hit): String = checkNotNull(hit.document.get(LuceneDocument.PATH)) { "a document without a path" }

    private fun idTerm(repository: String, path: String): Term = Term(LuceneDocument.ID, pathHash(repository, path))

    companion object {

        const val ID: String = "lucene"

        /** The version of the document layout this plugin writes. An index of another version is refused at open. */
        const val MAPPING_VERSION: Int = 1

        const val MAPPING_VERSION_KEY: String = "ingot_mapping_version"
        const val IDENTITY_KEY: String = "ingot_index_identity"

        /** How long a batch waits when the index volume has no space left (design D3 and D9). */
        val DISK_FULL_RETRY: Duration = Duration.ofSeconds(60)

        private const val NO_SPACE = "No space left on device"

        /** The reason of a batch that waits for the free space to return, and of the health while it is short. */
        const val LOW_SPACE = "low disk space"
    }

}
