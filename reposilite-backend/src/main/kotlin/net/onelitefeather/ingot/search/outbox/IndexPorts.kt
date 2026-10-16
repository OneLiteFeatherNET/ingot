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

package net.onelitefeather.ingot.search.outbox

import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import java.time.Instant

/** What storage reports about one file now. Directories and missing paths have no [StoredFile]. */
data class StoredFile(
    val sizeBytes: Long,
    val lastModified: Instant,
)

/** The storage the worker reads. Every call reads the current state; nothing is cached. */
internal interface IndexStorage {

    /** The file at [path] of [repository], or null when it is missing or a directory. */
    fun file(repository: String, path: String): StoredFile?

    /** Whether anything, file or directory, is stored at [path] of [repository]. */
    fun exists(repository: String, path: String): Boolean

    /** The first [maxBytes] of the file at [path], or null when it is missing. Used for checksum sidecars. */
    fun head(repository: String, path: String, maxBytes: Int): String?

}

/** The origin of a version directory, from the mirror ledger. */
internal fun interface OriginLookup {

    /** [Provenance.HOSTED] unless [directory] of [repository] holds a mirror copy. */
    fun provenance(repository: String, directory: String): Provenance

}

/** One change the worker makes to `search_artifact`. A batch of actions is applied in one transaction. */
internal sealed interface IndexAction {

    data class Upsert(val row: IndexRow) : IndexAction

    data class RemovePath(val repository: String, val path: String) : IndexAction

    /** Removes the rows at [prefix] and below it that were indexed at or before [notAfter] (3.3). */
    data class RemovePrefix(val repository: String, val prefix: String, val notAfter: Instant) : IndexAction

    data class Reorigin(val repository: String, val directory: String, val provenance: Provenance) : IndexAction

}

/** The write side of `search_artifact`, and the one read the removal of a directory needs. */
internal interface ArtifactIndexWriter {

    /** Applies [actions] in order, in one transaction. */
    fun apply(actions: List<IndexAction>)

    /** The paths of the rows at [prefix] and below it, in path order, after [after], at most [limit]. */
    fun pathsAt(repository: String, prefix: String, after: String?, limit: Int): List<String>

}
