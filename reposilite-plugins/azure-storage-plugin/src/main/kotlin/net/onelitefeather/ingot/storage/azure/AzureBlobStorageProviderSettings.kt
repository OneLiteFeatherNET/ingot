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

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Sensitive
import com.reposilite.storage.StorageProviderSettings
import io.javalin.openapi.Custom
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.capability.SharedNamespaceAware
import net.onelitefeather.ingot.storage.capability.SupportDetails

/** The storage type that this plugin registers. It is written as this value and is not configurable. */
internal const val AZURE_BLOB_TYPE = "azure-blob"

/** The default block size of an upload: one block of a block blob, see the upload rules of the provider. */
internal const val DEFAULT_BLOCK_SIZE: Long = 8L * 1024 * 1024

private const val DEFAULT_AUTHENTICATION = "default-credential"

/** The account part of a namespace key whose account is blank. See [AzureBlobStorageProviderSettings.overlaps]. */
private const val BLANK_ACCOUNT_KEY = "azure:"

@Doc(title = "Azure Blob Storage Provider", description = "Azure Blob container storage provider settings")
data class AzureBlobStorageProviderSettings(
    @get:Doc(title = "Endpoint", description = "Blob endpoint of the account. Empty means the public endpoint of the account name (optional)")
    val endpoint: String = "",
    @get:Doc(title = "Account Name", description = "The storage account")
    val accountName: String = "",
    @get:Doc(title = "Container", description = "The blob container that holds the repository")
    val container: String = "",
    @get:Doc(title = "Prefix", description = "Optional prefix prepended to all blob names, e.g. to share a container between repositories")
    val prefix: String = "",
    @get:Doc(
        title = "Authentication",
        description = "One of default-credential, account-key, sas or connection-string. Each mode reads only its own secret",
    )
    val authentication: String = DEFAULT_AUTHENTICATION,
    @get:Doc(title = "Block Size", description = "The size in bytes of one block of an upload")
    val blockSize: Long = DEFAULT_BLOCK_SIZE,
    @get:Sensitive
    @get:Doc(title = "Account Key", description = "The shared key of the account, for the account-key mode")
    val accountKey: String = "",
    @get:Sensitive
    @get:Doc(title = "SAS Token", description = "A SAS token for the container, for the sas mode")
    val sasToken: String = "",
    @get:Sensitive
    @get:Doc(title = "Connection String", description = "The connection string of the account, for the connection-string mode")
    val connectionString: String = "",
) : StorageProviderSettings, SharedNamespaceAware, SupportDetails {

    @get:Custom(name = "const", value = AZURE_BLOB_TYPE)
    override val type: String = AZURE_BLOB_TYPE

    /**
     * The namespace of this repository: the account, the container and the prefix, with the prefix normalised to one trailing slash.
     * A blank container has no namespace, so it never collides.
     */
    override fun namespaceKey(repositoryName: String): String? {
        if (container.isBlank()) {
            return null
        }
        return "azure:${namespaceAccount()}/${container.trim()}/${blobPrefix()}"
    }

    /**
     * The account of the namespace key. In connection-string mode the account is the `AccountName` of the connection string, which
     * is not a secret, and it is used whatever the account field says. Otherwise it is the account field. Blank when nothing supplies one.
     */
    private fun namespaceAccount(): String {
        if (authentication == "connection-string") {
            connectionStringAccount()?.let { return it }
        }
        return accountName.trim()
    }

    private fun connectionStringAccount(): String? =
        connectionString.split(';')
            .map { it.trim() }
            .firstOrNull { it.substringBefore('=').trim().equals("AccountName", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /**
     * Two namespaces collide when they are in the same container of the same account, and one prefix equals or nests under the other.
     * A blank account takes the environment account when the repository starts, a value that the namespace cannot read. So a blank
     * account collides with the account of any other repository in the same container and prefix.
     */
    override fun overlaps(key: String, otherKey: String): Boolean {
        val (account, container, prefix) = key.split('/', limit = 3)
        val (otherAccount, otherContainer, otherPrefix) = otherKey.split('/', limit = 3)
        val sameAccount = account == otherAccount || account == BLANK_ACCOUNT_KEY || otherAccount == BLANK_ACCOUNT_KEY
        return sameAccount && container == otherContainer && (prefix.startsWith(otherPrefix) || otherPrefix.startsWith(prefix))
    }

    override fun overlapMessage(): String =
        "Its Azure Blob namespace overlaps another repository in the same container. Give each repository a distinct 'prefix'."

    /** The account, the container and the authentication mode. The secrets are never part of the description. */
    override fun describe(): Map<String, String> =
        mapOf(
            "accountName" to accountName,
            "container" to container,
            "prefix" to prefix,
            "authentication" to authentication,
        )

    /** The prefix of the blobs of this repository, with one trailing slash, or empty for the root of the container. */
    internal fun blobPrefix(): String =
        prefix.trim().trim('/').let { if (it.isEmpty()) "" else "$it/" }

    /** The endpoint of the account: the configured one when set, otherwise the public Blob endpoint of [accountName]. */
    fun resolvedEndpoint(): String =
        endpoint.takeIf { it.isNotBlank() } ?: "https://$accountName.blob.core.windows.net"

    /**
     * Why these settings cannot authenticate, or null when they can. The message names the mode and the field, and never a value.
     * Each mode reads only the field it needs, so a secret of another mode does not satisfy it.
     */
    fun authenticationError(): String? {
        val field = when (authentication) {
            "default-credential" -> return null
            "account-key" -> "accountKey".takeIf { accountKey.isBlank() }
            "sas" -> "sasToken".takeIf { sasToken.isBlank() }
            "connection-string" -> "connectionString".takeIf { connectionString.isBlank() }
            else -> return "Azure Blob storage has no authentication mode '$authentication', use default-credential, account-key, sas or connection-string"
        }
        return field?.let { "Azure Blob storage with authentication '$authentication' needs the field '$it'" }
    }

}
