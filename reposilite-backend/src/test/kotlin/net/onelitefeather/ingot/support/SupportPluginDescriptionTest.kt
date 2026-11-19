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
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.PluginTier
import com.reposilite.plugin.api.ReposilitePlugin
import net.onelitefeather.ingot.plugin.tier.PluginOrigin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The description of a plugin in the system information and the support bundle. Plain functions and fixtures only, no server. */
internal class SupportPluginDescriptionTest {

    @Plugin(name = "described", description = "  Verifies checksums\nof uploaded artifacts  ")
    class DescribedFixture : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Plugin(name = "undescribed")
    class UndescribedFixture : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    private val clock = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC)

    private fun metadataOf(type: Class<*>): Plugin = type.getAnnotation(Plugin::class.java)

    private fun sources(plugins: List<PluginFact>, localValues: Map<String, String?> = emptyMap()) = SupportSources(
        instance = "ingot-test",
        clock = clock,
        version = "1.2.3",
        jvm = { JvmFacts(vendor = "Eclipse Adoptium", version = "21.0.4", uptimeMillis = 5_000, heapUsedMb = 120.5, heapMaxMb = 512, processors = 4, threads = 30) },
        operatingSystem = { OperatingSystemFacts(name = "Linux", version = "6.8", architecture = "amd64", container = true) },
        localValues = { localValues },
        plugins = { plugins },
    )

    private val described = PluginFact(name = "checksum", version = "1.0.0", dependencies = emptyList(), description = "Verifies checksums of uploaded artifacts")
    private val undescribed = PluginFact(name = "metrics", version = "1.2.3", dependencies = listOf("status"))

    /** `plugins.json` of a single plugin without a description, exactly as it was written before the description existed. */
    private val undescribedPluginsJson = """
        [ {
          "name" : "metrics",
          "version" : "1.2.3",
          "dependencies" : [ "status" ],
          "tier" : "community",
          "origin" : "plugins-directory"
        } ]
    """.trimIndent()

    @Test
    fun `should read the normalised description from the plugin annotation`() {
        // when: the plugin fact is built from a plugin that sets a description with whitespace and a line break
        val fact = pluginFact(metadataOf(DescribedFixture::class.java), PluginTier.COMMUNITY, PluginOrigin.PLUGINS_DIRECTORY)

        // then: the fact carries the stored plain text
        assertThat(fact.description).describedAs("description of the plugin fact").isEqualTo("Verifies checksumsof uploaded artifacts")
    }

    @Test
    fun `should read the empty text for a plugin annotation without a description`() {
        // when: the plugin fact is built from a plugin that sets no description
        val fact = pluginFact(metadataOf(UndescribedFixture::class.java), PluginTier.COMMUNITY, PluginOrigin.PLUGINS_DIRECTORY)

        // then: the description is the empty text
        assertThat(fact.description).describedAs("description of a plugin fact without one").isEmpty()
    }

    @Test
    fun `should write the description into plugins json and system info json`() {
        // given: a plugin with a description
        val snapshot = SupportInformationCollector(sources(listOf(described, undescribed))).collect()

        // when: both files are read
        val plugins = DEFAULT_OBJECT_MAPPER.readTree(snapshot.files.getValue("plugins.json"))
        val systemInfo = DEFAULT_OBJECT_MAPPER.readTree(snapshot.files.getValue("system-info.json"))

        // then: the described plugin carries its description in both
        assertThat(plugins[0].get("description")?.asText()).describedAs("description in plugins.json").isEqualTo("Verifies checksums of uploaded artifacts")
        assertThat(systemInfo.get("plugins")[0].get("description")?.asText()).describedAs("description in system-info.json").isEqualTo("Verifies checksums of uploaded artifacts")
        assertThat(snapshot.systemInfo.plugins[0].description).describedAs("description of the page").isEqualTo("Verifies checksums of uploaded artifacts")
    }

    @Test
    fun `should leave a plugin without a description unchanged in both files`() {
        // given: a plugin with a description next to one without
        val snapshot = SupportInformationCollector(sources(listOf(described, undescribed))).collect()

        // when: both files are read
        val plugins = DEFAULT_OBJECT_MAPPER.readTree(snapshot.files.getValue("plugins.json"))
        val systemInfo = DEFAULT_OBJECT_MAPPER.readTree(snapshot.files.getValue("system-info.json"))

        // then: the second plugin is the entry written before the description existed, with no description key
        val before = DEFAULT_OBJECT_MAPPER.readTree(undescribedPluginsJson).single()
        assertThat(plugins[1]).describedAs("plugin without a description in plugins.json").isEqualTo(before)
        assertThat(systemInfo.get("plugins")[1]).describedAs("plugin without a description in system-info.json").isEqualTo(before)
        assertThat(snapshot.systemInfo.plugins[1].description).describedAs("description of the page without one").isNull()
    }

    @Test
    fun `should write plugins json byte for byte as before when no plugin has a description`() {
        // given: only a plugin without a description
        val snapshot = SupportInformationCollector(sources(listOf(undescribed))).collect()

        // when: plugins.json is read as text
        val written = String(snapshot.files.getValue("plugins.json"), Charsets.UTF_8)

        // then: it is the file written before the description existed
        assertThat(written).describedAs("plugins.json without any description").isEqualTo(undescribedPluginsJson)
    }

    @Test
    fun `should mask a secret in a description and still serve the bundle`() {
        // given: a known secret, and a plugin whose description contains it
        val localValues = mapOf<String, String?>("keyPassword" to "hunter2-secret")
        val leaking = described.copy(description = "Signs with hunter2-secret")
        val baseline = SupportInformationCollector(sources(listOf(described), localValues)).collect()

        // when: the bundle is collected
        val snapshot = SupportInformationCollector(sources(listOf(leaking), localValues)).collect()

        // then: the secret is masked in both files, the guard finds nothing, and the mask is counted in the manifest
        val plugins = DEFAULT_OBJECT_MAPPER.readTree(snapshot.files.getValue("plugins.json"))
        val manifest = DEFAULT_OBJECT_MAPPER.readTree(snapshot.files.getValue("manifest.json"))
        assertThat(plugins[0].get("description")?.asText()).describedAs("masked description in plugins.json").isEqualTo("Signs with ${Redaction.REDACTED}")
        assertThat(snapshot.systemInfo.plugins[0].description).describedAs("masked description of the page").isEqualTo("Signs with ${Redaction.REDACTED}")
        assertThat(snapshot.guard.leaks(snapshot.files)).describedAs("entries the final check refuses").isEmpty()
        assertThat(manifest.get("redactions").asInt()).describedAs("redactions in the manifest").isEqualTo(baseline.redactions + 1)
    }

}
