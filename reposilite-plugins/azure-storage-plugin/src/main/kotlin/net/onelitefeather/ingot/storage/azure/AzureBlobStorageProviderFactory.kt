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

package net.onelitefeather.ingot.storage.azure

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobContainerClientBuilder
import com.azure.storage.common.StorageSharedKeyCredential
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.IngotStorageProviderFactory
import net.onelitefeather.ingot.storage.STORAGE_PROVIDER_API_VERSION
import net.onelitefeather.ingot.storage.StorageProviderContext

/**
 * The factory of the `azure-blob` storage type. The server finds it through the service file of the plugin jar and builds one provider
 * per repository. Nothing here contacts the service: the first request of a repository is the first call to Azure.
 */
class AzureBlobStorageProviderFactory :
    IngotStorageProviderFactory<AzureBlobStorageProvider, AzureBlobStorageProviderSettings> {

    override val apiVersion: Int = STORAGE_PROVIDER_API_VERSION

    override val type: String = AZURE_BLOB_TYPE

    override val settingsType: Class<AzureBlobStorageProviderSettings> = AzureBlobStorageProviderSettings::class.java

    override fun create(context: StorageProviderContext, settings: AzureBlobStorageProviderSettings): AzureBlobStorageProvider {
        check(settings.container.isNotBlank()) { "Azure Blob storage needs the field 'container'" }
        check(settings.authentication == "connection-string" || settings.accountName.isNotBlank()) {
            "Azure Blob storage needs the field 'accountName' unless the authentication is connection-string"
        }
        check(settings.blockSize > 0) { "Azure Blob storage needs a positive 'blockSize'" }
        settings.authenticationError()?.let { error(it) }

        return AzureBlobStorageProvider(
            failureFacade = context.failureFacade,
            client = AzureSdkBlobClient(containerOf(settings)),
            blobPrefix = settings.blobPrefix(),
            blockSize = settings.blockSize.toInt(),
        )
    }

}

/** The container client of [settings]. The builder is lazy, so no request is sent here. */
internal fun containerOf(settings: AzureBlobStorageProviderSettings): BlobContainerClient =
    when (settings.authentication) {
        "connection-string" -> BlobContainerClientBuilder()
            .connectionString(settings.connectionString)
            .containerName(settings.container)
            .buildClient()
        else -> BlobContainerClientBuilder()
            .endpoint(settings.resolvedEndpoint())
            .containerName(settings.container)
            .also { builder ->
                when (settings.authentication) {
                    "account-key" -> builder.credential(StorageSharedKeyCredential(settings.accountName, settings.accountKey))
                    "sas" -> builder.sasToken(settings.sasToken)
                    else -> builder.credential(DefaultAzureCredentialBuilder().build())
                }
            }
            .buildClient()
    }
