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

package net.onelitefeather.ingot.support

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The tier of a plugin in the support information. The sources are plain functions, so no test touches the running server. */
internal class SupportPluginTierTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC)

    private fun sources(plugins: List<PluginFact>) = SupportSources(
        instance = "ingot-test",
        clock = clock,
        version = "1.2.3",
        jvm = { JvmFacts(vendor = "Eclipse Adoptium", version = "21.0.4", uptimeMillis = 5_000, heapUsedMb = 120.5, heapMaxMb = 512, processors = 4, threads = 30) },
        operatingSystem = { OperatingSystemFacts(name = "Linux", version = "6.8", architecture = "amd64", container = true) },
        plugins = { plugins },
    )

    @Test
    fun `should write the tier of each plugin into plugins json next to the existing fields`() {
        // given: a plugin with a declared tier and its dependency
        val collector = SupportInformationCollector(sources(listOf(PluginFact(name = "metrics", version = "1.2.3", dependencies = listOf("status"), tier = "official"))))

        // when: the support bundle is collected
        val plugins = DEFAULT_OBJECT_MAPPER.readTree(collector.collect().files.getValue("plugins.json"))

        // then: the plugin keeps its name, version and dependencies, and it carries its tier
        val plugin = plugins.single()
        assertThat(plugin.get("name").asText()).describedAs("name in plugins.json").isEqualTo("metrics")
        assertThat(plugin.get("version").asText()).describedAs("version in plugins.json").isEqualTo("1.2.3")
        assertThat(plugin.get("dependencies").map { it.asText() }).describedAs("dependencies in plugins.json").containsExactly("status")
        assertThat(plugin.get("tier").asText()).describedAs("tier in plugins.json").isEqualTo("official")
    }

    @Test
    fun `should carry the tier of each plugin into the system information`() {
        // given: a plugin with a declared tier
        val collector = SupportInformationCollector(sources(listOf(PluginFact(name = "checksum", version = "1.0.0", dependencies = emptyList(), tier = "official"))))

        // when: the system information is collected
        val info = collector.collect().systemInfo

        // then: the plugin of the page has the same tier
        assertThat(info.plugins.single().tier).describedAs("tier of the plugin in the system information").isEqualTo("official")
    }

    @Test
    fun `should read community for a plugin fact that names no tier`() {
        // when: a plugin fact is created the way existing callers create it
        val fact = PluginFact(name = "metrics", version = "1.2.3", dependencies = emptyList())

        // then: the tier defaults to community
        assertThat(fact.tier).describedAs("default tier of a plugin fact").isEqualTo("community")
    }

}
