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

@file:OptIn(ExperimentalIngotStorageApi::class)

package net.onelitefeather.ingot.storage.cache

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.StorageProviderSettings
import com.reposilite.storage.api.Location
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.STORAGE_PROVIDER_API_VERSION
import net.onelitefeather.ingot.storage.StorageProviderContext
import net.onelitefeather.ingot.storage.StorageProviderNotInstalledException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CacheStorageProviderFactoryTest {

    @TempDir
    lateinit var workingDirectory: Path

    private val release = "org/example/app/1.0/app-1.0.jar"
    private val bytes = "release-bytes".toByteArray()

    private val journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(System.out, System.err)
    }

    private fun context(delegate: (StorageProviderSettings) -> StorageProvider): StorageProviderContext =
        object : StorageProviderContext {
            override val journalist: Journalist = this@CacheStorageProviderFactoryTest.journalist
            override val failureFacade: FailureFacade = FailureFacade(this@CacheStorageProviderFactoryTest.journalist)
            override val workingDirectory: Path = this@CacheStorageProviderFactoryTest.workingDirectory
            override val repositoryName: String = "releases"
            override fun createProvider(settings: StorageProviderSettings): StorageProvider = delegate(settings)
            override fun environment(name: String): String? = null
        }

    private data class FakeDelegateSettings(override val type: String = "fake") : StorageProviderSettings

    @Test
    fun `the factory registers the cache type with the current API version`() {
        val factory = CacheStorageProviderFactory()

        assertThat(factory.type).isEqualTo("cache")
        assertThat(factory.apiVersion).isEqualTo(STORAGE_PROVIDER_API_VERSION)
    }

    @Test
    fun `the factory is listed in the service file of the plugin`() {
        val services = javaClass.classLoader
            .getResource("META-INF/services/com.reposilite.storage.StorageProviderFactory")
            ?.readText()

        assertThat(services)
            .describedAs("the registry discovers the factory only through this service file")
            .isNotNull()
            .contains(CacheStorageProviderFactory::class.java.name)
    }

    @Test
    fun `the factory builds a cache over the delegate that the context creates`() {
        val delegate = RecordingDelegate(mapOf(release to bytes))
        val settings = CacheStorageProviderSettings(delegate = FakeDelegateSettings())

        val provider = CacheStorageProviderFactory().create(context { delegate }, settings)
        val read = provider.getFile(Location.of(release)).get().use { it.readBytes() }

        assertThat(read).describedAs("the cache must serve the delegate's bytes").isEqualTo(bytes)
        assertThat(workingDirectory.resolve("cache").resolve("releases").resolve("data").resolve(release))
            .describedAs("the default directory is the cache folder of the repository")
            .exists()
    }

    @Test
    fun `the factory refuses mutable caching before it creates the delegate`() {
        var created = false
        val settings = CacheStorageProviderSettings(delegate = FakeDelegateSettings(), cacheMutable = true)

        assertThatThrownBy {
            CacheStorageProviderFactory().create(context { created = true; RecordingDelegate() }, settings)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cacheMutable")
        assertThat(created).describedAs("a refused setting must not build the delegate").isFalse()
    }

    @Test
    fun `a delegate that is not installed is refused with the reason`() {
        val settings = CacheStorageProviderSettings(delegate = FakeDelegateSettings("minio"))

        assertThatThrownBy {
            CacheStorageProviderFactory().create(context { throw StorageProviderNotInstalledException(it.type) }, settings)
        }
            .isInstanceOf(StorageProviderNotInstalledException::class.java)
            .hasMessageContaining("'minio'")
    }

}
