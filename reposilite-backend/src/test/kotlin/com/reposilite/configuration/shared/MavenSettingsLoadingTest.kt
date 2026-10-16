/*
 * Copyright (c) 2023 dzikoysk
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

import com.reposilite.configuration.application.ConfigurationComponents
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.application.SharedConfigurationComponents
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.application.MavenSettings
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.MutableReference
import panda.std.reactive.mutableReference
import java.io.File
import java.nio.file.Path

/**
 * Loads the maven domain from a shared configuration document the way the server does at startup,
 * so the validation that runs on update is also the one that guards the file.
 */
internal class MavenSettingsLoadingTest {

    @TempDir
    lateinit var workingDirectory: File

    private lateinit var mavenSettings: MutableReference<SharedSettings>
    private lateinit var sharedConfigurationFacade: SharedConfigurationFacade

    @BeforeEach
    fun prepare() {
        val logger = InMemoryLogger()
        mavenSettings = mutableReference(MavenSettings())
        sharedConfigurationFacade = SharedConfigurationComponents(
            journalist = logger,
            workingDirectory = workingDirectory.toPath(),
            extensions = Extensions(logger),
            sharedConfigurationPath = Path.of("shared.json"),
            failureFacade = FailureFacade(logger),
            configurationFacade = ConfigurationComponents().configurationFacade()
        ).sharedConfigurationFacade(
            sharedSettingsProvider = SharedSettingsProvider(mapOf(MavenSettings::class.java to mavenSettings)),
        )
    }

    @Test
    fun `should load a maven block written before tags existed and keep the defaults`() {
        // given: a shared configuration as it was written before the description and tags were introduced
        val document = """{ "maven": { "repositories": [ { "id": "releases", "visibility": "PUBLIC" } ] } }"""

        // when: the document is loaded at startup
        val result = sharedConfigurationFacade.loadSharedSettingsFromString(document)

        // then: the load succeeds and the new fields take their empty defaults
        assertThat(result.isOk).describedAs("result of loading a legacy maven block: ${result.errorOrNull()}").isTrue
        val repository = currentMavenSettings().repositories.single()
        assertThat(repository.id).isEqualTo("releases")
        assertThat(repository.description).isEmpty()
        assertThat(repository.tags).isEmpty()
    }

    @Test
    fun `should reject a shared configuration with an invalid tag and keep the previous settings`() {
        // given: a shared configuration with a tag that breaks the format
        val document = """{ "maven": { "repositories": [ { "id": "releases", "tags": [ "Team" ] } ] } }"""

        // when: the document is loaded at startup
        val result = sharedConfigurationFacade.loadSharedSettingsFromString(document)

        // then: the load fails with a message naming the repository and the tag
        assertThat(result.isErr).describedAs("result of loading an invalid tag").isTrue
        assertThat(result.errorOrNull()?.message.orEmpty())
            .contains("'releases'")
            .contains("'Team'")

        // and: the settings in use are the defaults, not the rejected document
        assertThat(currentMavenSettings()).isEqualTo(MavenSettings())
    }

    private fun currentMavenSettings(): MavenSettings =
        mavenSettings.get() as MavenSettings

    private fun <T> panda.std.Result<T, out Exception>.errorOrNull(): Exception? =
        if (isErr) error else null

}
