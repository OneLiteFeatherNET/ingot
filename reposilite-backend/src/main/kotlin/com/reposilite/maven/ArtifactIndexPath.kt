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

@file:OptIn(net.onelitefeather.ingot.format.ExperimentalIngotFormatApi::class)

package com.reposilite.maven

import com.reposilite.maven.api.Checksum
import com.reposilite.storage.api.Location
import net.onelitefeather.ingot.format.PathRules
import net.onelitefeather.ingot.repository.index.ArtifactIndexSink

/** The index path of [this] location under the Maven rules, see [MavenPathRules.indexPath]. */
internal fun Location.artifactIndexPath(): String? =
    MavenPathRules.indexPath(this)

/** Whether this location is a checksum sidecar such as `app-1.0.0.jar.sha1`. */
internal fun Location.isChecksumSidecar(): Boolean =
    Checksum.entries.any { getSimpleName().endsWith(".${it.extension}") }

/** Tells the index that [location] of [repository] was written, unless the format says the write is not an artifact. */
internal fun ArtifactIndexSink.indexChanged(repository: String, location: Location, pathRules: PathRules) {
    pathRules.indexPath(location)?.let { changed(repository, it) }
}
