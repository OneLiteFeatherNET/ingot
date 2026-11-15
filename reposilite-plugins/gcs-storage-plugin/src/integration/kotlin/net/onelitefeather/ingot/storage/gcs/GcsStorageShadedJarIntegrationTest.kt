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

package net.onelitefeather.ingot.storage.gcs

import com.reposilite.storage.StorageProviderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.jar.JarFile

/**
 * The shaded jar, loaded the way the server loads a plugin: its factory is found through the service file, its copy of the SDK is
 * relocated, and the SDK starts from the jar. The path of the jar is passed by the build as `ingot.gcs.jar`.
 */
internal class GcsStorageShadedJarIntegrationTest {

    private val jar = File(checkNotNull(System.getProperty("ingot.gcs.jar")) { "The build must pass the path of the shaded jar" })

    /**
     * A loader of the jar that prefers the classes of the jar, as the server's plugin loader does for a plugin that the server does not
     * carry itself. The server's own types (the storage API) are not in the jar, so they still come from the parent.
     */
    private fun pluginLoader(): ChildFirstLoader =
        ChildFirstLoader(arrayOf(jar.toURI().toURL()), StorageProviderFactory::class.java.classLoader)

    private class ChildFirstLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {

        override fun loadClass(name: String, resolve: Boolean): Class<*> =
            synchronized(getClassLoadingLock(name)) {
                findLoadedClass(name) ?: try {
                    findClass(name)
                } catch (_: ClassNotFoundException) {
                    super.loadClass(name, resolve)
                }
            }

    }

    @Test
    fun `should relocate the copies of guava, protobuf and gax into the shaded package`() {
        // when: the entries of the jar are listed
        val names = JarFile(jar).use { file -> file.entries().asSequence().map { it.name }.toList() }

        // then: no class of those libraries sits under its original package, and the relocated copy of guava is there
        val unrelocated = names.filter {
            it.startsWith("com/google/common/") || it.startsWith("com/google/protobuf/") || it.startsWith("com/google/api/gax/")
        }
        assertThat(unrelocated).`as`("entries under the original packages").isEmpty()
        assertThat(names.any { it.startsWith("net/onelitefeather/ingot/storage/gcs/shaded/com/google/common/") })
            .`as`("relocated guava").isTrue()
    }

    @Test
    fun `should list the gcs factory in the service file of the jar`() {
        // when: the service file of the storage provider factories is read from the jar
        val factories = JarFile(jar).use { file ->
            val entry = checkNotNull(file.getEntry("META-INF/services/com.reposilite.storage.StorageProviderFactory")) { "no service file" }
            file.getInputStream(entry).bufferedReader().readLines()
        }

        // then: the gcs factory is listed there
        assertThat(factories).`as`("service file").containsExactly("net.onelitefeather.ingot.storage.gcs.GcsStorageProviderFactory")
    }

    @Test
    fun `should find the gcs factory through the service loader of the jar`() {
        // when: the storage provider factories are listed with the plugin loader
        pluginLoader().use { loader ->
            val factories = ServiceLoader.load(StorageProviderFactory::class.java, loader).map { it.javaClass.name }.toList()

            // then: the gcs factory is among them
            assertThat(factories).`as`("registered factories").contains("net.onelitefeather.ingot.storage.gcs.GcsStorageProviderFactory")
        }
    }

    @Test
    fun `should start the sdk from the jar with its relocated libraries`() {
        // when: the storage options are built and the service is created with the loader of the jar
        pluginLoader().use { loader ->
            val optionsClass = loader.loadClass("com.google.cloud.storage.StorageOptions")
            val builder = optionsClass.getMethod("newBuilder").invoke(null)
            val options = builder.javaClass.getMethod("build").invoke(builder)
            val service = options.javaClass.getMethod("getService").invoke(options)

            // then: the service object comes from the jar, so the libraries it needs resolved there
            assertThat(service).`as`("storage service").isNotNull()
            assertThat(File(service.javaClass.protectionDomain.codeSource.location.toURI()).name)
                .`as`("jar of the service class")
                .isEqualTo(jar.name)
        }
    }

}
