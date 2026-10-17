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


package com.reposilite.storage

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.internalServerError
import com.reposilite.storage.api.Location
import java.time.Instant
import panda.std.Result
import panda.std.asSuccess

/**
 * A flat listing of the files below a location, with size and modification time in every entry.
 *
 * Each provider defines its own total order and its own cursor. The cursor is the root-relative path of the
 * last entry of a page, and it is handed back unchanged. Use [entrySource] to get the source of any provider.
 */
interface StorageEntrySource {

    /**
     * Lists at most [limit] files below [prefix], strictly after the root-relative path [after] in the order of
     * this source. [StorageEntryPage.next] is null when the listing has no more entries.
     */
    fun listEntries(prefix: Location, after: String?, limit: Int): Result<StorageEntryPage, ErrorResponse>

}

/**
 * A file of a [StorageEntrySource].
 *
 * @param path path relative to the root of the storage
 * @param size size in bytes
 * @param lastModified last modification time, [Instant.EPOCH] when the storage does not report one
 */
data class StorageEntry(
    val path: Location,
    val size: Long,
    val lastModified: Instant,
)

/**
 * One page of a [StorageEntrySource] listing.
 *
 * @param next the cursor to pass as `after` for the following page, null at the end of the listing
 */
data class StorageEntryPage(
    val entries: List<StorageEntry>,
    val next: String?,
)

/**
 * The entry source of this provider: the provider itself when it implements [StorageEntrySource], otherwise a
 * [WalkingEntrySource] over the generic listing.
 */
fun StorageProvider.entrySource(): StorageEntrySource =
    this as? StorageEntrySource ?: WalkingEntrySource(this)

/**
 * Orders paths by their segments, one segment at a time. A path whose segments are a prefix of another path's
 * segments sorts first. Depth-first walks with children sorted by name produce exactly this order.
 */
internal fun compareSegments(left: List<String>, right: List<String>): Int {
    for (index in 0 until minOf(left.size, right.size)) {
        val comparison = left[index].compareTo(right[index])
        if (comparison != 0) return comparison
    }
    return left.size.compareTo(right.size)
}

internal fun String.toSegments(): List<String> =
    split('/').filter { it.isNotEmpty() }

internal fun List<String>.startsWithSegments(prefix: List<String>): Boolean =
    size >= prefix.size && subList(0, prefix.size) == prefix

internal fun List<String>.toLocationOrNull(): Location? =
    runCatching { Location.of(joinToString("/")) }.getOrNull()

/**
 * Takes one page from [entries] that are already in order. Reads one entry beyond [limit] to know whether
 * more follow, so the sequence is only consumed as far as the page reaches.
 */
internal fun paginate(entries: Sequence<StorageEntry>, limit: Int): StorageEntryPage {
    val window = entries.take(limit + 1).toList()
    val page = window.take(limit)
    return StorageEntryPage(
        entries = page,
        next = if (window.size > limit) page.last().path.toString() else null,
    )
}

/**
 * Fallback for providers without a native listing. Walks the whole storage with [walkFiles] and sorts the result,
 * so every page costs a full walk. It is slow on large storages but correct, and it keeps third-party providers
 * working without any change to the [StorageProvider] contract.
 */
class WalkingEntrySource(private val storage: StorageProvider) : StorageEntrySource {

    override fun listEntries(prefix: Location, after: String?, limit: Int): Result<StorageEntryPage, ErrorResponse> {
        require(limit > 0) { "limit must be positive, was $limit" }
        val directory = prefix.toString().toSegments()
        val cursor = after?.toSegments()

        return try {
            val entries = storage.walkFiles()
                .asSequence()
                .map { file -> file.path.toSegments() to file }
                .filter { (segments, _) -> segments.startsWithSegments(directory) }
                .filter { (segments, _) -> cursor == null || compareSegments(segments, cursor) > 0 }
                .sortedWith { left, right -> compareSegments(left.first, right.first) }
                .mapNotNull { (segments, file) ->
                    segments.toLocationOrNull()?.let { path ->
                        StorageEntry(
                            path = path,
                            size = file.sizeBytes,
                            lastModified = file.lastModified ?: Instant.EPOCH,
                        )
                    }
                }

            paginate(entries, limit).asSuccess()
        } catch (exception: Exception) {
            internalServerError(exception.message ?: exception::class.toString())
        }
    }

}
