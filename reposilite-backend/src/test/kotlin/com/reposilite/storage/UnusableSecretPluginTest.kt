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
import com.reposilite.configuration.shared.SCHEMA_OPTION_PRESET
import com.reposilite.configuration.shared.SettingsModule
import com.reposilite.configuration.shared.SettingsSecrets
import com.reposilite.maven.application.MavenSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A storage plugin that marks a field that is not a String as a secret is left out of the served schema and of the secret shape,
 * with one warning, and nothing else changes. Every test builds its own registry, guard and warning list.
 */
internal class UnusableSecretPluginTest {

    private val warnings = mutableListOf<String>()
    private val registry = registryWithNumericStore()
    private val guard = PluginStorageSecretGuard { warnings += it }
    private val generator = SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).with(SettingsModule()).build())
    private val variants = StorageProviderVariants(registry, { generator.generateSchema(it) }, guard = guard) { warnings += it }

    private val served = """
        { "type": "object", "properties": { "repositories": { "type": "array", "items": { "type": "object", "properties": {
          "storageProvider": { "oneOf": [
            { "type": "object", "properties": { "type": { "type": "string", "const": "fs" } } },
            { "type": "object", "properties": { "type": { "type": "string", "const": "s3" } } }
          ] } } } } } }
    """.trimIndent()

    private fun maven(block: String): ObjectNode =
        DEFAULT_OBJECT_MAPPER.readTree("""{ "repositories": [ { "id": "releases", "storageProvider": $block } ] }""") as ObjectNode

    private fun JsonNode.storage(): JsonNode =
        at("/repositories/0/storageProvider")

    private fun offeredTypes(): List<String> =
        DEFAULT_OBJECT_MAPPER.readTree(variants.apply(served.toByteArray()))
            .at("/properties/repositories/items/properties/storageProvider/oneOf")
            .map { it.at("/properties/type/const").asText() }

    @Test
    fun `should leave a plugin with a secret field that is not a String out of the served schema`() {
        assertThat(offeredTypes())
            .describedAs("constant types of the served storageProvider variants")
            .containsExactly("fs", "s3", VAULT_STORE)
    }

    @Test
    fun `should keep masking the other plugin and the s3 secrets when one plugin has an unusable secret field`() {
        val secretVariants = registry.secretVariants(guard)

        val vault = SettingsSecrets.mask(MavenSettings::class.java, maven("""{ "type": "$VAULT_STORE", "accountKey": "stored-key" }"""), variants = secretVariants)
        val s3 = SettingsSecrets.mask(MavenSettings::class.java, maven("""{ "type": "s3", "accessKey": "access", "secretKey": "secret" }"""), variants = secretVariants)

        assertThat(vault.storage().get("accountKey").textValue()).describedAs("account key of the other plugin").isEqualTo(HIDDEN_SECRET)
        assertThat(s3.storage().get("accessKey").textValue()).describedAs("s3 access key").isEqualTo(HIDDEN_SECRET)
        assertThat(s3.storage().get("secretKey").textValue()).describedAs("s3 secret key").isEqualTo(HIDDEN_SECRET)
    }

    @Test
    fun `should leave the plugin with an unusable secret field out of the secret shape`() {
        assertThat(registry.secretVariants(guard).of(StorageProviderSettings::class).map { it.java })
            .describedAs("settings classes the secret shape adds to a storage property")
            .containsExactly(VaultStoreSettings::class.java)
    }

    @Test
    fun `should warn once naming the plugin, type, field and jar however often schema and secrets are used`() {
        repeat(3) {
            offeredTypes()
            SettingsSecrets.mask(MavenSettings::class.java, maven("""{ "type": "fs" }"""), variants = registry.secretVariants(guard))
        }

        assertThat(warnings).describedAs("warnings").hasSize(1)
        assertThat(warnings.single())
            .describedAs("warning")
            .contains(NUMERIC_STORE, "pin", "numeric-store-plugin.jar", NumericStoreSettings::class.java.name)
    }

}
