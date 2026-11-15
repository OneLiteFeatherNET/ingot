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

package net.onelitefeather.ingot.storage.azure

import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.models.BlobHttpHeaders
import com.azure.storage.blob.models.BlobRange
import com.azure.storage.blob.models.BlobStorageException
import com.azure.storage.blob.models.ListBlobsOptions
import com.azure.storage.blob.options.BlockBlobCommitBlockListOptions
import com.azure.storage.blob.options.BlockBlobSimpleUploadOptions
import java.io.InputStream

/** The [AzureBlobClient] of the Azure SDK. It is the only class of the plugin that talks to the service. */
internal class AzureSdkBlobClient(private val container: BlobContainerClient) : AzureBlobClient {

    override fun putBlob(name: String, content: ByteArray, contentType: String) {
        container.getBlobClient(name).blockBlobClient.uploadWithResponse(
            BlockBlobSimpleUploadOptions(BinaryData.fromBytes(content)).setHeaders(BlobHttpHeaders().setContentType(contentType)),
            null,
            null,
        )
    }

    override fun stageBlock(name: String, blockId: String, content: ByteArray) {
        container.getBlobClient(name).blockBlobClient.stageBlock(blockId, BinaryData.fromBytes(content))
    }

    override fun commitBlockList(name: String, blockIds: List<String>, contentType: String) {
        container.getBlobClient(name).blockBlobClient.commitBlockListWithResponse(
            BlockBlobCommitBlockListOptions(blockIds).setHeaders(BlobHttpHeaders().setContentType(contentType)),
            null,
            null,
        )
    }

    override fun properties(name: String): AzureBlobProperties = translating(name) {
        val properties = container.getBlobClient(name).properties
        AzureBlobProperties(contentLength = properties.blobSize, lastModified = properties.lastModified.toInstant())
    }

    override fun download(name: String, offset: Long, count: Long?): InputStream = translating(name) {
        container.getBlobClient(name).openInputStream(BlobRange(offset, count), null)
    }

    override fun listChildren(prefix: String, maxResults: Int?): List<AzureBlobChild> {
        val options = ListBlobsOptions().setPrefix(prefix).also { options -> maxResults?.let { options.setMaxResultsPerPage(it) } }
        return translating(prefix) {
            container.listBlobsByHierarchy("/", options, null)
                .let { items -> maxResults?.let { items.take(it) } ?: items.toList() }
                .map { item ->
                    val properties = item.properties
                    if (item.isPrefix == true || properties == null) {
                        AzureBlobChild(item.name, null)
                    } else {
                        AzureBlobChild(
                            item.name,
                            AzureBlobProperties(properties.contentLength ?: 0L, properties.lastModified.toInstant()),
                        )
                    }
                }
        }
    }

    override fun delete(name: String) {
        translating(name) { container.getBlobClient(name).delete() }
    }

    /** Maps the status codes that the provider answers to the exceptions of [AzureBlobClient], and rethrows every other failure. */
    private fun <T> translating(name: String, action: () -> T): T =
        try {
            action()
        } catch (exception: BlobStorageException) {
            when (exception.statusCode) {
                NOT_FOUND -> throw AzureBlobNotFoundException(name)
                RANGE_NOT_SATISFIABLE -> throw AzureRangeNotSatisfiableException(name)
                else -> throw exception
            }
        }

    private companion object {
        const val NOT_FOUND = 404
        const val RANGE_NOT_SATISFIABLE = 416
    }

}
