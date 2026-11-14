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

package net.onelitefeather.ingot.storage

import com.reposilite.journalist.Journalist
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.StorageProviderFactory
import com.reposilite.storage.StorageProviderSettings
import java.nio.file.Path

/** The storage provider API version of this server. A factory declares the version it was built for. */
const val STORAGE_PROVIDER_API_VERSION: Int = 1

/** Marks the storage provider API that is still experimental. Using it without an opt-in produces a compiler warning. */
@RequiresOptIn(message = "The Ingot storage provider API is experimental", level = RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalIngotStorageApi

/** What the server hands to a plugin factory. Plugins receive a context, they never build one. */
@ExperimentalIngotStorageApi
interface StorageProviderContext {

    val journalist: Journalist
    val failureFacade: FailureFacade
    val workingDirectory: Path
    val repositoryName: String

    companion object {

        internal fun of(
            journalist: Journalist,
            failureFacade: FailureFacade,
            workingDirectory: Path,
            repositoryName: String,
        ): StorageProviderContext = DefaultStorageProviderContext(journalist, failureFacade, workingDirectory, repositoryName)

    }

}

/**
 * A storage factory of a plugin. The registry accepts it only when [apiVersion] equals [STORAGE_PROVIDER_API_VERSION]. The inherited
 * method delegates to [create] with a context, so the server always calls the method that receives the context.
 */
@ExperimentalIngotStorageApi
interface IngotStorageProviderFactory<PROVIDER : StorageProvider, SETTINGS : StorageProviderSettings> :
    StorageProviderFactory<PROVIDER, SETTINGS> {

    val apiVersion: Int

    fun create(context: StorageProviderContext, settings: SETTINGS): PROVIDER

    override fun create(
        journalist: Journalist,
        failureFacade: FailureFacade,
        workingDirectory: Path,
        repositoryName: String,
        settings: SETTINGS,
    ): PROVIDER = create(StorageProviderContext.of(journalist, failureFacade, workingDirectory, repositoryName), settings)

}

private class DefaultStorageProviderContext(
    override val journalist: Journalist,
    override val failureFacade: FailureFacade,
    override val workingDirectory: Path,
    override val repositoryName: String,
) : StorageProviderContext
