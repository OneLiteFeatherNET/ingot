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

package net.onelitefeather.ingot.storage.gcs

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Sensitive
import com.reposilite.storage.StorageProviderSettings
import io.javalin.openapi.Custom
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi

/** The storage type that this plugin registers. It is written as this value and is not configurable. */
internal const val GCS_STORAGE_TYPE = "gcs"

/** The authentication mode that uses Application Default Credentials, which covers GKE workload identity. */
internal const val AUTHENTICATION_ADC = "adc"

/** The authentication mode that uses the service account key of [GcsStorageProviderSettings.credentialsJson]. */
internal const val AUTHENTICATION_SERVICE_ACCOUNT_KEY = "service-account-key"

/** The unit of a resumable upload chunk, and the smallest chunk size. */
internal const val CHUNK_UNIT: Long = 256L * 1024

/** The largest chunk size of a resumable upload. */
internal const val MAX_CHUNK_SIZE: Long = 64L * 1024 * 1024

/** The default chunk size: 8 MiB, which is 32 chunk units. */
internal const val DEFAULT_CHUNK_SIZE: Long = 8L * 1024 * 1024

/** The name of the process-wide switch that allows the [GcsStorageProviderSettings.endpoint] key. */
internal const val EMULATOR_SWITCH = "INGOT_GCS_ALLOW_EMULATOR_ENDPOINT"

@Doc(title = "Google Cloud Storage Provider", description = "Google Cloud Storage bucket storage provider settings")
data class GcsStorageProviderSettings(
    @get:Doc(title = "Project", description = "Optional project for billing and quota. Empty leaves the client default (optional)")
    val projectId: String = "",
    @get:Doc(title = "Bucket", description = "The bucket that holds the repository")
    val bucket: String = "",
    @get:Doc(title = "Prefix", description = "Optional prefix prepended to all object names, e.g. to share a bucket between repositories")
    val prefix: String = "",
    @get:Doc(title = "Endpoint", description = "Emulator endpoint only. Used when INGOT_GCS_ALLOW_EMULATOR_ENDPOINT is true (optional)")
    val endpoint: String = "",
    @get:Doc(title = "Authentication", description = "One of adc (Application Default Credentials) or service-account-key")
    val authentication: String = AUTHENTICATION_ADC,
    @get:Sensitive
    @get:Doc(title = "Credentials", description = "The service account key as JSON, for the service-account-key mode")
    val credentialsJson: String = "",
    @get:Doc(title = "Chunk Size", description = "Bytes per resumable upload chunk: a multiple of 256 KiB, between 256 KiB and 64 MiB")
    val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) : StorageProviderSettings {

    @get:Custom(name = "const", value = GCS_STORAGE_TYPE)
    override val type: String = GCS_STORAGE_TYPE

    /**
     * Why these settings cannot be used, or null when they can. The message names the key that is wrong and never a value. The emulator
     * switch is passed in, because the factory reads it; a settings object does not know the environment of the process.
     */
    fun validationError(emulatorEndpointAllowed: Boolean): String? = when {
        bucket.isBlank() ->
            "Google Cloud Storage needs the field 'bucket'"
        authentication != AUTHENTICATION_ADC && authentication != AUTHENTICATION_SERVICE_ACCOUNT_KEY ->
            "Google Cloud Storage has no authentication '$authentication', use adc or service-account-key"
        authentication == AUTHENTICATION_ADC && credentialsJson.isNotBlank() ->
            "Google Cloud Storage with authentication 'adc' does not read the field 'credentialsJson', remove it or use service-account-key"
        authentication == AUTHENTICATION_SERVICE_ACCOUNT_KEY && credentialsJson.isBlank() ->
            "Google Cloud Storage with authentication 'service-account-key' needs the field 'credentialsJson'"
        chunkSize < CHUNK_UNIT || chunkSize > MAX_CHUNK_SIZE || chunkSize % CHUNK_UNIT != 0L ->
            "Google Cloud Storage needs the field 'chunkSize' to be a multiple of 256 KiB between 256 KiB and 64 MiB"
        endpoint.isNotBlank() && !emulatorEndpointAllowed ->
            "Google Cloud Storage uses the field 'endpoint' only when $EMULATOR_SWITCH is true"
        else -> null
    }

}
