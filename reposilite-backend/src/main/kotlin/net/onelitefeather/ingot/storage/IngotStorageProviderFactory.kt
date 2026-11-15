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

    /**
     * Builds the provider of another registered storage type from [settings], for a plugin that wraps a delegate. The delegate is built
     * by the same registry, so it may be of any installed type except the one that is being built.
     *
     * The default throws [UnsupportedOperationException]. The server overrides it for every context it hands out, so a plugin that runs
     * on this server never sees the default. The server refuses a type that no installed plugin registers with
     * [StorageProviderNotInstalledException], and a type that is already being built in the same chain with [IllegalStateException]
     * whose message names the chain.
     */
    fun createProvider(settings: StorageProviderSettings): StorageProvider =
        throw UnsupportedOperationException("This server does not build nested storage providers for '${settings.type}'")

    /**
     * The value of an environment setting of this storage provider, for example `AZURE_ACCOUNT` for the variable `INGOT_AZURE_ACCOUNT`.
     * The INGOT variable is read first, and the REPOSILITE variable is the fallback when the INGOT one is unset or blank. Null when
     * neither is set.
     *
     * A provider reads only its own namespace: the upper case type followed by an underscore, so the provider `azure` reads `AZURE_*`
     * names and nothing else. The core settings under `SETTINGS_`, `LOCAL_` and `CLUSTER_` are refused for every provider, even for one
     * whose type is named after them.
     *
     * This is not a secret store. A value is plain text in the environment of the process. The server neither encrypts it nor masks it
     * in the console or in logs. Credentials belong in the settings, behind the secret references the operator allows.
     *
     * @throws IllegalArgumentException when [name] is not made of upper case letters, digits and underscores, lies outside the namespace
     * of this provider, or is reserved for the server
     */
    fun environment(name: String): String? =
        throw UnsupportedOperationException("This server does not read environment settings for '$name'")

    companion object {

        internal fun of(
            journalist: Journalist,
            failureFacade: FailureFacade,
            workingDirectory: Path,
            repositoryName: String,
            providerType: String,
            environment: (String) -> String? = { System.getenv(it) },
            nested: (StorageProviderSettings) -> StorageProvider = { throw StorageProviderNotInstalledException(it.type) },
        ): StorageProviderContext =
            DefaultStorageProviderContext(journalist, failureFacade, workingDirectory, repositoryName, providerType, environment, nested)

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
    ): PROVIDER =
        create(StorageProviderContext.of(journalist, failureFacade, workingDirectory, repositoryName, type), settings)

}

private class DefaultStorageProviderContext(
    override val journalist: Journalist,
    override val failureFacade: FailureFacade,
    override val workingDirectory: Path,
    override val repositoryName: String,
    private val providerType: String,
    private val variables: (String) -> String?,
    private val nested: (StorageProviderSettings) -> StorageProvider,
) : StorageProviderContext {

    override fun createProvider(settings: StorageProviderSettings): StorageProvider =
        nested(settings)

    override fun environment(name: String): String? {
        require(ENVIRONMENT_NAME.matches(name)) {
            "'$name' is not an environment setting name, use upper case letters, digits and underscores"
        }
        require(RESERVED_ENVIRONMENT_PREFIXES.none { name.startsWith(it) }) {
            "'$name' is reserved for the server and cannot be read by a storage provider"
        }
        val namespace = providerType.uppercase().replace('-', '_') + "_"
        require(name.startsWith(namespace)) {
            "'$name' is outside the '$namespace' namespace of storage provider '$providerType'"
        }
        return variables("INGOT_$name") ?: variables("REPOSILITE_$name")
    }

}

private val ENVIRONMENT_NAME = Regex("[A-Z][A-Z0-9_]*")

/** The prefixes of the core settings and the core secrets. No storage provider reads them, whatever its type is named. */
private val RESERVED_ENVIRONMENT_PREFIXES = listOf("SETTINGS_", "LOCAL_", "CLUSTER_")
