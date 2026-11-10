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
import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.infrastructure.LocalSharedConfigurationProvider
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.SharedConfigurationChangedEvent
import com.reposilite.status.FailureFacade
import io.javalin.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.asSuccess
import panda.std.reactive.mutableReference
import java.nio.file.Path

/**
 * With the reload switch on, the mounted file is the owner of its domains: a write through the facade is refused with a
 * conflict, the file and the settings stay as they are, and no change event is emitted. Every test builds its own facade and
 * temporary directory, and reads the events it needs from its own [Extensions].
 */
internal class SharedConfigurationReadOnlyTest {

    @TempDir
    lateinit var workingDirectory: Path

    @Doc(title = "Probe", description = "Probe settings")
    data class ProbeSettings(val property: String = "value") : SharedSettings

    private val journalist = InMemoryLogger()
    private val auditEvents = mutableListOf<AuditEvent>()
    private val changedEvents = mutableListOf<SharedConfigurationChangedEvent>()
    private val extensions = Extensions(journalist).also { extensions ->
        extensions.registerEvent(AuditEvent::class.java) { auditEvents.add(it) }
        extensions.registerEvent(SharedConfigurationChangedEvent::class.java) { changedEvents.add(it) }
    }

    private val file: Path by lazy { workingDirectory.resolve(FILE_NAME) }

    private fun facade(reloadEnabled: Boolean): SharedConfigurationFacade =
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(ProbeSettings::class.java to mutableReference(ProbeSettings()))),
            sharedConfigurationProvider = LocalSharedConfigurationProvider(
                journalist = journalist,
                workingDirectory = workingDirectory,
                configurationFile = Path.of(FILE_NAME),
                reloadEnabled = reloadEnabled,
            ),
            extensions = extensions,
        )

    /** Writes the document and loads it the way the start of the instance does. */
    private fun SharedConfigurationFacade.start(content: String) {
        file.toFile().writeText(content)
        loadSharedSettingsFromString(fetchConfiguration())
    }

    private fun SharedConfigurationFacade.probe(): String =
        getDomainSettings<ProbeSettings>().get().property

    private fun document(probe: String): String =
        """{ "probe": { "property": "$probe" } }"""

    @Test
    fun `a write to a file managed domain through updateDomain is refused with a conflict`() {
        // given: a file loaded at start with the switch on
        val facade = facade(reloadEnabled = true)
        facade.start(document("from-file"))

        // when: a plugin writes the domain through the facade
        val result = facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(property = "from-api").asSuccess() }

        // then: the write is refused as a conflict that names the file
        assertThat(result).describedAs("result of a write to a known domain").isNotNull()
        assertThat(result!!.isErr).describedAs("the write is refused").isTrue()
        assertThat(result.error.status).describedAs("status of the refused write").isEqualTo(HttpStatus.CONFLICT.code)
        assertThat(result.error.message).describedAs("message of the refused write").contains(FILE_NAME)
    }

    @Test
    fun `a refused write through updateDomain leaves the file bytes unchanged`() {
        // given: a file loaded at start with the switch on
        val facade = facade(reloadEnabled = true)
        facade.start(document("from-file"))
        val before = file.toFile().readText()

        // when: a write to the domain is refused
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(property = "from-api").asSuccess() }

        // then: the file is byte for byte the same
        assertThat(file.toFile().readText()).describedAs("file after a refused write").isEqualTo(before)
    }

    @Test
    fun `a refused write through updateDomain leaves the settings unchanged`() {
        // given: a file loaded at start with the switch on
        val facade = facade(reloadEnabled = true)
        facade.start(document("from-file"))

        // when: a write to the domain is refused
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(property = "from-api").asSuccess() }

        // then: the settings in force are the ones from the file
        assertThat(facade.probe()).describedAs("probe after a refused write").isEqualTo("from-file")
    }

    @Test
    fun `a refused write through updateSharedSettings returns an error and writes nothing`() {
        // given: a file loaded at start with the switch on
        val facade = facade(reloadEnabled = true)
        facade.start(document("from-file"))
        val before = file.toFile().readText()

        // when: a plugin writes the domain through updateSharedSettings
        val result = facade.updateSharedSettings("probe", ProbeSettings(property = "from-plugin"))

        // then: an error is returned, the file is unchanged and the settings are the ones from the file
        assertThat(result).describedAs("result of a plugin write to a known domain").isNotNull()
        assertThat(result!!.isErr).describedAs("the plugin write is refused").isTrue()
        assertThat(file.toFile().readText()).describedAs("file after a refused plugin write").isEqualTo(before)
        assertThat(facade.probe()).describedAs("probe after a refused plugin write").isEqualTo("from-file")
    }

    @Test
    fun `a refused write records no settings change event`() {
        // given: a file loaded at start with the switch on
        val facade = facade(reloadEnabled = true)
        facade.start(document("from-file"))

        // when: a write to the domain is refused
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(property = "from-api").asSuccess() }
        facade.updateSharedSettings("probe", ProbeSettings(property = "from-plugin"))

        // then: no audit event and no configuration change event is emitted
        assertThat(auditEvents).describedAs("audit events of refused writes").isEmpty()
        assertThat(changedEvents).describedAs("change events of refused writes").isEmpty()
    }

    @Test
    fun `an unknown domain is still not found under the switch`() {
        // given: a file loaded at start with the switch on
        val facade = facade(reloadEnabled = true)
        facade.start(document("from-file"))

        // when: a write to a domain that does not exist is attempted
        val result = facade.updateDomain<ProbeSettings>("unknown", ifMatch = null) { ProbeSettings().asSuccess() }

        // then: the domain is reported as missing, not as managed by the file
        assertThat(result).describedAs("result for an unknown domain").isNull()
    }

    @Test
    fun `a write is stored in the file when the switch is off`() {
        // given: a file loaded at start with the switch off
        val facade = facade(reloadEnabled = false)
        facade.start(document("from-file"))

        // when: the domain is written through the facade
        val result = facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(property = "from-api").asSuccess() }

        // then: the write succeeds and the file holds the new value, as it always did
        assertThat(result!!.isOk).describedAs("the write succeeds with the switch off").isTrue()
        assertThat(file.toFile().readText()).describedAs("file after the write").contains("from-api")
    }

    private companion object {
        const val FILE_NAME = "configuration.shared.json"
    }
}
