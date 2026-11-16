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

package net.onelitefeather.ingot.storage.cache

import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.PluginTier
import com.reposilite.plugin.api.ReposilitePlugin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

internal class StorageCachePluginTest {

    @Test
    fun `should declare the storage-cache plugin as experimental`() {
        // when: the plugin annotation is read by reflection
        val annotation = StorageCachePlugin::class.java.getAnnotation(Plugin::class.java)

        // then: the plugin is named storage-cache and declares the experimental tier
        assertThat(annotation).`as`("plugin annotation").isNotNull()
        assertThat(annotation.name).`as`("plugin name").isEqualTo("storage-cache")
        assertThat(annotation.tier).`as`("tier of storage-cache").isEqualTo(PluginTier.EXPERIMENTAL)
    }

    @Test
    fun `should list the storage-cache plugin in the plugin service file`() {
        // when: the plugins of the class path are listed as the loader lists them
        val plugins = ServiceLoader.load(ReposilitePlugin::class.java).map { it.javaClass.name }

        // then: the storage cache plugin is among them
        assertThat(plugins).`as`("registered plugins").contains(StorageCachePlugin::class.java.name)
    }

}
