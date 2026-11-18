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

package com.reposilite.storage

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Sensitive
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.IngotStorageProviderFactory
import net.onelitefeather.ingot.storage.STORAGE_PROVIDER_API_VERSION
import net.onelitefeather.ingot.storage.StorageProviderContext

/** The type id of the fake storage plugin of the registry and secret tests. */
internal const val VAULT_STORE = "vault-store"

/** Settings of a fake storage plugin with one secret, as a real plugin declares them. */
@Doc(title = "Vault store", description = "Storage of a fake plugin with a secret")
data class VaultStoreSettings(
    override val type: String = VAULT_STORE,
    @get:Doc(title = "Container", description = "Container of the vault store")
    val container: String = "",
    @get:Sensitive
    @get:Doc(title = "Account key", description = "Key of the vault store account")
    val accountKey: String = "",
) : StorageProviderSettings

/** A factory of the plugin API that is only registered, never asked for a provider. */
@OptIn(ExperimentalIngotStorageApi::class)
internal class FakePluginFactory<S : StorageProviderSettings>(
    override val type: String,
    override val settingsType: Class<S>,
) : IngotStorageProviderFactory<StorageProvider, S> {

    override val apiVersion: Int = STORAGE_PROVIDER_API_VERSION

    override fun create(context: StorageProviderContext, settings: S): StorageProvider =
        error("the plugin storage tests never create a provider")

}

/** A registry with the built-in factories and the fake vault store plugin, as a server with that plugin holds it. */
internal fun registryWithVaultStore(): StorageProviderRegistry =
    StorageProviderRegistry.applicationClassPath().also {
        it.register(FakePluginFactory(VAULT_STORE, VaultStoreSettings::class.java), source = "vault-store-plugin.jar", builtIn = false)
    }
