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

package com.reposilite.configuration.shared

import com.fasterxml.jackson.databind.JsonNode
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.configuration.application.ConfigurationComponents
import com.reposilite.configuration.infrastructure.InMemoryConfigurationRepository
import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.infrastructure.LocalSharedConfigurationProvider
import com.reposilite.configuration.shared.infrastructure.RemoteSharedConfigurationProvider
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.asSuccess
import panda.std.reactive.mutableReference
import java.nio.file.Files
import java.nio.file.Path

/**
 * A stored document can hold the settings of a plugin this build does not load. Saving any other domain must keep that row as it
 * was, for the file and for the database alike, so the settings are still there once the plugin is back.
 */
internal class UnclaimedSettingsDomainTest {

    @TempDir
    lateinit var directory: Path

    @Doc(title = "Probe", description = "Probe settings")
    data class ProbeSettings(
        val label: String = "plain",
    ) : SharedSettings

    private val journalist = InMemoryLogger()

    private val unclaimedRow = """{ "endpoint": "https://example.invalid", "retries": 3, "nested": { "flags": [ true, false ], "empty": null } }"""

    private val storedDocument = """{ "probe": { "label": "before" }, "detached": $unclaimedRow }"""

    private fun facade(provider: SharedConfigurationProvider): SharedConfigurationFacade =
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(ProbeSettings::class.java to mutableReference(ProbeSettings()))),
            sharedConfigurationProvider = provider,
            extensions = Extensions(journalist),
        )

    private fun SharedConfigurationFacade.saveProbe(label: String) {
        val result = updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(label = label).asSuccess() }!!
        assertThat(result.isOk).describedAs("the probe domain is saved").isTrue
    }

    private fun row(document: String, name: String): JsonNode? =
        DEFAULT_OBJECT_MAPPER.readTree(document).get(name)

    @Test
    fun `should keep the row of an unclaimed domain in the file when another domain is saved`() {
        // given: a settings file with a row no loaded plugin claims
        Files.writeString(directory.resolve(SHARED_FILE), storedDocument)
        val facade = facade(LocalSharedConfigurationProvider(journalist, directory, Path.of(SHARED_FILE)))
        facade.loadSharedSettingsFromString(facade.fetchConfiguration())

        // when: the claimed domain is saved
        facade.saveProbe("after")

        // then: the claimed domain is written and the unclaimed row is kept unchanged
        val written = Files.readString(directory.resolve(SHARED_FILE))
        assertThat(row(written, "probe")?.get("label")?.asText()).isEqualTo("after")
        assertThat(row(written, "detached")).describedAs("the unclaimed row in the file").isEqualTo(DEFAULT_OBJECT_MAPPER.readTree(unclaimedRow))
    }

    @Test
    fun `should keep the row of an unclaimed domain in the database when another domain is saved`() {
        // given: a stored database document with a row no loaded plugin claims
        val store: ConfigurationFacade = ConfigurationComponents().configurationFacade(configurationRepository = InMemoryConfigurationRepository())
        store.saveConfiguration(REMOTE_CONFIGURATION, storedDocument)
        val facade = facade(RemoteSharedConfigurationProvider(store))
        facade.loadSharedSettingsFromString(facade.fetchConfiguration())

        // when: the claimed domain is saved
        facade.saveProbe("after")

        // then: the claimed domain is written and the unclaimed row is kept unchanged
        val written = facade.fetchConfiguration()
        assertThat(row(written, "probe")?.get("label")?.asText()).isEqualTo("after")
        assertThat(row(written, "detached")).describedAs("the unclaimed row in the database").isEqualTo(DEFAULT_OBJECT_MAPPER.readTree(unclaimedRow))
    }

    private companion object {
        const val SHARED_FILE = "configuration.shared.json"
        const val REMOTE_CONFIGURATION = "remote-shared-configuration"
    }

}
