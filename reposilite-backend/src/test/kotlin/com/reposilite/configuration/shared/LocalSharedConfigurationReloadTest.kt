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
import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.api.ValidatedSettings
import com.reposilite.configuration.shared.infrastructure.LocalSharedConfigurationProvider
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.mutableReference
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The file source of shared settings with the reload switch on or off. The test plays the start of the instance, and then
 * calls [SharedConfigurationFacade.reloadIfChanged] directly, so no poll is waited for and no clock is read.
 */
internal class LocalSharedConfigurationReloadTest {

    @TempDir
    lateinit var workingDirectory: Path

    @Doc(title = "Probe", description = "Probe settings")
    data class ProbeSettings(val property: String = "value") : SharedSettings

    @Doc(title = "Guarded", description = "Settings with a rule")
    data class GuardedSettings(val property: String = "ok") : ValidatedSettings {
        override fun violations(): List<String> =
            if (property == "invalid") listOf("property: must not be invalid") else emptyList()
    }

    private val journalist = InMemoryLogger()

    private val file: Path by lazy { workingDirectory.resolve(FILE_NAME) }

    private fun facade(reloadEnabled: Boolean): SharedConfigurationFacade =
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(
                mapOf(
                    ProbeSettings::class.java to mutableReference(ProbeSettings()),
                    GuardedSettings::class.java to mutableReference(GuardedSettings()),
                )
            ),
            sharedConfigurationProvider = LocalSharedConfigurationProvider(
                journalist = journalist,
                workingDirectory = workingDirectory,
                configurationFile = Path.of(FILE_NAME),
                reloadEnabled = reloadEnabled,
            ),
            extensions = Extensions(journalist),
        )

    /** Writes the document and loads it the way the start of the instance does. */
    private fun SharedConfigurationFacade.start(content: String) {
        file.toFile().writeText(content)
        loadSharedSettingsFromString(fetchConfiguration())
    }

    private fun document(probe: String, guarded: String = "ok"): String =
        """{ "probe": { "property": "$probe" }, "guarded": { "property": "$guarded" } }"""

    private fun SharedConfigurationFacade.probe(): String =
        getDomainSettings<ProbeSettings>().get().property

    private fun errorLines(): List<String> =
        journalist.messages
            .filter { it.key == Channel.ERROR }
            .map { it.value }

    @Test
    fun `with the switch off a changed file is not reported as an update`() {
        // given: a file loaded at start with the switch off
        val facade = facade(reloadEnabled = false)
        facade.start(document("first"))

        // when: the file is changed on disk
        file.toFile().writeText(document("second"))

        // then: the change is not reported
        assertThat(facade.isUpdateRequired()).describedAs("update reported with the switch off").isFalse()
    }

    @Test
    fun `with the switch off the file source is not mutable`() {
        // given: a file source with the switch off
        val facade = facade(reloadEnabled = false)

        // when, then: the source is not mutable, so no poll is scheduled for it
        assertThat(facade.isMutable()).describedAs("mutability with the switch off").isFalse()
    }

    @Test
    fun `with the switch on a changed file is reported as an update`() {
        // given: a file loaded at start with the switch on
        val facade = facade(reloadEnabled = true)
        facade.start(document("first"))

        // when: the file is changed on disk
        file.toFile().writeText(document("second"))

        // then: the change is reported
        assertThat(facade.isUpdateRequired()).describedAs("update reported with the switch on").isTrue()
    }

    @Test
    fun `reloadIfChanged applies a changed file with the switch on`() {
        // given: a file loaded at start with the switch on, and then changed
        val facade = facade(reloadEnabled = true)
        facade.start(document("first"))
        file.toFile().writeText(document("second"))

        // when: the reload runs
        val reloaded = facade.reloadIfChanged()

        // then: the new value is in force
        assertThat(reloaded).describedAs("a changed file is reloaded").isTrue()
        assertThat(facade.probe()).describedAs("probe after reload").isEqualTo("second")
    }

    @Test
    fun `reloadIfChanged does not reload an unchanged file`() {
        // given: a file loaded at start with the switch on and not changed since
        val facade = facade(reloadEnabled = true)
        facade.start(document("first"))

        // when: the reload runs
        val reloaded = facade.reloadIfChanged()

        // then: nothing is loaded
        assertThat(reloaded).describedAs("an unchanged file is not reloaded").isFalse()
    }

    @Test
    fun `a rewrite that restores the old modification time is still applied`() {
        // given: a file loaded at start, then rewritten and given its old modification time back
        val facade = facade(reloadEnabled = true)
        facade.start(document("first"))
        val originalTime = Files.getLastModifiedTime(file)
        file.toFile().writeText(document("second"))
        Files.setLastModifiedTime(file, originalTime)

        // when: the reload runs
        facade.reloadIfChanged()

        // then: the content is compared, not the time, so the new value is applied
        assertThat(facade.probe()).describedAs("probe after a rewrite with the old time").isEqualTo("second")
    }

    @Test
    fun `a change made through a symlink swap of the data directory is applied`() {
        // given: a file that is a symlink into a data directory, as a mounted ConfigMap is laid out
        val firstRevision = Files.createDirectory(workingDirectory.resolve("..v1"))
        firstRevision.resolve(FILE_NAME).toFile().writeText(document("first"))
        Files.createSymbolicLink(workingDirectory.resolve("..data"), Path.of("..v1"))
        Files.createSymbolicLink(file, Path.of("..data", FILE_NAME))

        val facade = facade(reloadEnabled = true)
        facade.start(document("first"))

        // when: the data directory is swapped to a new revision, the way the kubelet updates it
        val secondRevision = Files.createDirectory(workingDirectory.resolve("..v2"))
        secondRevision.resolve(FILE_NAME).toFile().writeText(document("second"))
        val pointer = Files.createSymbolicLink(workingDirectory.resolve("..data_next"), Path.of("..v2"))
        Files.move(pointer, workingDirectory.resolve("..data"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        facade.reloadIfChanged()

        // then: the content of the new target is applied
        assertThat(facade.probe()).describedAs("probe after the symlink swap").isEqualTo("second")
    }

    @Test
    fun `a document that does not parse keeps the settings and logs one error across two polls`() {
        // given: a file loaded at start, then replaced by content that is not JSON
        val facade = facade(reloadEnabled = true)
        facade.start(document("first"))
        file.toFile().writeText("{ probe: ")

        // when: two polls run
        facade.reloadIfChanged()
        facade.reloadIfChanged()

        // then: the settings keep their previous value and exactly one error names the source
        assertThat(facade.probe()).describedAs("probe after an unreadable document").isEqualTo("first")
        assertThat(errorLines()).describedAs("error lines for one unreadable content").hasSize(1)
        assertThat(errorLines().single()).describedAs("error names the file source").contains(FILE_NAME)
    }

    @Test
    fun `a corrected document is applied after an unreadable one`() {
        // given: a file that was unreadable at the last poll
        val facade = facade(reloadEnabled = true)
        facade.start(document("first"))
        file.toFile().writeText("{ probe: ")
        facade.reloadIfChanged()

        // when: the corrected document is mounted and the next poll runs
        file.toFile().writeText(document("corrected"))
        val reloaded = facade.reloadIfChanged()

        // then: the valid document is applied
        assertThat(reloaded).describedAs("the corrected document is reloaded").isTrue()
        assertThat(facade.probe()).describedAs("probe after the correction").isEqualTo("corrected")
    }

    @Test
    fun `a domain that fails validation keeps its old value while the other domains load`() {
        // given: a loaded file with a valid value in both domains
        val facade = facade(reloadEnabled = true)
        facade.start(document("first", guarded = "ok"))

        // when: the file is changed so that one domain is valid and the other is rejected by its rule
        file.toFile().writeText(document("second", guarded = "invalid"))
        facade.reloadIfChanged()

        // then: the valid domain is applied and the rejected one keeps its previous value
        assertThat(facade.probe()).describedAs("probe after the reload").isEqualTo("second")
        assertThat(facade.getDomainSettings<GuardedSettings>().get().property)
            .describedAs("guarded keeps its previous value")
            .isEqualTo("ok")
    }

    private companion object {
        const val FILE_NAME = "configuration.shared.json"
    }
}
