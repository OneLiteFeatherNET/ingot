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
package net.onelitefeather.ingot.jobs

import com.reposilite.plugin.api.jobs.JobQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JobPagingTest {

    private val index = FakeJobSource(
        id = "index",
        kinds = setOf("index.verify"),
        runs = listOf(
            run("index", "1", BASE_TIME.plusSeconds(6), kind = "index.verify"),
            run("index", "2", BASE_TIME.plusSeconds(8), kind = "index.verify"),
            run("index", "3", BASE_TIME.plusSeconds(10), kind = "index.verify"),
        ),
    )

    private val cleanup = FakeJobSource(
        id = "cleanup",
        kinds = setOf("cleanup.policy"),
        runs = listOf(
            run("cleanup", "1", BASE_TIME.plusSeconds(5), kind = "cleanup.policy"),
            run("cleanup", "2", BASE_TIME.plusSeconds(7), kind = "cleanup.policy"),
            run("cleanup", "3", BASE_TIME.plusSeconds(9), kind = "cleanup.policy"),
        ),
    )

    @Test
    fun `the first page merges the runs of two sources by start time`() {
        val page = JobAggregator(registryOf(index, cleanup)).page(JobQuery(), EVERY_NODE, size = 4)

        assertThat(page.items.map { it.id })
            .describedAs("runs of both sources must be merged newest first")
            .containsExactly("index:3", "cleanup:3", "index:2", "cleanup:2")
    }

    @Test
    fun `following the cursor of each page lists every older run once`() {
        val aggregator = JobAggregator(registryOf(index, cleanup))
        val collected = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = aggregator.page(JobQuery(), EVERY_NODE, size = 2, cursor = cursor)
            collected += page.items.map { it.id }
            cursor = page.nextCursor
        } while (cursor != null)

        assertThat(collected)
            .describedAs("the cursor must continue with the next older run and never repeat one")
            .containsExactly("index:3", "cleanup:3", "index:2", "cleanup:2", "index:1", "cleanup:1")
    }

    @Test
    fun `a size above one hundred is answered with one hundred items`() {
        val many = FakeJobSource(
            id = "index",
            kinds = setOf("index.verify"),
            runs = (1..150).map { run("index", "$it", BASE_TIME.plusSeconds(it.toLong()), kind = "index.verify") },
        )

        val page = JobAggregator(registryOf(many)).page(JobQuery(), EVERY_NODE, size = 500)

        assertThat(page.items).describedAs("a page is capped at one hundred runs").hasSize(100)
        assertThat(many.requestedLimits).describedAs("the source is asked for the capped size").containsExactly(100)
    }

    @Test
    fun `a source that throws is named in the source errors while the other runs are listed`() {
        val broken = FakeJobSource(id = "migration", failing = true)

        val page = JobAggregator(registryOf(index, broken)).page(JobQuery(), EVERY_NODE, size = 10)

        assertThat(page.items.map { it.id })
            .describedAs("the runs of the healthy source must still be listed")
            .containsExactly("index:3", "index:2", "index:1")
        assertThat(page.sourceErrors)
            .describedAs("the failing source must be named")
            .containsExactly("migration")
    }
}
