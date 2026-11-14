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

import com.reposilite.journalist.Journalist
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.IngotStorageProviderFactory
import net.onelitefeather.ingot.storage.STORAGE_PROVIDER_API_VERSION
import net.onelitefeather.ingot.storage.StorageProviderContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URLClassLoader
import java.nio.file.Path

@OptIn(ExperimentalIngotStorageApi::class)
class StorageProviderRegistryTest {

    private class PlainFactory(override val type: String) : StorageProviderFactory<StorageProvider, StorageProviderSettings> {
        override val settingsType: Class<StorageProviderSettings> = StorageProviderSettings::class.java
        override fun create(
            journalist: Journalist,
            failureFacade: FailureFacade,
            workingDirectory: Path,
            repositoryName: String,
            settings: StorageProviderSettings,
        ): StorageProvider = error("the registry tests never create a provider")
    }

    private class VersionedFactory(override val type: String, override val apiVersion: Int) :
        IngotStorageProviderFactory<StorageProvider, StorageProviderSettings> {
        override val settingsType: Class<StorageProviderSettings> = StorageProviderSettings::class.java
        override fun create(context: StorageProviderContext, settings: StorageProviderSettings): StorageProvider =
            error("the registry tests never create a provider")
    }

    private val warnings = mutableListOf<String>()
    private val registry = StorageProviderRegistry { warnings += it }

    @Test
    fun `should register a factory of the current API version`() {
        val registered = registry.register(VersionedFactory("fixture", STORAGE_PROVIDER_API_VERSION), source = "fixture.jar", builtIn = false)

        assertThat(registered).describedAs("registration result").isTrue()
        assertThat(registry.resolve("fixture")).describedAs("resolved factory").isNotNull()
        assertThat(warnings).describedAs("warnings").isEmpty()
    }

    @Test
    fun `should refuse a factory of another API version and name both versions`() {
        val otherVersion = STORAGE_PROVIDER_API_VERSION + 1

        assertThat(registry.register(VersionedFactory("fixture", otherVersion), source = "fixture.jar", builtIn = false)).isFalse()
        assertThat(registry.resolve("fixture")).describedAs("resolved factory").isNull()
        assertThat(warnings.single())
            .describedAs("warning")
            .contains(VersionedFactory::class.java.name, "$otherVersion", "$STORAGE_PROVIDER_API_VERSION")
    }

    @Test
    fun `should refuse a plain factory from a plugin jar and name the jar`() {
        assertThat(registry.register(PlainFactory("fixture"), source = "pluginA.jar", builtIn = false)).isFalse()
        assertThat(registry.resolve("fixture")).describedAs("resolved factory").isNull()
        assertThat(warnings.single()).describedAs("warning").contains("pluginA.jar")
    }

    @Test
    fun `should accept a plain factory of the application class path`() {
        assertThat(registry.register(PlainFactory("fs"), source = "application", builtIn = true)).isTrue()
        assertThat(registry.resolve("fs")).describedAs("resolved factory").isNotNull()
    }

    @Test
    fun `should refuse a duplicate id and name both sources`() {
        registry.register(PlainFactory("fixture"), source = "application", builtIn = true)

        assertThat(registry.register(VersionedFactory("fixture", STORAGE_PROVIDER_API_VERSION), source = "pluginB.jar", builtIn = false)).isFalse()
        assertThat(warnings.single()).describedAs("warning").contains("pluginB.jar", "application")
    }

    @Test
    fun `should keep a built-in provider when a plugin claims its id`() {
        val builtIn = PlainFactory("s3")
        registry.register(builtIn, source = "application", builtIn = true)

        registry.register(VersionedFactory("s3", STORAGE_PROVIDER_API_VERSION), source = "pluginC.jar", builtIn = false)

        assertThat(registry.resolve("s3")).describedAs("factory of s3").isSameAs(builtIn)
    }

    @Test
    fun `should refuse an id that is not lower-case kebab-case and name the class`() {
        assertThat(registry.register(VersionedFactory("Azure_Blob", STORAGE_PROVIDER_API_VERSION), source = "pluginD.jar", builtIn = false)).isFalse()
        assertThat(warnings.single()).describedAs("warning").contains(VersionedFactory::class.java.name)
    }

    @Test
    fun `should refuse a blank type`() {
        assertThat(registry.register(VersionedFactory("", STORAGE_PROVIDER_API_VERSION), source = "pluginE.jar", builtIn = false)).isFalse()
        assertThat(registry.types()).describedAs("registered types").isEmpty()
    }

    @Test
    fun `should not register a built-in factory twice when a plugin loader lists it again`() {
        registry.discover(StorageProviderFactory::class.java.classLoader, builtIn = true)
        val pluginLoader = URLClassLoader(emptyArray(), StorageProviderFactory::class.java.classLoader)

        registry.discover(pluginLoader, builtIn = false)

        assertThat(registry.types()).describedAs("registered types").containsExactly("fs", "s3")
        assertThat(warnings).describedAs("warnings").isEmpty()
    }

}
