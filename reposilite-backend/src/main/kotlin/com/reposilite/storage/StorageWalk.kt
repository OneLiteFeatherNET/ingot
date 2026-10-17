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

import com.reposilite.storage.api.AbstractDirectoryInfo
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.Location
import java.time.Instant

/**
 * A file found by [walkFiles].
 *
 * @param path path relative to the root of the storage, with forward slashes
 * @param sizeBytes size of the file, 0 when the storage does not report one
 * @param lastModified last modification time, null when the storage does not report one
 */
data class StoredFile(
    val path: String,
    val sizeBytes: Long,
    val lastModified: Instant?,
)

/**
 * Lists every file below the root of this storage.
 *
 * Each directory is read once through [StorageProvider.getFileDetails], which works the same way for the
 * file system and for object storage. A directory that cannot be read is skipped, so one broken entry does
 * not fail the whole walk, but it is never skipped silently: [onUnreadable] receives its path (empty for the
 * root) and the reason, which is the error of the listing, the actual type when it is not a directory, or an
 * invalid path. The result is not sorted.
 */
fun StorageProvider.walkFiles(onUnreadable: (path: String, reason: String) -> Unit = { _, _ -> }): List<StoredFile> {
    val files = mutableListOf<StoredFile>()
    visitDirectory(path = "", files = files, onUnreadable = onUnreadable)
    return files
}

private fun StorageProvider.visitDirectory(
    path: String,
    files: MutableList<StoredFile>,
    onUnreadable: (path: String, reason: String) -> Unit,
) {
    val location = if (path.isEmpty()) {
        Location.empty()
    } else {
        runCatching { Location.of(path) }.getOrNull() ?: return onUnreadable(path, "invalid path")
    }

    val details = getFileDetails(location)
    if (details.isErr) {
        return onUnreadable(path, details.error.message)
    }

    val directory = details.get() as? DirectoryInfo
        ?: return onUnreadable(path, "not a directory: ${details.get()::class.java.simpleName}")

    directory.files.forEach { entry ->
        val childPath = if (path.isEmpty()) entry.name else "$path/${entry.name}"
        when (entry) {
            is DocumentInfo -> files += StoredFile(
                path = childPath,
                sizeBytes = entry.contentLength.coerceAtLeast(0),
                lastModified = entry.lastModifiedTime,
            )
            is AbstractDirectoryInfo -> visitDirectory(childPath, files, onUnreadable)
        }
    }
}
