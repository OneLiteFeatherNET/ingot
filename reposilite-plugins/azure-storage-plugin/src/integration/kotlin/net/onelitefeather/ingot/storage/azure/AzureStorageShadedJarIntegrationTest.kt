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

package net.onelitefeather.ingot.storage.azure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.util.jar.JarFile

/**
 * The shaded jar that the server loads, read as an artifact: the libraries of the SDK sit under the shaded package, and the jar carries
 * no class of the server. The jar is built by the shadow task that this suite depends on, and its path comes from the build.
 */
internal class AzureStorageShadedJarIntegrationTest {

    private val jar = File(checkNotNull(System.getProperty("ingot.azure.jar")) { "the shaded jar path is not set" })

    private fun entries(): List<String> =
        JarFile(jar).use { file -> file.entries().asSequence().map { it.name }.toList() }

    @Test
    fun `should relocate the libraries of the sdk under the shaded package`() {
        // when: the entries of the jar are listed
        val entries = entries()

        // then: the relocated libraries are present under the shaded package, and the originals are not
        assertThat(entries.filter { it.startsWith("net/onelitefeather/ingot/storage/azure/shaded/reactor/") })
            .`as`("relocated reactor classes").isNotEmpty()
        assertThat(entries.filter { it.startsWith("net/onelitefeather/ingot/storage/azure/shaded/jackson/") })
            .`as`("relocated jackson classes").isNotEmpty()
        assertThat(entries.filter { it.startsWith("reactor/") || it.startsWith("com/fasterxml/jackson/") || it.startsWith("org/reactivestreams/") })
            .`as`("unrelocated library classes").isEmpty()
    }

    @Test
    fun `should not carry any class of the server`() {
        // when: the entries of the jar are listed
        val entries = entries()

        // then: no class of the server is in the jar, so the server's own classes are always the ones in use
        assertThat(entries.filter { it.startsWith("com/reposilite/") }).`as`("server classes").isEmpty()
        assertThat(entries).`as`("server storage SPI").doesNotContain(
            "net/onelitefeather/ingot/storage/IngotStorageProviderFactory.class",
            "com/reposilite/storage/StorageProviderFactory.class",
        )
    }

    @Test
    fun `should list the storage factory and the plugin in the service files of the jar`() {
        // when: the service files of the jar are read
        val storageFactories = JarFile(jar).use { file ->
            file.getInputStream(file.getJarEntry("META-INF/services/com.reposilite.storage.StorageProviderFactory"))
                .bufferedReader().readText()
        }
        val plugins = JarFile(jar).use { file ->
            file.getInputStream(file.getJarEntry("META-INF/services/com.reposilite.plugin.api.ReposilitePlugin"))
                .bufferedReader().readText()
        }

        // then: the Azure factory and the Azure plugin are both declared
        assertThat(storageFactories).contains("net.onelitefeather.ingot.storage.azure.AzureBlobStorageProviderFactory")
        assertThat(plugins).contains("net.onelitefeather.ingot.storage.azure.AzureStoragePlugin")
    }

}
