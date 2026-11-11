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
import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Sensitive
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.shared.EntityTagSigner
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.settings.encryption.NonceSource
import net.onelitefeather.ingot.settings.encryption.SettingsEncryptionMode
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec
import net.onelitefeather.ingot.settings.encryption.SettingsSecretCipher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.asSuccess
import panda.std.reactive.mutableReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * The stored document of a domain holds envelopes for its literal secrets when a cipher is active, and the consumers read plain
 * values. Every test builds its own facade, provider and temporary directory, and uses a fixed key and a fixed nonce.
 */
internal class SettingsSealingFacadeTest {

    @TempDir
    lateinit var directory: Path

    @Doc(title = "Probe", description = "Probe settings")
    data class ProbeSettings(
        @get:Sensitive val secret: String = "default",
        val label: String = "plain",
    ) : SharedSettings

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

    private val key = ByteArray(32) { (it * 7 + 3).toByte() }
    private val nonce = ByteArray(12) { 0x11 }
    private val journalist = InMemoryLogger()
    private val signer = EntityTagSigner.ephemeral()
    private val provider = MemoryProvider(content = "{}")

    private fun cipher(): SettingsSecretCipher =
        SettingsSecretCipher(
            active = SettingsEnvelopeCodec(key = key, nonces = NonceSource { nonce.copyOf() }),
            previous = null,
            mode = SettingsEncryptionMode.OPTIONAL,
        )

    private fun facade(
        cipher: SettingsSecretCipher? = cipher(),
        resolver: SecretReferenceResolver? = null,
    ): SharedConfigurationFacade =
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(ProbeSettings::class.java to mutableReference(ProbeSettings()))),
            sharedConfigurationProvider = provider,
            extensions = Extensions(journalist),
            secretReferences = resolver,
            entityTags = signer,
            secretCipher = cipher,
        )

    private fun storedNode(): JsonNode =
        DEFAULT_OBJECT_MAPPER.readTree(provider.content).get("probe")

    private fun SharedConfigurationFacade.consumerSecret(): String =
        getDomainSettings<ProbeSettings>().get().secret

    private fun errorMessages(): List<String> =
        journalist.messages.filter { it.key == Channel.ERROR }.map { it.value }

    private fun document(secret: String, label: String = "plain"): String =
        """{ "probe": { "secret": "$secret", "label": "$label" } }"""

    @Test
    fun `a literal secret is stored as an envelope and the plain value never reaches the stored document`() {
        // given: a facade with a key and a secret written through the API
        val facade = facade()

        // when
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "s3cret-value").asSuccess() }

        // then
        val stored = storedNode().get("secret").asText()
        assertThat(stored).describedAs("stored secret").startsWith("enc:v1:").doesNotContain("s3cret-value")
        assertThat(provider.content).describedAs("stored document").doesNotContain("s3cret-value")
    }

    @Test
    fun `a non-secret value is stored as written`() {
        // given
        val facade = facade()

        // when
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "s3cret-value", label = "visible").asSuccess() }

        // then
        assertThat(storedNode().get("label").asText()).describedAs("stored label").isEqualTo("visible")
    }

    @Test
    fun `an empty secret is stored empty`() {
        // given
        val facade = facade()

        // when
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "").asSuccess() }

        // then
        assertThat(storedNode().get("secret").asText()).describedAs("stored secret").isEmpty()
    }

    @Test
    fun `a reference is stored as written when references are on`() {
        // given: a secret file in an allowed directory, references switched on, and a reference written through the API
        val secrets = Files.createDirectories(directory.resolve("secrets"))
        val file = Files.writeString(secrets.resolve("s3"), "file-value")
        val resolver = SecretReferenceResolver(environment = emptyMap(), variables = emptySet(), directories = listOf(secrets))
        val facade = facade(resolver = resolver)

        // when
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "\${file:$file}").asSuccess() }

        // then: the reference is stored as written and no envelope is written for it
        assertThat(storedNode().get("secret").asText()).describedAs("stored secret").isEqualTo("\${file:$file}")
    }

    @Test
    fun `a stored document with envelopes loads into the consumer view as plain values`() {
        // given: a document written by this facade
        val writer = facade()
        writer.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "s3cret-value").asSuccess() }

        // when: a new instance loads the stored document
        val reader = facade()
        val result = reader.loadSharedSettingsFromString(provider.content)

        // then
        assertThat(result.isOk).describedAs("load result").isTrue()
        assertThat(reader.consumerSecret()).describedAs("consumer secret").isEqualTo("s3cret-value")
    }

    @Test
    fun `the entity tag of the same settings is the same with and without sealing`() {
        // given: the same settings on a facade that seals and on a facade that does not
        val sealing = facade(cipher = cipher())
        val plain = facade(cipher = null)
        sealing.loadSharedSettingsFromString(document("s3cret-value"))
        plain.loadSharedSettingsFromString(document("s3cret-value"))

        // when / then: the tag describes the plain settings, so sealing changes nothing in it
        assertThat(sealing.getDomainSnapshot<ProbeSettings>("probe")!!.etag)
            .describedAs("entity tag with sealing")
            .isEqualTo(plain.getDomainSnapshot<ProbeSettings>("probe")!!.etag)
    }

    @Test
    fun `a tampered envelope on reload keeps the previous settings and logs the domain and the property only`() {
        // given: a loaded document with a sealed secret
        val facade = facade()
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "first-value").asSuccess() }
        facade.loadSharedSettingsFromString(provider.content)
        val envelope = storedNode().get("secret").asText()
        val tampered = envelope.dropLast(4) + (if (envelope.endsWith("AAAA")) "BBBB" else "AAAA")

        // when: the same document comes back with the envelope changed
        val result = facade.loadSharedSettingsFromString(provider.content.replace(envelope, tampered))

        // then: the load fails, the previous value stays and the error names the domain and the property
        assertThat(result.isErr).describedAs("load result").isTrue()
        assertThat(facade.consumerSecret()).describedAs("consumer secret").isEqualTo("first-value")
        assertThat(errorMessages().joinToString("\n"))
            .describedAs("error log")
            .contains("probe")
            .contains("secret")
            .doesNotContain(tampered)
            .doesNotContain(envelope)
    }

    @Test
    fun `an envelope without a configured key stops the load of its domain and names the property`() {
        // given: a document sealed with a key, read by an instance without a key
        val writer = facade()
        writer.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "s3cret-value").asSuccess() }
        val unkeyed = facade(cipher = null)

        // when
        val result = unkeyed.loadSharedSettingsFromString(provider.content)

        // then
        assertThat(result.isErr).describedAs("load result").isTrue()
        assertThat(errorMessages().joinToString("\n")).describedAs("error log").contains("probe").contains("secret")
        assertThat(errorMessages().joinToString("\n")).doesNotContain("s3cret-value")
    }

}
