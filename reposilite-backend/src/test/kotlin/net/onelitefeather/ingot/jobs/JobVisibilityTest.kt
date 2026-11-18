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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class JobVisibilityTest {

    private val index = FakeJobSource(
        id = "index",
        kinds = setOf("index.rebuild", "index.verify"),
        requiredNode = MAINTENANCE_NODE,
        runs = listOf(run("index", "1", BASE_TIME, kind = "index.verify")),
    )

    private val migration = FakeJobSource(
        id = "migration",
        kinds = setOf("migration.nexus"),
        requiredNode = ADMIN_NODE,
        runs = listOf(run("migration", "7", BASE_TIME.plusSeconds(1), kind = "migration.nexus")),
    )

    private val aggregator = JobAggregator(registryOf(index, migration))

    @Test
    fun `a source the visitor may not read is absent from the items, the kinds and the detail`() {
        val visitor = accessTo(MAINTENANCE_NODE)

        val page = aggregator.page(JobQuery(), visitor)

        assertThat(page.items.map { it.id })
            .describedAs("runs of an unreadable source must not be listed")
            .containsExactly("index:1")
        assertThat(page.kinds)
            .describedAs("kinds of an unreadable source must not be offered")
            .containsExactlyInAnyOrder("index.rebuild", "index.verify")
        assertThat(aggregator.detail("migration:7", visitor))
            .describedAs("a run of an unreadable source must look like a missing run")
            .isNull()
    }

    @Test
    fun `a visitor who reads no source gets the forbidden answer that names the area`() {
        assertThatThrownBy { aggregator.page(JobQuery(), accessTo()) }
            .describedAs("a visitor with no readable source must be refused")
            .isInstanceOf(JobsForbiddenException::class.java)
            .hasMessageContaining("maintenance")
    }

    @Test
    fun `a filter on an unknown kind answers an empty page without an error`() {
        val page = aggregator.page(JobQuery(kinds = setOf("unknown.kind")), accessTo(MAINTENANCE_NODE, ADMIN_NODE))

        assertThat(page.items).describedAs("an unknown kind matches no run").isEmpty()
        assertThat(page.sourceErrors).describedAs("an unknown kind is not an error").isEmpty()
    }
}
