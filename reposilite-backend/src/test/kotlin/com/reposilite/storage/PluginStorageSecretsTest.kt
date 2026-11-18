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

package com.reposilite.storage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.shared.HIDDEN_SECRET
import com.reposilite.configuration.shared.OneOfVariants
import com.reposilite.configuration.shared.SCHEMA_OPTION_PRESET
import com.reposilite.configuration.shared.SettingsSecrets
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.shared.SharedConfigurationProvider
import com.reposilite.configuration.shared.SharedSettingsProvider
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.plugin.Extensions
import com.reposilite.shared.EntityTagSigner
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.settings.encryption.NonceSource
import net.onelitefeather.ingot.settings.encryption.SettingsEncryptionMode
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec
import net.onelitefeather.ingot.settings.encryption.SettingsSecretCipher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.asSuccess
import panda.std.reactive.mutableReference

/**
 * The `@Sensitive` fields of a plugin storage variant are secrets like the S3 keys: masked on read, kept when the placeholder comes
 * back, and sealed at rest when a settings key is configured. Every test builds its own registry, facade and provider.
 */
internal class PluginStorageSecretsTest {

    private class MemoryProvider(var content: String = "{}") : SharedConfigurationProvider {
        override fun updateConfiguration(content: String) {
            this.content = content
        }
        override fun fetchConfiguration(): String = content
        override fun isUpdateRequired(): Boolean = false
        override fun isMutable(): Boolean = false
        override fun name(): String = "memory"
    }

    /** A maven domain with one repository whose storage block is [block], as the dashboard sends and stores it. */
    private fun maven(block: String): ObjectNode =
        DEFAULT_OBJECT_MAPPER.readTree("""{ "repositories": [ { "id": "releases", "storageProvider": $block } ] }""") as ObjectNode

    private fun vaultBlock(accountKey: String): String =
        """{ "type": "$VAULT_STORE", "container": "artifacts", "accountKey": "$accountKey" }"""

    private fun JsonNode.storage(): JsonNode =
        at("/repositories/0/storageProvider")

    /** The JSON pointers of every hidden value. */
    private fun JsonNode.maskedPaths(path: String = ""): List<String> =
        when {
            isObject -> fields().asSequence().flatMap { (name, child) -> child.maskedPaths("$path/$name") }.toList()
            isArray -> mapIndexed { index, child -> child.maskedPaths("$path/$index") }.flatten()
            isTextual && textValue() == HIDDEN_SECRET -> listOf(path)
            else -> emptyList()
        }

    @Test
    fun `should mask the sensitive field of a plugin storage variant on read`() {
        val variants = registryWithVaultStore().secretVariants()

        val masked = SettingsSecrets.mask(MavenSettings::class.java, maven(vaultBlock("stored-key")), variants = variants)

        assertThat(masked.storage().get("accountKey").textValue())
            .describedAs("account key of the plugin storage block")
            .isEqualTo(HIDDEN_SECRET)
        assertThat(masked.storage().get("container").textValue())
            .describedAs("container of the plugin storage block, which is not a secret")
            .isEqualTo("artifacts")
    }

    @Test
    fun `should keep the stored value of a plugin secret when the placeholder is sent back`() {
        val variants = registryWithVaultStore().secretVariants()
        val stored = maven(vaultBlock("stored-key"))
        val incoming = maven(vaultBlock(HIDDEN_SECRET))

        val restored = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored, variants = variants)

        assertThat(restored.isOk).describedAs("restore result").isTrue()
        assertThat(restored.get().storage().get("accountKey").textValue())
            .describedAs("account key after the placeholder came back")
            .isEqualTo("stored-key")
    }

    @Test
    fun `should leave a plugin field readable when no plugin is registered, as the shape of main does`() {
        val document = maven(vaultBlock("stored-key"))

        val withoutVariants = SettingsSecrets.mask(MavenSettings::class.java, document)
        val builtInOnly = SettingsSecrets.mask(MavenSettings::class.java, document, variants = StorageProviderRegistry.applicationClassPath().secretVariants())

        assertThat(withoutVariants.maskedPaths()).describedAs("hidden fields without variants").isEmpty()
        assertThat(builtInOnly.maskedPaths()).describedAs("hidden fields with only the built-in providers registered").isEmpty()
    }

    @Test
    fun `should hide exactly the s3 secrets of a built-in block whether or not a plugin is registered`() {
        val document = maven("""{ "type": "s3", "bucketName": "bucket", "endpoint": "https://s3.example.test", "accessKey": "access", "secretKey": "secret", "region": "eu" }""")

        val withoutPlugin = SettingsSecrets.mask(MavenSettings::class.java, document).maskedPaths()
        val withPlugin = SettingsSecrets.mask(MavenSettings::class.java, document, variants = registryWithVaultStore().secretVariants()).maskedPaths()

        assertThat(withoutPlugin)
            .describedAs("hidden fields of an s3 block without a plugin")
            .containsExactlyInAnyOrder(
                "/repositories/0/storageProvider/endpoint",
                "/repositories/0/storageProvider/accessKey",
                "/repositories/0/storageProvider/secretKey",
            )
        assertThat(withPlugin).describedAs("hidden fields of an s3 block with the vault store plugin registered").isEqualTo(withoutPlugin)
    }

    @Test
    fun `should seal the sensitive field of a plugin storage variant at rest when a settings key is configured`() {
        // given: a server with the vault store plugin and an active settings key
        val journalist = InMemoryLogger()
        val extensions = Extensions(journalist)
        extensions.storageProviders.register(FakePluginFactory(VAULT_STORE, VaultStoreSettings::class.java), source = "vault-store-plugin.jar", builtIn = false)
        val provider = MemoryProvider()
        val cipher = SettingsSecretCipher(
            active = SettingsEnvelopeCodec(key = ByteArray(32) { (it * 7 + 3).toByte() }, nonces = NonceSource { ByteArray(12) { 0x11 } }),
            previous = null,
            mode = SettingsEncryptionMode.OPTIONAL,
        )
        val facade = SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).build()) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(MavenSettings::class.java to mutableReference(MavenSettings()))),
            sharedConfigurationProvider = provider,
            extensions = extensions,
            entityTags = EntityTagSigner.ephemeral(),
            secretCipher = cipher,
        )
        val block = UnresolvedStorageProviderSettings(VAULT_STORE, DEFAULT_OBJECT_MAPPER.readTree(vaultBlock("plain-account-key")) as ObjectNode)

        // when: a repository on the plugin storage is saved
        val result = facade.updateDomain<MavenSettings>("maven", ifMatch = null) {
            MavenSettings(repositories = listOf(RepositorySettings(id = "releases", storageProvider = block))).asSuccess()
        }

        // then: the stored document holds an envelope, and the consumer view the plain value
        assertThat(result?.isOk).describedAs("update result").isTrue()
        val stored = DEFAULT_OBJECT_MAPPER.readTree(provider.content).get("maven").storage()
        assertThat(stored.get("accountKey").asText()).describedAs("stored account key").startsWith("enc:v1:")
        assertThat(provider.content).describedAs("stored document").doesNotContain("plain-account-key")
        val consumer = facade.getDomainSettings<MavenSettings>().get().repositories.single().storageProvider
        assertThat(DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(consumer).get("accountKey").asText())
            .describedAs("account key of the consumer view")
            .isEqualTo("plain-account-key")
    }

    @Test
    fun `should offer no extra variant for a property that is not a storage provider`() {
        val variants: OneOfVariants = registryWithVaultStore().secretVariants()

        assertThat(variants.of(String::class)).describedAs("variants of an unrelated declared type").isEmpty()
        assertThat(variants.of(StorageProviderSettings::class).map { it.java })
            .describedAs("variants of the storage provider type")
            .containsExactly(VaultStoreSettings::class.java)
    }

}
