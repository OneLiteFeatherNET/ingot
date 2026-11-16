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

package net.onelitefeather.ingot.storage.cache

import com.reposilite.storage.StorageProvider
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.IngotStorageProviderFactory
import net.onelitefeather.ingot.storage.STORAGE_PROVIDER_API_VERSION
import net.onelitefeather.ingot.storage.StorageProviderContext

/**
 * Registers the storage type `cache`. The settings are checked first, so a refused setting never builds the delegate. The delegate is
 * built through the context, which is how a delegate of any installed type is reached.
 */
class CacheStorageProviderFactory : IngotStorageProviderFactory<StorageProvider, CacheStorageProviderSettings> {

    override val type: String = CACHE_STORAGE_TYPE
    override val settingsType: Class<CacheStorageProviderSettings> = CacheStorageProviderSettings::class.java
    override val apiVersion: Int = STORAGE_PROVIDER_API_VERSION

    override fun create(context: StorageProviderContext, settings: CacheStorageProviderSettings): StorageProvider {
        val checked = settings.validated()
        val delegate = context.createProvider(checked.delegate)
        return CacheStorageProvider(
            delegate = delegate,
            directory = checked.cacheDirectory(context.workingDirectory, context.repositoryName),
            maxFileSize = parseOptionalCacheSize("maxFileSize", checked.maxFileSize),
            maxSize = parseCacheSize("maxSize", checked.maxSize),
            cacheOnUpload = checked.cacheOnUpload,
            repositoryName = context.repositoryName,
            onFailure = { context.failureFacade.logger.error("Storage cache of '${context.repositoryName}': $it") },
        )
    }

}
