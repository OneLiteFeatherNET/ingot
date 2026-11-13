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

package com.reposilite.configuration.shared.infrastructure

import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.reposilite.ReposiliteJournalist
import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.configuration.infrastructure.InMemoryConfigurationRepository
import com.reposilite.configuration.shared.SCHEMA_OPTION_PRESET
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.shared.SharedConfigurationProvider
import com.reposilite.configuration.shared.SharedSettingsProvider
import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Sensitive
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.console.CommandContext
import com.reposilite.console.CommandStatus
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.settings.encryption.NonceSource
import net.onelitefeather.ingot.settings.encryption.SettingsEncryptionMode
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec
import net.onelitefeather.ingot.settings.encryption.SettingsKeyRecords
import net.onelitefeather.ingot.settings.encryption.SettingsSecretCipher
import net.onelitefeather.ingot.settings.encryption.StoredSettingsKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.asSuccess
import panda.std.reactive.mutableReference

/**
 * The rotation and downgrade commands, run against an in-memory document and an in-memory configuration table. Every test builds its own
 * facade, records and cipher with fixed keys, so no test depends on another.
 */
internal class SettingsKeyCommandsTest {

    @Doc(title = "Probe", description = "Probe settings")
    data class ProbeSettings(@get:Sensitive val secret: String = "default") : SharedSettings

    private class MemoryProvider(var content: String) : SharedConfigurationProvider {
        override fun updateConfiguration(content: String) {
            this.content = content
        }

        override fun fetchConfiguration(): String = content

        override fun isUpdateRequired(): Boolean = false

        override fun isMutable(): Boolean = true

        override fun name(): String = "memory"
    }

    private val oldKey = ByteArray(32) { (it * 5 + 2).toByte() }
    private val newKey = ByteArray(32) { (it * 11 + 4).toByte() }
    private val journalist = InMemoryLogger()
    private val reporter = ReposiliteJournalist(InMemoryLogger(), 0, testEnv = true, noColor = false)

    private fun codec(key: ByteArray) = SettingsEnvelopeCodec(key = key, nonces = NonceSource { ByteArray(12) { 0x21 } })

    private fun cipher(active: ByteArray?, previous: ByteArray?) = SettingsSecretCipher(
        active = active?.let { codec(it) },
        previous = previous?.let { codec(it) },
        mode = SettingsEncryptionMode.OPTIONAL,
    )

    private fun facade(provider: MemoryProvider, cipher: SettingsSecretCipher) =
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(ProbeSettings::class.java to mutableReference(ProbeSettings()))),
            sharedConfigurationProvider = provider,
            extensions = Extensions(journalist),
            secretCipher = cipher,
        )

    /** A document whose probe secret was sealed with [key], as an older instance would have stored it. */
    private fun documentSealedWith(key: ByteArray): String {
        val envelope = codec(key).seal(domain = "probe", path = "secret", plain = "rotated-secret-value")
        return """{ "probe": { "secret": "$envelope" } }"""
    }

    private fun records() = SettingsKeyRecords(ConfigurationFacade(InMemoryConfigurationRepository()))

    private fun context() = CommandContext(reporter)

    @Test
    fun `reencrypt without an active key fails and writes nothing`() {
        // given: an instance without a key and a sealed document
        val provider = MemoryProvider(documentSealedWith(oldKey))
        val before = provider.content
        val records = records()
        val cipher = cipher(active = null, previous = null)

        // when
        val context = context()
        SettingsReencryptCommand(facade(provider, cipher), records, cipher).execute(context)

        // then
        assertThat(context.status).describedAs("status").isEqualTo(CommandStatus.FAILED)
        assertThat(provider.content).describedAs("stored document").isEqualTo(before)
        assertThat(records.read()).describedAs("key record").isNull()
    }

    @Test
    fun `reencrypt seals a value of the previous key with the active key and records the active key`() {
        // given: a document sealed with the previous key, and the new key active with the old one as previous
        val provider = MemoryProvider(documentSealedWith(oldKey))
        val records = records()
        val cipher = cipher(active = newKey, previous = oldKey)
        val facade = facade(provider, cipher)
        facade.loadSharedSettingsFromString(provider.content)

        // when
        val context = context()
        SettingsReencryptCommand(facade, records, cipher).execute(context)

        // then: the stored envelope names the new key, the value still reads, and the record names the new key
        assertThat(context.status).describedAs("status").isEqualTo(CommandStatus.SUCCEEDED)
        assertThat(provider.content).describedAs("stored document").contains("enc:v1:${codec(newKey).keyId}:").doesNotContain("rotated-secret-value")
        assertThat(records.read()).describedAs("key record").isEqualTo(StoredSettingsKey.of(codec(newKey)))
        assertThat(facade.getDomainSettings<ProbeSettings>().get().secret).describedAs("consumer secret").isEqualTo("rotated-secret-value")
    }

    @Test
    fun `decrypt without an active key refuses and leaves the document sealed`() {
        // given
        val provider = MemoryProvider(documentSealedWith(oldKey))
        val before = provider.content
        val cipher = cipher(active = null, previous = oldKey)

        // when
        val context = context()
        SettingsDecryptCommand(facade(provider, cipher), records(), cipher).execute(context)

        // then
        assertThat(context.status).describedAs("status").isEqualTo(CommandStatus.FAILED)
        assertThat(provider.content).describedAs("stored document").isEqualTo(before)
    }

    @Test
    fun `decrypt writes the plain values back and clears the key record`() {
        // given: a sealed document with the record of the key that sealed it
        val provider = MemoryProvider(documentSealedWith(oldKey))
        val records = records()
        records.write(StoredSettingsKey.of(codec(oldKey)))
        val cipher = cipher(active = oldKey, previous = null)
        val facade = facade(provider, cipher)
        facade.loadSharedSettingsFromString(provider.content)

        // when
        val context = context()
        SettingsDecryptCommand(facade, records, cipher).execute(context)

        // then: the value is stored in clear again and the record is gone
        assertThat(context.status).describedAs("status").isEqualTo(CommandStatus.SUCCEEDED)
        assertThat(provider.content).describedAs("stored document").contains("rotated-secret-value").doesNotContain("enc:v1:")
        assertThat(records.read()).describedAs("key record").isNull()
    }

}
