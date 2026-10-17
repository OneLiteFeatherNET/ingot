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


package com.reposilite.storage.filesystem

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.internalServerError
import com.reposilite.storage.StorageEntry
import com.reposilite.storage.StorageEntryPage
import com.reposilite.storage.StorageEntrySource
import com.reposilite.storage.compareSegments
import com.reposilite.storage.paginate
import com.reposilite.storage.startsWithSegments
import com.reposilite.storage.toLocationOrNull
import com.reposilite.storage.toSegments
import com.reposilite.storage.api.Location
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.isDirectory
import kotlin.io.path.useDirectoryEntries
import panda.std.Result
import panda.std.asSuccess

/**
 * Depth-first listing of a file system tree with children sorted by name. The cursor is the root-relative path of
 * the last entry of a page, and the walk resumes after it without rescanning: at each directory on the way to the
 * cursor only the entries that sort after it are visited, and the directories beside that path are not opened.
 *
 * Directories that have vanished during the walk are skipped, and so are entries that cannot be expressed as a
 * [Location]. Symbolic links are followed, as the rest of the file system provider does.
 *
 * @param rootDirectory root directory of the storage space
 */
class FileSystemEntrySource(private val rootDirectory: Path) : StorageEntrySource {

    override fun listEntries(prefix: Location, after: String?, limit: Int): Result<StorageEntryPage, ErrorResponse> {
        require(limit > 0) { "limit must be positive, was $limit" }

        return try {
            val entries = entriesAfter(prefix = prefix.toString().toSegments(), cursor = after?.toSegments())
            paginate(entries, limit).asSuccess()
        } catch (exception: IOException) {
            internalServerError(exception.message ?: exception::class.toString())
        }
    }

    private fun entriesAfter(prefix: List<String>, cursor: List<String>?): Sequence<StorageEntry> =
        when {
            cursor == null -> walk(prefix, cursor = null)
            cursor.startsWithSegments(prefix) -> walk(prefix, cursor)
            compareSegments(prefix, cursor) > 0 -> walk(prefix, cursor = null)
            else -> emptySequence()
        }

    private fun walk(directory: List<String>, cursor: List<String>?): Sequence<StorageEntry> = sequence {
        val path = rootDirectory.resolve(directory.joinToString("/"))
        if (!path.isDirectory()) {
            return@sequence
        }

        val children = path.useDirectoryEntries { entries -> entries.sortedBy { it.fileName.toString() }.toList() }

        for (child in children) {
            val segments = directory + child.fileName.toString()
            // The cursor lies inside this entry when it starts with the entry's segments.
            val cursorInside = cursor != null && cursor.startsWithSegments(segments)

            if (cursor != null && !cursorInside && compareSegments(segments, cursor) < 0) {
                continue // the whole entry sorts before the cursor
            }

            val attributes = runCatching { Files.readAttributes(child, BasicFileAttributes::class.java) }.getOrNull()
                ?: continue // vanished between listing and reading

            if (attributes.isDirectory) {
                yieldAll(walk(segments, cursor = if (cursorInside) cursor else null))
            } else if (!cursorInside) {
                val location = segments.toLocationOrNull() ?: continue
                yield(
                    StorageEntry(
                        path = location,
                        size = attributes.size(),
                        lastModified = attributes.lastModifiedTime().toInstant(),
                    )
                )
            }
        }
    }

}
