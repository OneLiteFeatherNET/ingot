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

import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.PluginTier
import com.reposilite.plugin.api.ReposilitePlugin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

/** The plugin identity of the jar, which the plugin store and the dashboard list. */
internal class AzureStoragePluginTest {

    @Test
    fun `should identify the jar as the azure-storage plugin`() {
        // when: the plugin annotation is read
        val annotation = AzureStoragePlugin::class.java.getAnnotation(Plugin::class.java)

        // then: the plugin is named azure-storage
        assertThat(annotation).`as`("plugin annotation").isNotNull()
        assertThat(annotation.name).`as`("plugin name").isEqualTo("azure-storage")
    }

    @Test
    fun `should declare the azure-storage plugin as experimental`() {
        // when: the plugin annotation is read by reflection
        val annotation = AzureStoragePlugin::class.java.getAnnotation(Plugin::class.java)

        // then: the plugin declares the experimental tier, because its factory opts in to the storage API
        assertThat(annotation.tier).`as`("tier of azure-storage").isEqualTo(PluginTier.EXPERIMENTAL)
    }

    @Test
    fun `should be found through the plugin service file of the jar`() {
        // when: the plugins of the class path are listed as the loader lists them
        val plugins = ServiceLoader.load(ReposilitePlugin::class.java).map { it.javaClass.name }

        // then: the Azure plugin is among them
        assertThat(plugins).`as`("registered plugins").contains(AzureStoragePlugin::class.java.name)
    }

}
