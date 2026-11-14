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

package com.reposilite.storage

import com.reposilite.ReposiliteObjectMapper
import com.reposilite.journalist.Journalist
import com.reposilite.plugin.api.Facade
import com.reposilite.status.FailureFacade
import java.nio.file.Path

open class StorageFacade internal constructor(
    private val storageProviders: StorageProviderRegistry,
) : Facade {

    /** A facade that knows the built-in providers only, for callers outside the plugin start-up. */
    constructor() : this(StorageProviderRegistry.applicationClassPath())

    @Suppress("UNCHECKED_CAST")
    open fun createStorageProvider(
        journalist: Journalist,
        failureFacade: FailureFacade,
        workingDirectory: Path,
        repository: String,
        storageSettings: StorageProviderSettings,
    ): StorageProvider? {
        val factory = storageProviders.resolve(storageSettings.type) as StorageProviderFactory<*, StorageProviderSettings>?
            ?: return null

        return factory.create(
            journalist = journalist,
            failureFacade = failureFacade,
            workingDirectory = workingDirectory,
            repositoryName = repository,
            settings = storageSettings.boundTo(factory.settingsType)
        )
    }

    private fun StorageProviderSettings.boundTo(settingsType: Class<out StorageProviderSettings>): StorageProviderSettings =
        if (this is UnresolvedStorageProviderSettings) ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.treeToValue(raw, settingsType) else this

}
