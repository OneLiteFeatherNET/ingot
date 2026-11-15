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
import com.azure.storage.blob.options.BlockBlobCommitBlockListOptions
import com.azure.storage.blob.options.BlockBlobSimpleUploadOptions

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

}
