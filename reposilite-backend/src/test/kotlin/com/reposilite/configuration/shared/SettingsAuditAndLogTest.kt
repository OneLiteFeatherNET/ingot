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
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Sensitive
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.EventListener
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.settings.encryption.NonceSource
import net.onelitefeather.ingot.settings.encryption.SettingsEncryptionMode
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec
import net.onelitefeather.ingot.settings.encryption.SettingsSecretCipher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.asSuccess
import panda.std.reactive.mutableReference
import java.util.Base64

/**
 * The audit enumeration and the audit and log output of the settings store. Events are collected by a listener of the same
 * [Extensions] the facade emits to, and log lines by the in-memory journal. Every test uses a fixed key and nonce.
 */
internal class SettingsAuditAndLogTest {

    @Doc(title = "Probe", description = "Probe settings")
    data class ProbeSettings(
        @get:Sensitive val secret: String = "default",
        val label: String = "plain",
    ) : SharedSettings

    private class MemoryProvider(var content: String) : SharedConfigurationProvider {
        override fun updateConfiguration(content: String) {
            this.content = content
        }

        override fun fetchConfiguration(): String = content

        override fun isUpdateRequired(): Boolean = false

        override fun isMutable(): Boolean = true

        override fun name(): String = "memory"
    }

    private val key = ByteArray(32) { (it * 7 + 3).toByte() }
    private val encodedKey = Base64.getEncoder().encodeToString(key)
    private val codec = SettingsEnvelopeCodec(key = key, nonces = NonceSource { ByteArray(12) { 0x31 } })
    private val journalist = InMemoryLogger()
    private val extensions = Extensions(journalist)
    private val events = mutableListOf<AuditEvent>()
    private val provider = MemoryProvider(content = "{}")

    init {
        extensions.registerEvent(EventListener<AuditEvent> { events.add(it) })
    }

    private fun facade(): SharedConfigurationFacade =
        SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(ProbeSettings::class.java to mutableReference(ProbeSettings()))),
            sharedConfigurationProvider = provider,
            extensions = extensions,
            secretCipher = SettingsSecretCipher(active = codec, previous = null, mode = SettingsEncryptionMode.OPTIONAL),
        )

    private fun messages(channel: Channel? = null): List<String> =
        journalist.messages.filter { channel == null || it.key == channel }.map { it.value }

    @Test
    fun `the four settings actions are appended after the support bundle action with their wire names`() {
        // given / when
        val actions = AuditAction.entries.map { it.name }

        // then: the new names follow the last action that existed before, in the order of the design
        val tail = actions.takeLast(4)
        assertThat(tail)
            .describedAs("last four audit actions")
            .containsExactly("SETTINGS_EXPORTED", "SETTINGS_IMPORT_DECRYPTED", "SETTINGS_IMPORT_REFUSED", "SETTINGS_SECRETS_REENCRYPTED")
        assertThat(actions.indexOf("SUPPORT_BUNDLE_DOWNLOADED"))
            .describedAs("position of the action before the new ones")
            .isEqualTo(actions.size - 5)
    }

    @Test
    fun `a settings change audit event names the fields and never a value`() {
        // given: a secret written through the facade
        val facade = facade()

        // when
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "audit-secret-value", label = "shown").asSuccess() }

        // then: the change is audited, and no audit event holds the value
        val changed = events.filter { it.action == AuditAction.SETTINGS_CHANGED }
        assertThat(changed).describedAs("settings change events").isNotEmpty()
        assertThat(changed.joinToString { it.details.toString() })
            .describedAs("audit details")
            .contains("secret")
            .doesNotContain("audit-secret-value")
        assertThat(events.joinToString { it.toString() }).describedAs("all audit events").doesNotContain("audit-secret-value")
    }

    @Test
    fun `the re-encryption event carries the key identifier and a count and never a value`() {
        // given: a plain document and a key
        provider.content = """{ "probe": { "secret": "plain-audit-value", "label": "x" } }"""
        val facade = facade()
        facade.loadSharedSettingsFromString(provider.content)

        // when
        val written = facade.sealStoredSecrets(reason = "first-start")

        // then
        assertThat(written).describedAs("values sealed").isEqualTo(1)
        val event = events.single { it.action == AuditAction.SETTINGS_SECRETS_REENCRYPTED }
        assertThat(event.details)
            .describedAs("details")
            .containsEntry("reason", "first-start")
            .containsEntry("values", 1)
            .containsEntry("activeKeyId", codec.keyId)
        assertThat(event.details.toString()).describedAs("details").doesNotContain("plain-audit-value")
    }

    @Test
    fun `a failing load logs the domain and the property and no envelope, key or plain value`() {
        // given: a sealed document, then the same document with the envelope changed
        val facade = facade()
        facade.updateDomain<ProbeSettings>("probe", ifMatch = null) { ProbeSettings(secret = "log-secret-value").asSuccess() }
        val envelope = Regex("enc:v1:[^\"]+").find(provider.content)!!.value
        val tampered = provider.content.replace(envelope, envelope.dropLast(4) + (if (envelope.endsWith("AAAA")) "BBBB" else "AAAA"))

        // when
        facade.loadSharedSettingsFromString(tampered)

        // then: the error names the domain and the property, and nothing in any line is secret material
        val logged = messages().joinToString("\n")
        assertThat(messages(Channel.ERROR).joinToString("\n")).describedAs("error lines").contains("probe")
        assertThat(logged)
            .describedAs("all log lines")
            .doesNotContain(envelope)
            .doesNotContain("log-secret-value")
            .doesNotContain(encodedKey)
    }

    @Test
    fun `sealing the stored document logs no envelope and no plain value`() {
        // given: a plain document
        provider.content = """{ "probe": { "secret": "sealing-log-value", "label": "x" } }"""
        val facade = facade()
        facade.loadSharedSettingsFromString(provider.content)

        // when
        facade.sealStoredSecrets(reason = "first-start")

        // then
        assertThat(messages().joinToString("\n"))
            .describedAs("all log lines")
            .doesNotContain("sealing-log-value")
            .doesNotContain("enc:v1:")
            .doesNotContain(encodedKey)
    }

}
