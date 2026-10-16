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

/** A column of the search index with a length limit (artifact search spec, section 2.8). */
enum class SearchColumn(val limit: Int) {
    REPOSITORY(128),
    PATH(512),
    GROUP_ID(255),
    ARTIFACT_ID(255),
    VERSION(255),
    CLASSIFIER(255),
    EXTENSION(64),
}

/**
 * One stored file with the coordinates derived from its repository path (one row per file,
 * section 2.1). [classifier] is null for the main artifact. [version] keeps the directory name,
 * so every timestamped build of a snapshot groups under `1.0-SNAPSHOT`.
 */
data class ArtifactFileRecord(
    val repository: String,
    val path: String,
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String?,
    val extension: String,
    val snapshot: Boolean,
)
