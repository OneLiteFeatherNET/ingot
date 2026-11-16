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

import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

/** The plugin identity of the jar, which the plugin store and the dashboard list. */
internal class GcsStoragePluginTest {

    @Test
    fun `should identify the jar as the gcs-storage plugin`() {
        // when: the plugin annotation is read
        val annotation = GcsStoragePlugin::class.java.getAnnotation(Plugin::class.java)

        // then: the plugin is named gcs-storage
        assertThat(annotation).`as`("plugin annotation").isNotNull()
        assertThat(annotation.name).`as`("plugin name").isEqualTo("gcs-storage")
    }

    @Test
    fun `should be found through the plugin service file of the jar`() {
        // when: the plugins of the class path are listed as the loader lists them
        val plugins = ServiceLoader.load(ReposilitePlugin::class.java).map { it.javaClass.name }

        // then: the GCS plugin is among them
        assertThat(plugins).`as`("registered plugins").contains(GcsStoragePlugin::class.java.name)
    }

}
