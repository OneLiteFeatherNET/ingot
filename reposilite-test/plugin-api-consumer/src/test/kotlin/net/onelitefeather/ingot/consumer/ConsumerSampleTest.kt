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

package net.onelitefeather.ingot.consumer

import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConsumerSampleTest {

    // The marker has BINARY retention, so reflection does not see it. The class file of the sample carries the annotation
    // descriptor in its annotations attribute, and the test reads that from the class file.
    @Test
    fun `sample class carries the experimental marker in its class file`() {
        val resource = ConsumerSample::class.java.name.replace('.', '/') + ".class"
        val bytes = checkNotNull(ConsumerSample::class.java.classLoader.getResourceAsStream(resource)) {
            "class file $resource is not on the test classpath"
        }.use { it.readBytes() }

        assertThat(String(bytes, Charsets.ISO_8859_1))
            .`as`("the sample class file must reference the experimental marker annotation")
            .contains("L" + ExperimentalIngotPluginApi::class.java.name.replace('.', '/') + ";")
    }

    @Test
    fun `experimental marker is an annotation type loaded from the plugin api jar`() {
        val marker = Class.forName("net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi")

        assertThat(marker.isAnnotation)
            .`as`("ExperimentalIngotPluginApi must be an annotation type")
            .isTrue()
    }
}
