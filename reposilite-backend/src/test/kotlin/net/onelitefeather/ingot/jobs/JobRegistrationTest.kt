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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JobRegistrationTest {

    @Test
    fun `a source registered on the registration event is listed`() {
        val registry = JobSourceRegistry()
        val source = FakeJobSource(id = "plugin", runs = listOf(run("plugin", "1", BASE_TIME, kind = "plugin.run")))

        JobRegistrationEvent(registry).registry.register(source)
        registry.seal()

        val page = JobAggregator(registry).page(JobQuery(), EVERY_NODE)

        assertThat(page.items.map { it.id })
            .describedAs("a source registered during the event must be listed")
            .containsExactly("plugin:1")
    }

    @Test
    fun `a registration after the event is logged and not listed`() {
        val rejected = mutableListOf<String>()
        val registry = JobSourceRegistry(onLateRegistration = { rejected.add(it) })
        registry.seal()

        registry.register(FakeJobSource(id = "late", runs = listOf(run("late", "1", BASE_TIME, kind = "late.run"))))

        assertThat(rejected)
            .describedAs("a late registration must be reported to the core")
            .containsExactly("late")
        assertThat(JobAggregator(registry).page(JobQuery(), EVERY_NODE).items)
            .describedAs("a late source must not be listed")
            .isEmpty()
    }

    @Test
    fun `the sources of a disabled plugin leave the registry`() {
        val registry = JobSourceRegistry()
        val registration = registry.register(
            FakeJobSource(id = "plugin", runs = listOf(run("plugin", "1", BASE_TIME, kind = "plugin.run")))
        )
        registry.seal()

        registration.close()

        assertThat(JobAggregator(registry).page(JobQuery(), EVERY_NODE).items)
            .describedAs("a closed registration must no longer list its runs")
            .isEmpty()
    }
}
