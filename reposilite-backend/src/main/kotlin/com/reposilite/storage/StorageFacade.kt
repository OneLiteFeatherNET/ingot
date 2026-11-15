/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.storage

import com.reposilite.ReposiliteObjectMapper
import com.reposilite.journalist.Journalist
import com.reposilite.plugin.api.Facade
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.IngotStorageProviderFactory
import net.onelitefeather.ingot.storage.StorageProviderContext
import net.onelitefeather.ingot.storage.StorageProviderNotInstalledException
import java.nio.file.Path

open class StorageFacade internal constructor(
    private val storageProviders: StorageProviderRegistry,
) : Facade {

    /** A facade that knows the built-in providers only, for callers outside the plugin start-up. */
    constructor() : this(StorageProviderRegistry.applicationClassPath())

    open fun createStorageProvider(
        journalist: Journalist,
        failureFacade: FailureFacade,
        workingDirectory: Path,
        repository: String,
        storageSettings: StorageProviderSettings,
    ): StorageProvider? =
        buildStorageProvider(journalist, failureFacade, workingDirectory, repository, storageSettings, chain = emptyList())

    /**
     * Builds a provider through its registered factory. A factory of the Ingot API builds its delegates through the context, which calls
     * this function again with the types that are being built, so a cycle is refused before any provider is created.
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildStorageProvider(
        journalist: Journalist,
        failureFacade: FailureFacade,
        workingDirectory: Path,
        repository: String,
        storageSettings: StorageProviderSettings,
        chain: List<String>,
    ): StorageProvider? {
        val factory = storageProviders.resolve(storageSettings.type) as StorageProviderFactory<*, StorageProviderSettings>?
            ?: return null
        check(storageSettings.type !in chain) {
            "storage provider cycle refused: ${(chain + storageSettings.type).joinToString(" -> ")}"
        }
        val settings = storageSettings.boundTo(factory.settingsType)
        val path = chain + storageSettings.type

        if (factory !is IngotStorageProviderFactory<*, *>) {
            return factory.create(journalist, failureFacade, workingDirectory, repository, settings)
        }
        val nested = mutableListOf<StorageProvider>()
        val context = StorageProviderContext.of(journalist, failureFacade, workingDirectory, repository, storageSettings.type) { nestedSettings ->
            buildStorageProvider(journalist, failureFacade, workingDirectory, repository, nestedSettings, path)
                ?.also { nested += it }
                ?: throw StorageProviderNotInstalledException(nestedSettings.type)
        }
        return try {
            (factory as IngotStorageProviderFactory<StorageProvider, StorageProviderSettings>).create(context, settings)
        } catch (failure: Throwable) {
            nested.asReversed().forEach { provider ->
                try {
                    provider.shutdown()
                } catch (shutdownFailure: Throwable) {
                    failure.addSuppressed(shutdownFailure)
                }
            }
            throw failure
        }
    }

    private fun StorageProviderSettings.boundTo(settingsType: Class<out StorageProviderSettings>): StorageProviderSettings =
        if (this is UnresolvedStorageProviderSettings) ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.treeToValue(raw, settingsType) else this

}
