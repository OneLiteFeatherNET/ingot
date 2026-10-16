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

package com.reposilite.maven

import com.reposilite.maven.api.Checksum
import com.reposilite.maven.api.METADATA_FILE
import com.reposilite.storage.api.Location
import net.onelitefeather.ingot.search.ArtifactIndexSink

/**
 * The path the artifact index is told about when [this] location was written. A checksum sidecar maps to its base file,
 * because the index stores the checksums of the base file. `maven-metadata.xml` and its checksums are not artifacts and
 * map to null (artifact search spec, 3.5).
 */
internal fun Location.artifactIndexPath(): String? {
    val name = getSimpleName()
    val checksumSuffix = Checksum.entries.map { ".${it.extension}" }.firstOrNull { name.endsWith(it) }
    val baseName = checksumSuffix?.let { name.removeSuffix(it) } ?: name
    if (baseName.startsWith(METADATA_FILE)) {
        return null
    }
    return resolveSibling(baseName).provenancePath()
}

/** Tells the index that [location] of [repository] was written, unless the write is not an artifact. */
internal fun ArtifactIndexSink.indexChanged(repository: String, location: Location) {
    location.artifactIndexPath()?.let { changed(repository, it) }
}
