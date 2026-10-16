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

/** Where a stored file came from: a hosted deployment, or a copy a proxy repository cached (artifact search spec, 2.4). */
enum class Origin { HOSTED, MIRROR }

/** The origin of a version directory. [sourceHost] is only set for a mirror copy whose host is known. */
data class Provenance(val origin: Origin, val sourceHost: String?) {
    companion object {
        val HOSTED = Provenance(Origin.HOSTED, null)
    }
}

/**
 * One row of `search_artifact`, as the index writes it: the parsed coordinates of a stored file together with what the
 * storage and the mirror ledger say about it. Times are epoch milliseconds, UTC.
 */
data class IndexRow(
    val file: ArtifactFileRecord,
    val provenance: Provenance,
    val sizeBytes: Long,
    val lastModified: Long,
    val sha1: String?,
    val sha256: String?,
    val indexedAt: Long,
)
