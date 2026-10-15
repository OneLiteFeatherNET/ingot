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

package com.reposilite.cleanup

import com.reposilite.storage.StoredFile
import java.time.Instant

/**
 * Finds the version directories of a repository from its files.
 *
 * A version directory is a directory that holds a `.pom` file directly. Directories inside a version are part of
 * that version, and the artifact directory above it holds the version directories. Files that sit in an artifact
 * directory, such as `maven-metadata.xml`, are not versions. A version whose files report no modification time is
 * left out, because no policy could judge its age.
 */
internal object CleanupInventory {

    fun versionsOf(repository: String, files: List<StoredFile>, cached: Boolean): List<ArtifactVersion> {
        val directoriesWithPom = files
            .asSequence()
            .filter { it.path.endsWith(".pom") }
            .map { parentOf(it.path) }
            .toSet()

        // a directory below another version belongs to that version and is not a version of its own
        val versionDirectories = directoriesWithPom
            .filter { parentOf(it).isNotEmpty() && parentOf(it) !in directoriesWithPom }
            .toSet()
        if (versionDirectories.isEmpty()) {
            return emptyList()
        }

        val sizes = HashMap<String, Long>()
        val modified = HashMap<String, Instant>()
        files.forEach { file ->
            val directory = nearestVersionDirectory(parentOf(file.path), versionDirectories) ?: return@forEach
            sizes.merge(directory, file.sizeBytes, Long::plus)
            file.lastModified?.let { time -> modified.merge(directory, time) { current, other -> if (other.isAfter(current)) other else current } }
        }

        return versionDirectories
            .mapNotNull { directory ->
                val lastModified = modified[directory] ?: return@mapNotNull null
                ArtifactVersion(
                    repository = repository,
                    artifact = parentOf(directory),
                    version = directory.substringAfterLast('/'),
                    path = directory,
                    sizeBytes = sizes[directory] ?: 0L,
                    lastModified = lastModified,
                    cached = cached,
                )
            }
            .sortedBy { it.path }
    }

    private fun parentOf(path: String): String =
        path.substringBeforeLast('/', "")

    private fun nearestVersionDirectory(start: String, versionDirectories: Set<String>): String? {
        var directory = start
        while (directory.isNotEmpty()) {
            if (directory in versionDirectories) return directory
            directory = parentOf(directory)
        }
        return null
    }

}
