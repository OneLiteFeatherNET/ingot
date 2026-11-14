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

package com.reposilite.storage.fixtures

import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.StorageProviderSettings
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.IngotStorageProviderFactory
import net.onelitefeather.ingot.storage.STORAGE_PROVIDER_API_VERSION
import net.onelitefeather.ingot.storage.StorageProviderContext
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.outputStream

data class FixtureStorageSettings(override val type: String = "fixture") : StorageProviderSettings

@OptIn(ExperimentalIngotStorageApi::class)
class FixtureStorageProviderFactory : IngotStorageProviderFactory<StorageProvider, FixtureStorageSettings> {

    override val type: String = "fixture"
    override val settingsType: Class<FixtureStorageSettings> = FixtureStorageSettings::class.java
    override val apiVersion: Int = STORAGE_PROVIDER_API_VERSION

    override fun create(context: StorageProviderContext, settings: FixtureStorageSettings): StorageProvider =
        error("the registry tests never create a provider")

}

/** Records, during its own initialisation, whether the fixture storage provider is already registered. */
@Plugin(name = "storage-probe")
class StorageProbePlugin : ReposilitePlugin() {

    var fixtureVisibleAtInitialize: Boolean = false

    override fun initialize(): Facade? {
        fixtureVisibleAtInitialize = extensions().storageProviders.resolve("fixture") != null
        return null
    }

}

/** Packs the given implementations under their service names into a jar, so that the test owns every byte it loads. */
internal object StorageProviderJarFixture {

    fun write(directory: Path, fileName: String, services: Map<Class<*>, List<Class<*>>>): Path {
        val jar = directory.resolve(fileName)
        val classes = services.values.flatten().toSet()
        JarOutputStream(jar.outputStream()).use { out ->
            services.forEach { (service, implementations) ->
                out.putNextEntry(JarEntry("META-INF/services/${service.name}"))
                out.write(implementations.joinToString("\n") { it.name }.toByteArray())
                out.closeEntry()
            }
            classes.forEach { implementation ->
                val entry = implementation.name.replace('.', '/') + ".class"
                out.putNextEntry(JarEntry(entry))
                requireNotNull(implementation.classLoader.getResourceAsStream(entry)) { "Missing class bytes for $entry" }
                    .use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return jar
    }

}
