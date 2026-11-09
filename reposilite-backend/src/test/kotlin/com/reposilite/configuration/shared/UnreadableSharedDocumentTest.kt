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
import com.reposilite.configuration.shared.infrastructure.LocalSharedConfigurationProvider
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.mutableReference
import java.nio.file.Path

/**
 * A shared document that is not valid JSON is reported once, with the file and the position, and the domains fall back to
 * defaults. The messages are read from an in-memory logger, so the test reads no console and no file outside its temp directory.
 */
internal class UnreadableSharedDocumentTest {

    @TempDir
    lateinit var workingDirectory: Path

    /** A domain whose only property is the value a test can look for in the log. */
    @Doc(title = "Probe", description = "Probe settings")
    data class ProbeSettings(val property: String = "value") : SharedSettings

    private val journalist = InMemoryLogger()

    private val facade by lazy {
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(ProbeSettings::class.java to mutableReference(ProbeSettings()))),
            sharedConfigurationProvider = LocalSharedConfigurationProvider(journalist, workingDirectory, Path.of("configuration.shared.json")),
            extensions = Extensions(journalist),
        )
    }

    private fun errorLines(): List<String> =
        journalist.messages
            .filter { it.key == Channel.ERROR }
            .map { it.value }

    @Test
    fun `a shared document that is not valid json logs one error naming the file and the position`() {
        // given: a document whose third line holds an unquoted value
        val document = """
            {
              "probe": {
                "property": hunter2
              }
            }
        """.trimIndent()

        // when: the document is loaded
        val result = facade.loadSharedSettingsFromString(document)

        // then: the load still succeeds and exactly one error names the file and the line and column
        assertThat(result.isOk).describedAs("loading an unreadable document keeps the start going").isTrue
        val errors = errorLines()
        assertThat(errors).describedAs("error lines written for the unreadable document").hasSize(1)
        assertThat(errors.single())
            .describedAs("error text")
            .contains("configuration.shared.json")
            .contains("line 3")
            .contains("column 17")
    }

    @Test
    fun `the error for an unreadable shared document does not repeat any value from the document`() {
        // given: a document that carries a credential next to a syntax error
        val document = """{ "probe": { "property": hunter2 } }"""

        // when: the document is loaded
        facade.loadSharedSettingsFromString(document)

        // then: the credential is not in any error line
        assertThat(errorLines()).describedAs("error lines written for the unreadable document").isNotEmpty
        assertThat(errorLines().joinToString("\n"))
            .describedAs("error text must not carry the document content")
            .doesNotContain("hunter2")
    }

    @Test
    fun `an empty shared document logs no error`() {
        // given: an empty shared document, as written when the file is created but not filled

        // when: the document is loaded
        val result = facade.loadSharedSettingsFromString("")

        // then: nothing is reported as an error
        assertThat(result.isOk).describedAs("loading an empty document succeeds").isTrue
        assertThat(errorLines()).describedAs("error lines written for an empty document").isEmpty()
    }

}
