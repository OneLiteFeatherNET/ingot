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
import com.reposilite.configuration.shared.api.Sensitive
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.journalist.Channel
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
 * The resolved view of a loaded document: consumers read the resolved values, the stored view and the rendered document keep the
 * references, and a failing reference keeps the previous settings. Every test builds its own facade, provider and directory, and
 * reads no real file or variable outside its temporary directory.
 */
internal class SettingsSecretReferenceLoadTest {

    @TempDir
    lateinit var directory: Path

    @Doc(title = "Probe", description = "Probe settings")
    data class ProbeSettings(@get:Sensitive val secret: String = "default") : SharedSettings

    private class MemoryProvider(var content: String) : SharedConfigurationProvider {

        override fun updateConfiguration(content: String) {
            this.content = content
        }

        override fun fetchConfiguration(): String =
            content

        override fun isUpdateRequired(): Boolean =
            false

        override fun isMutable(): Boolean =
            false

        override fun name(): String =
            "memory"
    }

    private val journalist = InMemoryLogger()
    private val provider = MemoryProvider(content = "{}")

    private val secrets: Path by lazy { Files.createDirectories(directory.resolve("secrets")) }

    private fun secretFile(name: String, content: String): Path =
        Files.writeString(secrets.resolve(name), content)

    private fun resolverOn(): SecretReferenceResolver =
        SecretReferenceResolver(environment = emptyMap(), variables = emptySet(), directories = listOf(secrets))

    private fun facade(resolver: SecretReferenceResolver? = resolverOn()): SharedConfigurationFacade =
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(ProbeSettings::class.java to mutableReference(ProbeSettings()))),
            sharedConfigurationProvider = provider,
            extensions = Extensions(journalist),
            secretReferences = resolver,
        )

    private fun document(secret: String): String =
        """{ "probe": { "secret": "$secret" } }"""

    private fun SharedConfigurationFacade.consumerSecret(): String =
        getDomainSettings<ProbeSettings>().get().secret

    private fun SharedConfigurationFacade.storedSecret(): String =
        getDomainSnapshot<ProbeSettings>("probe")!!.settings.secret

    private fun errorMessages(): List<String> =
        journalist.messages.filter { it.key == Channel.ERROR }.map { it.value }

    @Test
    fun `a loaded reference gives consumers the resolved value and the stored view the reference`() {
        // given: a secret file in the allowed directory and a document that names it
        val file = secretFile("s3", "s3cret-file-value")
        val facade = facade()

        // when: the document is loaded
        val result = facade.loadSharedSettingsFromString(document("\${file:$file}"))

        // then: the load succeeds, consumers read the value and the stored view keeps the reference
        assertThat(result.isOk).describedAs("load result").isTrue()
        assertThat(facade.consumerSecret()).describedAs("secret read by consumers").isEqualTo("s3cret-file-value")
        assertThat(facade.storedSecret()).describedAs("secret of the stored view").isEqualTo("\${file:$file}")
    }

    @Test
    fun `rendering the document writes the reference and never the resolved value`() {
        // given: a loaded reference
        val file = secretFile("s3", "s3cret-file-value")
        val facade = facade()
        facade.loadSharedSettingsFromString(document("\${file:$file}"))

        // when: the domain is written, which renders the whole document
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "\${file:$file}").asSuccess() }

        // then: the rendered document holds the reference and not the value
        assertThat(provider.content)
            .describedAs("rendered document")
            .contains("\${file:$file}")
            .doesNotContain("s3cret-file-value")
    }

    @Test
    fun `a missing file at start stops the load and the error names the domain, the property and the path`() {
        // given: a document that names a file that does not exist
        val missing = secrets.resolve("missing")
        val facade = facade()

        // when: the document is loaded
        val result = facade.loadSharedSettingsFromString(document("\${file:$missing}"))

        // then: the load fails with a message that names the domain, the property and the path
        assertThat(result.isErr).describedAs("load result").isTrue()
        val message = result.error.errors.single().second.message.orEmpty()
        assertThat(message)
            .describedAs("message of the missing file")
            .contains("probe", "secret", missing.toString())
    }

    @Test
    fun `a failing reload keeps the previous resolved value and logs one error`() {
        // given: a loaded document whose file is then removed
        val file = secretFile("s3", "first")
        val facade = facade()
        facade.loadSharedSettingsFromString(document("\${file:$file}"))
        Files.delete(file)

        // when: the document is loaded again
        val result = facade.loadSharedSettingsFromString(document("\${file:$file}"))

        // then: the load fails, the previous value stays and one error is logged for the domain
        assertThat(result.isErr).describedAs("reload result").isTrue()
        assertThat(facade.consumerSecret()).describedAs("secret after a failing reload").isEqualTo("first")
        assertThat(errorMessages().filter { "Cannot update 'probe'" in it })
            .describedAs("errors logged for the failing domain")
            .hasSize(1)
    }

    @Test
    fun `a changed file takes effect on the next reload and not before`() {
        // given: a loaded reference, and a file whose content changes after the load
        val file = secretFile("s3", "first")
        val facade = facade()
        facade.loadSharedSettingsFromString(document("\${file:$file}"))
        Files.writeString(file, "second")

        // when: the value is read twice before the next reload
        val beforeReload = listOf(facade.consumerSecret(), facade.consumerSecret())

        // then: both reads give the value of the load, the file is not read again
        assertThat(beforeReload).describedAs("reads before the reload").containsOnly("first")

        // when: the document is reloaded
        facade.loadSharedSettingsFromString(document("\${file:$file}"))

        // then: the new content is the value
        assertThat(facade.consumerSecret()).describedAs("secret after the reload").isEqualTo("second")
    }

    @Test
    fun `a captured log holds no resolved value`() {
        // given: a loaded reference whose value is distinctive
        val file = secretFile("s3", "s3cret-file-value")
        val facade = facade()
        facade.loadSharedSettingsFromString(document("\${file:$file}"))

        // when: the document is rendered and loaded again
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "\${file:$file}").asSuccess() }
        facade.loadSharedSettingsFromString(document("\${file:$file}"))

        // then: no line that the logger holds contains the value
        assertThat(journalist.messages.map { it.value })
            .describedAs("logged lines")
            .noneMatch { "s3cret-file-value" in it }
    }

    @Test
    fun `with references off a reference-looking value is the consumer value too`() {
        // given: no resolver, so the text is a plain value
        val facade = facade(resolver = null)

        // when: the document is loaded
        facade.loadSharedSettingsFromString(document("\${env:S3_SECRET}"))

        // then: consumers and the stored view both read the text as it was written
        assertThat(facade.consumerSecret()).describedAs("secret read by consumers").isEqualTo("\${env:S3_SECRET}")
        assertThat(facade.storedSecret()).describedAs("secret of the stored view").isEqualTo("\${env:S3_SECRET}")
    }
}
