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
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.reposilite.configuration.shared.SCHEMA_OPTION_PRESET
import com.reposilite.configuration.shared.SettingsModule
import com.reposilite.storage.filesystem.FileSystemStorageProviderSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The `type` enum and the subtype resolution of [StorageProviderSettings] read the registry they are given. Each test builds its own
 * registry, so the companion map of [StorageProviderSerdes] plays no part.
 */
internal class StorageProviderSerdesRegistryTest {

    data class StorageHolder(
        val storageProvider: StorageProviderSettings = FileSystemStorageProviderSettings(),
    )

    private fun schemaWith(registry: StorageProviderRegistry): JsonNode =
        SchemaGenerator(
            SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET)
                .with(
                    SettingsModule(
                        subtypeResolvers = listOf(StorageProviderSerdes.StorageProviderSubtypeResolver(registry)),
                        enumResolvers = listOf(StorageProviderSerdes.StorageProviderEnumResolver(registry)),
                    )
                )
                .build()
        ).generateSchema(StorageHolder::class.java)

    /** The type ids the generated variants accept, from their `type` enum or constant. */
    private fun JsonNode.offeredTypes(): List<String> =
        at("/properties/storageProvider/oneOf").flatMap { variant ->
            val type = variant.at("/properties/type")
            type.get("enum")?.map { it.asText() } ?: listOfNotNull(type.get("const")?.asText())
        }

    @Test
    fun `should resolve the subtype and the type enum of a plugin factory that the registry holds`() {
        val schema = schemaWith(registryWithVaultStore())

        assertThat(schema.offeredTypes())
            .describedAs("types of the generated storageProvider variants with the vault store plugin registered")
            .contains("fs", "s3", VAULT_STORE)
        assertThat(schema.at("/properties/storageProvider/oneOf").last().at("/properties/accountKey/title").asText())
            .describedAs("title of the account key of the plugin variant")
            .isEqualTo("Account key")
    }

    @Test
    fun `should not resolve a plugin type when the registry holds only the built-in factories`() {
        val schema = schemaWith(StorageProviderRegistry.applicationClassPath())

        assertThat(schema.offeredTypes())
            .describedAs("types of the generated storageProvider variants without a plugin")
            .contains("fs", "s3")
            .doesNotContain(VAULT_STORE)
    }

    @Test
    fun `should resolve nothing from the companion map when the registry is empty`() {
        val schema = schemaWith(StorageProviderRegistry())

        assertThat(schema.offeredTypes())
            .describedAs("types of the generated storageProvider variants of an empty registry")
            .isEmpty()
    }

}
