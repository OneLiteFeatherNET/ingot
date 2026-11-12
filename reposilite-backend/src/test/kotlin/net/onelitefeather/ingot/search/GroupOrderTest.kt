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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GroupOrderTest {

    @Test
    fun `should order equal artifacts by the newest version first in the default order`() {
        val keys = listOf(
            key("com.b", "app", "1.0"),
            key("com.a", "lib", "1.0"),
            key("com.a", "lib", "2.0"),
            key("com.a", "app", "1.0"),
        )

        val sorted = keys.sortedWith(GroupOrder.comparator(SearchSort.GROUP_ID, SearchOrder.ASC))

        assertThat(sorted.map { "${it.groupLc}:${it.artifactLc}:${it.versionLc}" })
            .describedAs("default order is group, artifact, then the newest version of a tie first")
            .containsExactly("com.a:app:1.0", "com.a:lib:2.0", "com.a:lib:1.0", "com.b:app:1.0")
    }

    @Test
    fun `should order by the summed size descending and break ties by the group in the same direction`() {
        val keys = listOf(
            key("x", "a", "1", size = 10),
            key("y", "a", "1", size = 30),
            key("z", "a", "1", size = 10),
        )

        val sorted = keys.sortedWith(GroupOrder.comparator(SearchSort.SIZE, SearchOrder.DESC))

        assertThat(sorted.map { it.groupLc })
            .describedAs("the largest size comes first, and equal sizes follow the database tie order, group descending")
            .containsExactly("y", "z", "x")
    }

    private fun key(group: String, artifact: String, version: String, size: Long = 0L) =
        GroupOrder.Key(groupLc = group, artifactLc = artifact, versionLc = version, lastModified = 0L, sizeBytes = size)

}
