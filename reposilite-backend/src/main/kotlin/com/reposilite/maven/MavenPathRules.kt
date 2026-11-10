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

@file:OptIn(ExperimentalIngotFormatApi::class)

package com.reposilite.maven

import com.reposilite.maven.api.Checksum
import com.reposilite.maven.api.METADATA_FILE
import com.reposilite.storage.api.Location
import net.onelitefeather.ingot.format.ExperimentalIngotFormatApi
import net.onelitefeather.ingot.format.PathRules

/**
 * The path rules of the Maven 2 layout. Each answer is the one the code of the repository, the resolution provider, the
 * mirror service and the artifact index gave before the format existed.
 */
internal object MavenPathRules : PathRules {

    /** Checksums, artifact descriptions and signatures are not counted as downloads. */
    private val ignoredDownloadExtensions = listOf(
        // Checksums
        ".md5",
        ".sha1",
        ".sha256",
        ".sha512",
        // Artifact descriptions
        ".pom",
        ".xml",
        ".module",
        // Artifact extensions
        "-sources.jar",
        "-javadoc.jar",
        // Sign
        ".asc"
    )

    override fun isGeneratedMetadata(path: Location): Boolean =
        path.getSimpleName().contains(METADATA_FILE)

    override fun isMetadataOrChecksum(path: Location): Boolean =
        path.getSimpleName().isMetadataOrChecksumFileName()

    override fun isResolutionMetadata(path: Location): Boolean =
        path.getSimpleName().let { name ->
            when {
                name.endsWith(".pom") -> true
                name.endsWith(".module") -> true
                name == METADATA_FILE -> true
                name.endsWith(".xml") -> name == "ivy.xml" || name.startsWith("ivy-")
                else -> false
            }
        }

    override fun countsAsDownload(path: Location): Boolean =
        ignoredDownloadExtensions.none { extension -> path.getSimpleName().endsWith(extension) }

    /**
     * A checksum sidecar maps to its base file, because the index stores the checksums of the base file. `maven-metadata.xml`
     * and its checksums are not artifacts and map to null (artifact search spec, 3.5).
     */
    override fun indexPath(path: Location): String? {
        val baseName = path.baseFileName()
        if (baseName.startsWith(METADATA_FILE)) {
            return null
        }
        return path.resolveSibling(baseName).provenancePath()
    }

    private fun Location.baseFileName(): String {
        val name = getSimpleName()
        val checksumSuffix = Checksum.entries.map { ".${it.extension}" }.firstOrNull { name.endsWith(it) }
        return checksumSuffix?.let { name.removeSuffix(it) } ?: name
    }

}

/** Metadata and checksum files are neutral for provenance: they do not make a directory a version. */
internal fun String.isMetadataOrChecksumFileName(): Boolean =
    startsWith(METADATA_FILE) || Checksum.entries.any { endsWith(".${it.extension}") }
