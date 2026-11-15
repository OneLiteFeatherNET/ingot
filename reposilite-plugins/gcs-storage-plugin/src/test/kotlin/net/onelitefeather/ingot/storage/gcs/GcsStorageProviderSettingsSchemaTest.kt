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

import com.reposilite.configuration.shared.api.Sensitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The schema of the settings that the dashboard form is built from: its keys, and the only sensitive key. */
internal class GcsStorageProviderSettingsSchemaTest {

    private val properties = GcsStorageProviderSettings::class.java.methods
        .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.declaringClass == GcsStorageProviderSettings::class.java }

    @Test
    fun `should expose exactly the keys of the settings table`() {
        // when: the getters of the settings are listed
        val keys = properties.map { it.name.removePrefix("get").replaceFirstChar { first -> first.lowercase() } }.toSet()

        // then: the keys are the ones of the design table, and the type is among them
        assertThat(keys).`as`("settings keys").containsExactlyInAnyOrder(
            "type", "projectId", "bucket", "prefix", "endpoint", "authentication", "credentialsJson", "chunkSize",
        )
    }

    @Test
    fun `should mark only the credentials as sensitive`() {
        // when: the getters that carry the sensitive annotation are listed
        val sensitive = properties.filter { it.isAnnotationPresent(Sensitive::class.java) }.map { it.name }

        // then: the service account key is the only sensitive value, so no other key is written to a log or a response
        assertThat(sensitive).`as`("sensitive getters").containsExactly("getCredentialsJson")
    }

}
