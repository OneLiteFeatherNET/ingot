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

/**
 * The order of the groups of a search (artifact search spec, 4.3), for the backends that sort in memory. It mirrors the
 * `ORDER BY` of the database engine: the sort key, then groupId and artifactId in the request's direction, then the version in
 * the opposite direction, so the newest version of a tie comes first in the default order. The comparison is on the
 * lower-cased coordinates, as the database compares them.
 */
object GroupOrder {

    /** The grouped columns of one group: the lower-cased coordinates, the newest modification and the summed size. */
    data class Key(
        val groupLc: String,
        val artifactLc: String,
        val versionLc: String,
        val lastModified: Long,
        val sizeBytes: Long,
    )

    fun comparator(sort: SearchSort, order: SearchOrder): Comparator<Key> {
        val direction = if (order == SearchOrder.DESC) -1 else 1
        val tie = -direction
        val primary: Comparator<Key> = when (sort) {
            SearchSort.GROUP_ID -> Comparator { a, b -> a.groupLc.compareTo(b.groupLc) }
            SearchSort.ARTIFACT_ID -> Comparator { a, b -> a.artifactLc.compareTo(b.artifactLc) }
            SearchSort.VERSION -> Comparator { a, b -> a.versionLc.compareTo(b.versionLc) }
            SearchSort.MODIFIED -> Comparator { a, b -> a.lastModified.compareTo(b.lastModified) }
            SearchSort.SIZE -> Comparator { a, b -> a.sizeBytes.compareTo(b.sizeBytes) }
        }
        return Comparator { a, b ->
            listOf(
                primary.compare(a, b) * direction,
                a.groupLc.compareTo(b.groupLc) * direction,
                a.artifactLc.compareTo(b.artifactLc) * direction,
                a.versionLc.compareTo(b.versionLc) * tie,
            ).firstOrNull { it != 0 } ?: 0
        }
    }

}
