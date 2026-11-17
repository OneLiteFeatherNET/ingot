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

@file:OptIn(net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi::class)

package net.onelitefeather.ingot.storage.azure

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageProviderFactory
import com.reposilite.storage.StorageEntrySource
import net.onelitefeather.ingot.storage.STORAGE_PROVIDER_API_VERSION
import net.onelitefeather.ingot.storage.StorageProviderContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.ServiceLoader

/** The factory of the `azure-blob` type: its identity, its refusals and the service files that register it. */
internal class AzureBlobStorageProviderFactoryTest {

    @TempDir
    lateinit var workingDirectory: Path

    /** A context whose environment is [variables], keyed by the name without the INGOT_ or REPOSILITE_ prefix. */
    private fun context(variables: Map<String, String> = emptyMap()): StorageProviderContext {
        val logger: Journalist = InMemoryLogger()
        val root = workingDirectory
        return object : StorageProviderContext {
            override val journalist: Journalist = logger
            override val failureFacade: FailureFacade = FailureFacade(logger)
            override val workingDirectory: Path = root
            override val repositoryName: String = "repository"
            override fun environment(name: String): String? = variables[name]
        }
    }

    private val factory = AzureBlobStorageProviderFactory()

    @Test
    fun `should declare the azure-blob type and the current storage provider API version`() {
        // then: the factory identifies itself as the Azure type and the version this server accepts
        assertThat(factory.type).`as`("type").isEqualTo("azure-blob")
        assertThat(factory.apiVersion).`as`("API version").isEqualTo(STORAGE_PROVIDER_API_VERSION)
        assertThat(factory.settingsType).`as`("settings type").isEqualTo(AzureBlobStorageProviderSettings::class.java)
    }

    @Test
    fun `should create a provider for default credentials without contacting the service`() {
        // when: a repository is built from its settings, with no request to Azure
        val provider = factory.create(context(), AzureBlobStorageProviderSettings(accountName = "account", container = "repositories"))

        // then: the provider exists, and it does not claim a native listing (design D13)
        assertThat(provider).isNotNull()
        assertThat(provider).`as`("native entry source").isNotInstanceOf(StorageEntrySource::class.java)
    }

    @Test
    fun `should refuse a repository without a container and name the field`() {
        // when: the container is missing
        val refusal = assertThatThrownBy {
            factory.create(context(), AzureBlobStorageProviderSettings(accountName = "account"))
        }

        // then: the refusal names the field
        refusal.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("container")
    }

    @Test
    fun `should refuse a repository whose authentication lacks its secret without showing any value`() {
        // when: the account key mode is selected with an empty key, and a SAS token that must stay hidden
        val refusal = assertThatThrownBy {
            factory.create(
                context(),
                AzureBlobStorageProviderSettings(
                    accountName = "account",
                    container = "repositories",
                    authentication = "account-key",
                    sasToken = "hidden-sas-token-value",
                ),
            )
        }

        // then: the refusal names the account key and never prints the SAS token
        refusal.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("accountKey")
            .hasMessageNotContaining("hidden-sas-token-value")
    }

    @Test
    fun `should take the account name from the environment when the settings leave it blank`() {
        // when: the repository names no account, and the process variable of the account is set
        val provider = factory.create(
            context(mapOf("AZURE_BLOB_ACCOUNT_NAME" to "envaccount")),
            AzureBlobStorageProviderSettings(container = "repositories"),
        )

        // then: the repository is built instead of being refused for a missing account
        assertThat(provider).`as`("provider from the environment account").isNotNull()
    }

    @Test
    fun `should refuse a blank account name when neither the settings nor the environment name one`() {
        // when: the repository names no account and the environment is empty
        val refusal = assertThatThrownBy {
            factory.create(context(), AzureBlobStorageProviderSettings(container = "repositories"))
        }

        // then: the refusal still names the account field
        refusal.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("accountName")
    }

    @Test
    fun `should be found through the storage provider service file of the jar`() {
        // when: the storage provider factories of the class path are listed as the server lists them
        val factories = ServiceLoader.load(StorageProviderFactory::class.java).map { it.javaClass.name }

        // then: the Azure factory is among them
        assertThat(factories).`as`("registered factories").contains(AzureBlobStorageProviderFactory::class.java.name)
    }

}
