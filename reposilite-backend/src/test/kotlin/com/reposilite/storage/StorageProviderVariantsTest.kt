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
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.shared.SCHEMA_OPTION_PRESET
import com.reposilite.configuration.shared.SettingsModule
import com.reposilite.configuration.shared.api.Doc
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.IngotStorageProviderFactory
import net.onelitefeather.ingot.storage.STORAGE_PROVIDER_API_VERSION
import net.onelitefeather.ingot.storage.StorageProviderContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The served `storageProvider` property with the variants of the loaded storage plugins appended. Every test builds its own registry
 * and its own generator, so no test sees another one's factories.
 */
@OptIn(ExperimentalIngotStorageApi::class)
internal class StorageProviderVariantsTest {

    @Doc(title = "Fake store", description = "Storage of a fake plugin")
    data class FakeStoreSettings(
        override val type: String = "fake-store",
        @get:Doc(title = "Bucket", description = "Bucket of the fake store") val bucket: String = "",
    ) : StorageProviderSettings

    @Doc(title = "Broken store", description = "Settings whose schema cannot be generated in these tests")
    data class BrokenSettings(
        override val type: String = "broken",
    ) : StorageProviderSettings

    private class FakeFactory<S : StorageProviderSettings>(
        override val type: String,
        override val settingsType: Class<S>,
    ) : IngotStorageProviderFactory<StorageProvider, S> {

        override val apiVersion: Int = STORAGE_PROVIDER_API_VERSION

        override fun create(context: StorageProviderContext, settings: S): StorageProvider =
            error("the variant tests never create a provider")

    }

    /** The served schema of the repository settings, as the build writes it: the two built-in variants under `storageProvider`. */
    private val served = """
        {
          "type": "object",
          "properties": {
            "repositories": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "storageProvider": {
                    "oneOf": [
                      { "type": "object", "title": "File system Storage Provider", "properties": { "type": { "type": "string", "const": "fs" } } },
                      { "type": "object", "title": "S3 Storage Provider", "properties": { "type": { "type": "string", "const": "s3" } } }
                    ]
                  }
                }
              }
            }
          }
        }
    """.trimIndent()

    private val warnings = mutableListOf<String>()
    private val registry = StorageProviderRegistry { warnings += it }

    private val generator = SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).with(SettingsModule()).build())

    private fun variants(generate: (Class<*>) -> JsonNode = { generator.generateSchema(it) }): StorageProviderVariants =
        StorageProviderVariants(registry, generate) { warnings += it }

    private fun servedWith(variants: StorageProviderVariants): JsonNode =
        DEFAULT_OBJECT_MAPPER.readTree(variants.apply(served.toByteArray()))

    private fun JsonNode.storageVariants(): List<JsonNode> =
        at("/properties/repositories/items/properties/storageProvider/oneOf").toList()

    private fun List<JsonNode>.consts(): List<String> =
        map { it.at("/properties/type/const").asText() }

    @Test
    fun `should offer exactly the two built-in variants when no plugin factory is registered`() {
        val variants = servedWith(variants())

        assertThat(variants.storageVariants().consts())
            .describedAs("constant types of the served storageProvider variants")
            .containsExactly("fs", "s3")
        assertThat(warnings).describedAs("warnings").isEmpty()
    }

    @Test
    fun `should append one variant whose type is the constant of a plugin factory`() {
        registry.register(FakeFactory("fake-store", FakeStoreSettings::class.java), source = "fake-store-plugin.jar", builtIn = false)

        val offered = servedWith(variants()).storageVariants()

        assertThat(offered.consts())
            .describedAs("constant types of the served storageProvider variants")
            .containsExactly("fs", "s3", "fake-store")
        assertThat(offered.last().at("/properties/bucket/title").asText())
            .describedAs("title of the bucket field of the plugin variant")
            .isEqualTo("Bucket")
    }

    @Test
    fun `should list plugin variants sorted by type id whatever the registration order`() {
        registry.register(FakeFactory("gcs", FakeStoreSettings::class.java), source = "gcs.jar", builtIn = false)
        registry.register(FakeFactory("azure-blob", FakeStoreSettings::class.java), source = "azure.jar", builtIn = false)

        val offered = servedWith(variants()).storageVariants()

        assertThat(offered.consts())
            .describedAs("constant types of the served storageProvider variants")
            .containsExactly("fs", "s3", "azure-blob", "gcs")
    }

    @Test
    fun `should leave out a plugin whose settings schema cannot be generated and warn with its type and source`() {
        registry.register(FakeFactory("broken", BrokenSettings::class.java), source = "broken-store.jar", builtIn = false)
        registry.register(FakeFactory("fake-store", FakeStoreSettings::class.java), source = "fake-store-plugin.jar", builtIn = false)
        val generate: (Class<*>) -> JsonNode = {
            if (it == BrokenSettings::class.java) error("no schema for the broken fixture") else generator.generateSchema(it)
        }

        val offered = servedWith(variants(generate)).storageVariants()

        assertThat(offered.consts())
            .describedAs("constant types of the served storageProvider variants")
            .containsExactly("fs", "s3", "fake-store")
        assertThat(warnings.single())
            .describedAs("warning")
            .contains("broken", "broken-store.jar")
    }

}
