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

package net.onelitefeather.ingot.search.http

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The bulk planner of the search plugins: limits, order and the items that cannot be bulk actions. */
internal class NdjsonBulkTest {

    private fun lines(item: String): List<String> = listOf("""{"index":{"_id":"$item"}}""", """{"id":"$item"}""")

    @Test
    fun `should start a new body when the action limit is reached`() {
        // when: three items are planned with an action limit of two
        val steps = NdjsonBulk.plan(listOf("a", "b", "c"), maxActions = 2) { lines(it) }

        // then: the first body holds two actions and the second holds the last one
        val bulks = steps.filterIsInstance<BulkStep.Bulk>()
        assertThat(bulks.map { it.actions }).`as`("actions per body").containsExactly(2, 1)
    }

    @Test
    fun `should start a new body when the byte limit would be exceeded`() {
        // given: a byte limit that fits one action of two lines but not two
        val one = lines("a").sumOf { it.toByteArray(Charsets.UTF_8).size + 1 }

        // when: two items are planned
        val steps = NdjsonBulk.plan(listOf("a", "b"), maxActions = 500, maxBytes = one) { lines(it) }

        // then: each item has a body of its own
        assertThat(steps.filterIsInstance<BulkStep.Bulk>().map { it.actions })
            .`as`("actions per body")
            .containsExactly(1, 1)
    }

    @Test
    fun `should send an item that is not a bulk action on its own and keep the order around it`() {
        // when: the second item is not a bulk action
        val steps = NdjsonBulk.plan(listOf("a", "b", "c"), maxActions = 500) { item -> if (item == "b") null else lines(item) }

        // then: the bodies before and after it are split at the item, which is a step of its own
        assertThat(steps).`as`("steps in order").containsExactly(
            BulkStep.Bulk(lines("a").joinToString("") { "$it\n" }, 1),
            BulkStep.Single("b"),
            BulkStep.Bulk(lines("c").joinToString("") { "$it\n" }, 1),
        )
    }

}
