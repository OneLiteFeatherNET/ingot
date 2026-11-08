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

import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.reposilite.configuration.SharedReposiliteConfigurationFacadeTest.TestSettings
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.mutableReference

/**
 * The scheduled poll and the cluster bus both call [SharedConfigurationFacade.reloadIfChanged]. The provider is scripted, so
 * the test decides when a newer revision exists and never waits for the schedule.
 */
internal class SharedConfigurationPollTest {

    /** A provider whose stored document and update flag the test sets by hand. */
    private class ScriptedProvider(var content: String, var updateRequired: Boolean) : SharedConfigurationProvider {

        override fun updateConfiguration(content: String) {
            this.content = content
        }

        override fun fetchConfiguration(): String =
            content

        override fun isUpdateRequired(): Boolean =
            updateRequired

        override fun isMutable(): Boolean =
            true

        override fun name(): String =
            "scripted"
    }

    private fun facadeOver(provider: SharedConfigurationProvider): SharedConfigurationFacade {
        val logger = InMemoryLogger()
        return SharedConfigurationFacade(
            journalist = logger,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(logger),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(TestSettings::class.java to mutableReference(TestSettings()))),
            sharedConfigurationProvider = provider,
            extensions = Extensions(logger),
        )
    }

    private fun document(property: String): String =
        """{ "test": { "property": "$property" } }"""

    @Test
    fun `reloadIfChanged loads the stored document when the provider reports a newer revision`() {
        // given: a provider that stores a newer document than the one loaded so far
        val provider = ScriptedProvider(content = document("stored-later"), updateRequired = true)
        val facade = facadeOver(provider)

        // when: the poll runs
        val reloaded = facade.reloadIfChanged()

        // then: the newer document is applied
        assertThat(reloaded).describedAs("a newer revision was loaded").isTrue()
        assertThat(facade.getDomainSettings<TestSettings>().get().property).isEqualTo("stored-later")
    }

    @Test
    fun `reloadIfChanged keeps the current settings when the provider reports no newer revision`() {
        // given: a provider whose stored document is not newer than the one loaded so far
        val provider = ScriptedProvider(content = document("stored-later"), updateRequired = false)
        val facade = facadeOver(provider)

        // when: the poll runs
        val reloaded = facade.reloadIfChanged()

        // then: nothing is loaded and the settings keep their default
        assertThat(reloaded).describedAs("no newer revision").isFalse()
        assertThat(facade.getDomainSettings<TestSettings>().get().property).isEqualTo("value")
    }
}
