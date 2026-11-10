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
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import io.javalin.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.asSuccess
import panda.std.reactive.mutableReference
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes through [SharedConfigurationFacade.updateDomain] with settings secret references. A reference that cannot be resolved is
 * refused before anything is stored, and a reference that can be resolved is stored as written. The provider is in memory and
 * the directory is temporary, so no file or variable outside the test is read.
 */
internal class SettingsSecretReferenceWriteTest {

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
    private val provider = MemoryProvider(content = """{ "probe": { "secret": "default" } }""")

    private fun facade(resolver: SecretReferenceResolver?): SharedConfigurationFacade =
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(ProbeSettings::class.java to mutableReference(ProbeSettings()))),
            sharedConfigurationProvider = provider,
            extensions = Extensions(journalist),
            secretReferences = resolver,
        )

    private fun SharedConfigurationFacade.write(secret: String) =
        updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = secret).asSuccess() }

    private fun SharedConfigurationFacade.storedSecret(): String =
        getDomainSettings<ProbeSettings>().get().secret

    @Test
    fun `a write that names a variable outside the allowlist is refused with a bad request that names the field and the variable`() {
        // given: a resolver that allows one variable, and a write that names another one that is set
        val resolver = SecretReferenceResolver(
            environment = mapOf("DB_PASSWORD" to "db-value-xyz"),
            variables = setOf("S3_SECRET"),
            directories = emptyList(),
        )

        // when: the write arrives
        val result = facade(resolver).write("\${env:DB_PASSWORD}")

        // then: the write is refused as a bad request that names the field and the variable, and not its value
        assertThat(result).describedAs("result of the write").isNotNull()
        assertThat(result!!.isErr).describedAs("the write is refused").isTrue()
        assertThat(result.error.status).describedAs("status of the refused write").isEqualTo(HttpStatus.BAD_REQUEST.code)
        assertThat(result.error.message)
            .describedAs("message of the refused write")
            .contains("secret", "DB_PASSWORD")
            .doesNotContain("db-value-xyz")
    }

    @Test
    fun `a refused write stores nothing and leaves the settings as they were`() {
        // given: a resolver that refuses the variable of the write
        val resolver = SecretReferenceResolver(environment = emptyMap(), variables = emptySet(), directories = emptyList())
        val facade = facade(resolver)
        val before = provider.content

        // when: the write is refused
        facade.write("\${env:S3_SECRET}")

        // then: the stored document and the settings are the ones before the write
        assertThat(provider.content).describedAs("stored document after a refused write").isEqualTo(before)
        assertThat(facade.storedSecret()).describedAs("secret after a refused write").isEqualTo("default")
    }

    @Test
    fun `a write of an allowed and set variable is stored as the reference, not as its value`() {
        // given: a resolver that allows and has the variable
        val resolver = SecretReferenceResolver(
            environment = mapOf("S3_SECRET" to "s3cret-value"),
            variables = setOf("S3_SECRET"),
            directories = emptyList(),
        )

        // when: the write arrives
        val result = facade(resolver).write("\${env:S3_SECRET}")

        // then: the write succeeds and the stored document holds the reference and not the value
        assertThat(result!!.isOk).describedAs("the write succeeds").isTrue()
        assertThat(provider.content)
            .describedAs("stored document")
            .contains("\${env:S3_SECRET}")
            .doesNotContain("s3cret-value")
    }

    @Test
    fun `a file reference is read back as written by the stored settings`() {
        // given: a secret file inside an allowed directory
        val secrets = Files.createDirectories(directory.resolve("secrets"))
        val file = Files.writeString(secrets.resolve("s3"), "s3cret-value")
        val facade = facade(SecretReferenceResolver(emptyMap(), emptySet(), listOf(secrets)))

        // when: the reference is written and the setting is read back
        val result = facade.write("\${file:$file}")

        // then: the write succeeds and the read returns the reference as written
        assertThat(result!!.isOk).describedAs("the write succeeds").isTrue()
        assertThat(facade.storedSecret())
            .describedAs("secret read back")
            .isEqualTo("\${file:$file}")
    }

    @Test
    fun `with references off a reference-looking value is stored as plain text`() {
        // given: no resolver, the switch is off
        val facade = facade(resolver = null)

        // when: a value that looks like a reference is written
        val result = facade.write("\${env:S3_SECRET}")

        // then: the value is stored and read back as plain text, as it always was
        assertThat(result!!.isOk).describedAs("the write succeeds").isTrue()
        assertThat(facade.storedSecret())
            .describedAs("secret read back with references off")
            .isEqualTo("\${env:S3_SECRET}")
    }
}
