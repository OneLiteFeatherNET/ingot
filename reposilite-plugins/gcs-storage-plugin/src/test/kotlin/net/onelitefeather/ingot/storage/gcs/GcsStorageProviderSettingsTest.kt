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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The settings rules of design D2: defaults, required keys, the chunk size, the authentication modes and the emulator guard. */
internal class GcsStorageProviderSettingsTest {

    private val kib = 1024L
    private val mib = 1024L * kib

    private fun settings(
        bucket: String = "repositories",
        authentication: String = "adc",
        credentialsJson: String = "",
        endpoint: String = "",
        chunkSize: Long = 8 * mib,
    ) = GcsStorageProviderSettings(
        bucket = bucket,
        authentication = authentication,
        credentialsJson = credentialsJson,
        endpoint = endpoint,
        chunkSize = chunkSize,
    )

    @Test
    fun `should use adc, an empty prefix and endpoint and an 8 MiB chunk size by default`() {
        // when: a repository sets only the bucket
        val settings = GcsStorageProviderSettings(bucket = "repositories")

        // then: the defaults of design D2 apply
        assertThat(settings.authentication).`as`("authentication").isEqualTo("adc")
        assertThat(settings.prefix).`as`("prefix").isEmpty()
        assertThat(settings.endpoint).`as`("endpoint").isEmpty()
        assertThat(settings.chunkSize).`as`("chunk size").isEqualTo(8 * mib)
        assertThat(settings.validationError(emulatorEndpointAllowed = false)).`as`("validation error").isNull()
    }

    @Test
    fun `should always report the type gcs`() {
        // then: the type is the fixed value of the plugin, whatever the other keys are
        assertThat(GcsStorageProviderSettings(bucket = "repositories").type).isEqualTo("gcs")
    }

    @Test
    fun `should refuse an empty bucket and name the bucket key`() {
        // when: the bucket is missing
        val error = settings(bucket = "").validationError(emulatorEndpointAllowed = false)

        // then: the message names the key
        assertThat(error).`as`("validation error").isNotNull().contains("bucket")
    }

    @Test
    fun `should refuse a chunk size that is not a multiple of 256 KiB and name chunkSize`() {
        // when: the chunk size is 8 MiB plus one byte
        val error = settings(chunkSize = 8 * mib + 1).validationError(emulatorEndpointAllowed = false)

        // then: the message names the key
        assertThat(error).`as`("validation error").isNotNull().contains("chunkSize")
    }

    @Test
    fun `should refuse a chunk size below 256 KiB and name chunkSize`() {
        // when: the chunk size is below the minimum
        val error = settings(chunkSize = 128 * kib).validationError(emulatorEndpointAllowed = false)

        // then: the message names the key
        assertThat(error).`as`("validation error").isNotNull().contains("chunkSize")
    }

    @Test
    fun `should refuse a chunk size above 64 MiB and name chunkSize`() {
        // when: the chunk size is above the maximum
        val error = settings(chunkSize = 65 * mib).validationError(emulatorEndpointAllowed = false)

        // then: the message names the key
        assertThat(error).`as`("validation error").isNotNull().contains("chunkSize")
    }

    @Test
    fun `should accept the smallest and the largest chunk size`() {
        // then: both bounds are valid chunk sizes
        assertThat(settings(chunkSize = 256 * kib).validationError(emulatorEndpointAllowed = false)).isNull()
        assertThat(settings(chunkSize = 64 * mib).validationError(emulatorEndpointAllowed = false)).isNull()
    }

    @Test
    fun `should refuse an unknown authentication mode and name authentication`() {
        // when: the mode is not one of the two modes of design D2
        val error = settings(authentication = "metadata").validationError(emulatorEndpointAllowed = false)

        // then: the message names the key
        assertThat(error).`as`("validation error").isNotNull().contains("authentication")
    }

    @Test
    fun `should refuse a leftover credentials key with adc and name both keys`() {
        // when: adc is selected while a key is still configured
        val error = settings(authentication = "adc", credentialsJson = "{}").validationError(emulatorEndpointAllowed = false)

        // then: the message names both keys, so the operator sees which one to remove
        assertThat(error).`as`("validation error").isNotNull().contains("credentialsJson").contains("authentication")
    }

    @Test
    fun `should refuse service-account-key without a key and name credentialsJson`() {
        // when: the key mode has no key
        val error = settings(authentication = "service-account-key").validationError(emulatorEndpointAllowed = false)

        // then: the message names the key that is missing
        assertThat(error).`as`("validation error").isNotNull().contains("credentialsJson")
    }

    @Test
    fun `should accept service-account-key with a key`() {
        // when: the key mode has a key
        val error = settings(authentication = "service-account-key", credentialsJson = "{}")
            .validationError(emulatorEndpointAllowed = false)

        // then: the settings are valid
        assertThat(error).`as`("validation error").isNull()
    }

    @Test
    fun `should refuse an endpoint without the emulator switch and name the variable`() {
        // when: an endpoint is set while the process-wide switch is off
        val error = settings(endpoint = "http://localhost:4443").validationError(emulatorEndpointAllowed = false)

        // then: the message names the variable that allows it
        assertThat(error).`as`("validation error").isNotNull().contains("INGOT_GCS_ALLOW_EMULATOR_ENDPOINT")
    }

    @Test
    fun `should accept an endpoint when the emulator switch is on`() {
        // when: an endpoint is set while the process-wide switch allows it
        val error = settings(endpoint = "http://localhost:4443").validationError(emulatorEndpointAllowed = true)

        // then: the settings are valid
        assertThat(error).`as`("validation error").isNull()
    }

}
