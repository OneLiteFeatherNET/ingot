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
package net.onelitefeather.ingot.search

import org.apache.maven.artifact.versioning.ComparableVersion

/**
 * Rules over the versions of one artifact that every search backend shares (artifact search spec, 4.6), so the latest version
 * is the same whichever engine answers.
 */
object ArtifactVersions {

    /** The suffix of a snapshot version. */
    const val SNAPSHOT_SUFFIX = "-SNAPSHOT"

    /** The highest non-snapshot version by Maven's ordering; if there is none, the highest snapshot. */
    fun latestOf(versions: List<String>): String? {
        val releases = versions.filterNot { it.endsWith(SNAPSHOT_SUFFIX, ignoreCase = true) }
        return (releases.ifEmpty { versions }).maxByOrNull { ComparableVersion(it) }
    }

}
