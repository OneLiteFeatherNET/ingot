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

import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.reposilite.configuration.shared.EnumResolver
import com.reposilite.configuration.shared.SCHEMA_OPTION_PRESET
import com.reposilite.configuration.shared.SettingsModule
import com.reposilite.configuration.shared.SubtypeResolver
import com.reposilite.configuration.shared.application.settingsSchemaGenerator
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.shared.SharedConfigurationProvider
import com.reposilite.configuration.shared.SharedSettingsProvider
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.application.MavenSettings
import com.reposilite.plugin.Extensions
import com.reposilite.shared.EntityTagSigner
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.mutableReference
import java.util.ServiceLoader

/**
 * With no storage plugin loaded, the served repository schema is the schema of `main`, byte for byte. Two golden files hold it:
 * `maven-build-schema-main.json` is the scheme the annotation processor writes at build time, which production serves, and
 * `maven-runtime-schema-main.json` is the scheme the runtime generator writes, which the tests serve because the test classpath
 * has no build-time index. Both were taken from `main` before the variants were introduced.
 */
internal class StorageProviderVariantsDropInTest {

    private class MemoryProvider : SharedConfigurationProvider {
        override fun updateConfiguration(content: String) = Unit
        override fun fetchConfiguration(): String = "{}"
        override fun isUpdateRequired(): Boolean = false
        override fun isMutable(): Boolean = false
        override fun name(): String = "memory"
    }

    private fun golden(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/storage/$name")) { "golden schema $name is missing" }
            .use { it.readBytes() }

    /** The generator of the server, see the schema generator of the shared configuration components. */
    private fun serverGenerator(): SchemaGenerator =
        SchemaGenerator(
            SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET)
                .with(
                    SettingsModule(
                        subtypeResolvers = ServiceLoader.load(SubtypeResolver::class.java).toList(),
                        enumResolvers = ServiceLoader.load(EnumResolver::class.java).toList(),
                    )
                )
                .build()
        )

    @Test
    fun `served maven schema without a storage plugin is byte-identical to the schema of main`() {
        // given: a facade of a server that has loaded no storage plugin
        val journalist = InMemoryLogger()
        val facade = SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { serverGenerator() },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(MavenSettings::class.java to mutableReference(MavenSettings()))),
            sharedConfigurationProvider = MemoryProvider(),
            extensions = Extensions(journalist),
            entityTags = EntityTagSigner.ephemeral(),
        )

        // when: the schema of the maven settings is served
        val served = facade.getSettingsReference<MavenSettings>("maven")!!.schema.get().use { it.readBytes() }

        // then: the bytes are the bytes of main
        assertThat(served)
            .describedAs("served maven schema compared with the runtime golden schema of main")
            .isEqualTo(golden("maven-runtime-schema-main.json"))
    }

    @Test
    fun `the build-time maven schema is returned unchanged when only built-in providers are registered`() {
        // given: the registry of a server with the built-in providers and no plugin
        val journalist = InMemoryLogger()
        val variants = StorageProviderVariants(StorageProviderRegistry.applicationClassPath(), { serverGenerator().generateSchema(it) })

        // when: the build-time scheme goes through the variant seam
        val served = variants.apply(golden("maven-build-schema-main.json"))

        // then: the bytes are the bytes of main
        assertThat(served)
            .describedAs("build-time maven schema after the variant seam compared with the golden schema of main")
            .isEqualTo(golden("maven-build-schema-main.json"))
    }

    @Test
    fun `served maven schema of the generator that reads the live registry is byte-identical to the schema of main without a plugin`() {
        // given: a facade whose generator reads the registry of the server, which holds no storage plugin
        val journalist = InMemoryLogger()
        val extensions = Extensions(journalist)
        val facade = SharedConfigurationFacade(
            journalist = journalist,
            schemaGenerator = lazy { settingsSchemaGenerator(extensions.storageProviders) },
            failureFacade = FailureFacade(journalist),
            sharedSettingsProvider = SharedSettingsProvider(mapOf(MavenSettings::class.java to mutableReference(MavenSettings()))),
            sharedConfigurationProvider = MemoryProvider(),
            extensions = extensions,
            entityTags = EntityTagSigner.ephemeral(),
        )

        // when: the schema of the maven settings is served
        val served = facade.getSettingsReference<MavenSettings>("maven")!!.schema.get().use { it.readBytes() }

        // then: the bytes are the bytes of main
        assertThat(served)
            .describedAs("served maven schema of the live registry generator compared with the runtime golden schema of main")
            .isEqualTo(golden("maven-runtime-schema-main.json"))
    }

}
