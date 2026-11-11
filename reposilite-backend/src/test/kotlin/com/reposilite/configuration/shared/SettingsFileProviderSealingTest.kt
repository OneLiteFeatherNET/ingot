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
import com.reposilite.configuration.shared.infrastructure.LocalSharedConfigurationProvider
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
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

/**
 * The file provider in both modes. With reload off the file belongs to the instance: it is written sealed on the next save and never
 * rewritten at start. With reload on the file belongs to the operator: it is read as plain and sealed values alike and is never written.
 * Each test works in its own temporary directory with a fixed key and a fixed nonce.
 */
internal class SettingsFileProviderSealingTest {

    @TempDir
    lateinit var directory: Path

    @Doc(title = "Probe", description = "Probe settings")
    data class ProbeSettings(
        @get:Sensitive val secret: String = "default",
        val label: String = "plain",
    ) : SharedSettings

    private val key = ByteArray(32) { (it * 7 + 3).toByte() }
    private val journalist = InMemoryLogger()

    private fun cipher(): SettingsSecretCipher =
        SettingsSecretCipher(
            active = SettingsEnvelopeCodec(key = key, nonces = NonceSource { ByteArray(12) { 0x11 } }),
            previous = null,
            mode = SettingsEncryptionMode.OPTIONAL,
        )

    private fun facade(reloadEnabled: Boolean, cipher: SettingsSecretCipher? = cipher()): SharedConfigurationFacade =
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(ProbeSettings::class.java to mutableReference(ProbeSettings()))),
            sharedConfigurationProvider = LocalSharedConfigurationProvider(
                journalist = journalist,
                workingDirectory = directory,
                configurationFile = Path.of(SHARED_FILE),
                reloadEnabled = reloadEnabled,
            ),
            extensions = Extensions(journalist),
            secretCipher = cipher,
        )

    private fun writeFile(content: String) {
        Files.writeString(directory.resolve(SHARED_FILE), content)
    }

    private fun readFile(): String =
        Files.readString(directory.resolve(SHARED_FILE))

    private val plainDocument = """{ "probe": { "secret": "file-secret-value", "label": "plain" } }"""

    @Test
    fun `with reload off a save writes the secret to the file sealed`() {
        // given: a file-backed facade that owns its file
        val facade = facade(reloadEnabled = false)

        // when: a domain is saved through the API
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "file-secret-value").asSuccess() }

        // then: the file holds an envelope and no plain value
        assertThat(readFile()).describedAs("file content").contains("enc:v1:").doesNotContain("file-secret-value")
    }

    @Test
    fun `with reload off the file is not rewritten at start`() {
        // given: a plain file written by an operator before the key was set
        writeFile(plainDocument)

        // when: the file is loaded with a key configured and the instance starts
        val result = facade(reloadEnabled = false).loadSharedSettingsFromString(readFile())

        // then: the load succeeds and the file is byte for byte what it was
        assertThat(result.isOk).describedAs("load result").isTrue()
        assertThat(readFile()).describedAs("file content after start").isEqualTo(plainDocument)
    }

    @Test
    fun `with reload on the file is read as plain and sealed values alike`() {
        // given: a file that holds a sealed value written by this instance
        val writer = facade(reloadEnabled = false)
        writer.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "file-secret-value").asSuccess() }
        val sealedFile = readFile()

        // when: an operator-managed file with reload on is loaded
        val reader = facade(reloadEnabled = true)
        val result = reader.loadSharedSettingsFromString(sealedFile)

        // then: the consumer reads the plain value
        assertThat(result.isOk).describedAs("load result").isTrue()
        assertThat(reader.getDomainSettings<ProbeSettings>().get().secret).describedAs("consumer secret").isEqualTo("file-secret-value")
    }

    @Test
    fun `with reload on the file is never rewritten by sealing at start`() {
        // given: an operator-managed plain file and a key
        writeFile(plainDocument)
        val facade = facade(reloadEnabled = true)
        facade.loadSharedSettingsFromString(readFile())

        // when: the start asks for the stored secrets to be sealed
        val written = facade.sealStoredSecrets(reason = "first-start")

        // then: nothing is stored and the file is unchanged
        assertThat(written).describedAs("values written").isNull()
        assertThat(readFile()).describedAs("file content after start").isEqualTo(plainDocument)
    }

    private companion object {
        const val SHARED_FILE = "configuration.shared.json"
    }

}
