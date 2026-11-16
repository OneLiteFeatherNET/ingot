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

import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.IngotStorageProviderFactory
import net.onelitefeather.ingot.storage.STORAGE_PROVIDER_API_VERSION
import net.onelitefeather.ingot.storage.StorageProviderContext

/**
 * The factory of the `gcs` storage type. The server finds it through the service file of the plugin jar and builds one provider per
 * repository. The settings are checked first, so a wrong key stops the repository before any client is built.
 *
 * [emulatorEndpointAllowed] is the value of `INGOT_GCS_ALLOW_EMULATOR_ENDPOINT`. The server does not hand plugins its environment yet, so
 * the production factory is built with `false`, and an `endpoint` is refused in every installation. Tests build the factory with `true`.
 */
class GcsStorageProviderFactory(
    private val emulatorEndpointAllowed: Boolean = false,
) : IngotStorageProviderFactory<GcsStorageProvider, GcsStorageProviderSettings> {

    override val apiVersion: Int = STORAGE_PROVIDER_API_VERSION

    override val type: String = GCS_STORAGE_TYPE

    override val settingsType: Class<GcsStorageProviderSettings> = GcsStorageProviderSettings::class.java

    override fun create(context: StorageProviderContext, settings: GcsStorageProviderSettings): GcsStorageProvider {
        settings.validationError(emulatorEndpointAllowed)?.let { error(it) }

        return GcsStorageProvider(
            failureFacade = context.failureFacade,
            client = GcsSdkObjectClient(
                storage = storageOptionsOf(settings).build().service,
                bucket = settings.bucket,
            ),
            objectPrefix = settings.objectPrefix(),
            chunkSize = settings.chunkSize.toInt(),
            bucket = settings.bucket,
        )
    }

}
