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

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.internalServerError
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import io.javalin.http.ContentType
import io.javalin.http.ContentType.Companion.OCTET_STREAM
import panda.std.Result
import panda.std.Result.ok
import java.io.InputStream
import java.io.PushbackInputStream
import java.nio.file.attribute.FileTime
import java.util.Base64
import java.util.UUID

/**
 * The storage provider of one `azure-blob` repository. The blobs of the repository live under [blobPrefix] in one container, which the
 * [client] reaches. Uploads follow the block blob rules of design D4; the other operations are added by the following changes.
 */
internal class AzureBlobStorageProvider(
    private val failureFacade: FailureFacade,
    private val client: AzureBlobClient,
    private val blobPrefix: String,
    private val blockSize: Int,
    /** A new identifier for every upload. Its length must be the same for every upload, so the block ids of one blob have one length. */
    private val newUploadId: () -> String = { UUID.randomUUID().toString() },
) : StorageProvider {

    override fun putFile(location: Location, inputStream: InputStream): Result<Unit, ErrorResponse> =
        inputStream.use { data ->
            try {
                upload(location, data)
                ok(Unit)
            } catch (exception: Exception) {
                failed("write $location", exception)
            }
        }

    /**
     * The first block decides the request, as in the S3 provider: a stream that ends within it is one put with its exact content. A longer
     * stream is staged block by block, and the blocks are committed once, so a reader never sees a part of the blob. Only one block is
     * held at a time, and the stream never passes through the temporary directory.
     */
    private fun upload(location: Location, data: InputStream) {
        val name = blobName(location)
        val contentType = contentTypeOf(location)
        val input = PushbackInputStream(data, 1)
        val first = input.readNBytes(blockSize)
        // A full first block is followed by one more byte only when the stream continues past it
        val next = if (first.size == blockSize) input.read() else -1

        if (next == -1) {
            client.putBlob(name, first, contentType)
            return
        }
        input.unread(next)

        val uploadId = newUploadId()
        val blockIds = mutableListOf<String>()
        var block = first
        while (block.isNotEmpty()) {
            val blockId = blockIdOf(uploadId, blockIds.size)
            client.stageBlock(name, blockId, block)
            blockIds += blockId
            // A block shorter than the block size is the last one, and an empty read after a full block ends the upload
            if (block.size < blockSize) {
                break
            }
            block = input.readNBytes(blockSize)
        }
        client.commitBlockList(name, blockIds, contentType)
    }

    /** The name of the blob of [location] in the container, with the prefix of the repository. */
    private fun blobName(location: Location): String =
        blobPrefix + location.toString().replace('\\', '/')

    private fun contentTypeOf(location: Location): String =
        ContentType.mimeTypeByExtension(location.getExtension()) ?: OCTET_STREAM

    /**
     * A block id is the upload identifier followed by a fixed-width counter, encoded in Base64. Two uploads to one blob never stage a
     * block under the same id, so one upload can never commit the content of another.
     */
    private fun blockIdOf(uploadId: String, index: Int): String =
        Base64.getEncoder().encodeToString("$uploadId-%06d".format(index).toByteArray(Charsets.UTF_8))

    /** Logs an unexpected failure with its exception and answers it as a server error. */
    private fun failed(operation: String, exception: Exception): Result<Unit, ErrorResponse> {
        failureFacade.throwException("Azure Blob storage provider failed to $operation", exception)
        return internalServerError("Failed to $operation")
    }

    override fun getFile(location: Location): Result<InputStream, ErrorResponse> =
        throw UnsupportedOperationException("reads are added by a later change")

    override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> =
        throw UnsupportedOperationException("details are added by a later change")

    override fun removeFile(location: Location): Result<Unit, ErrorResponse> =
        throw UnsupportedOperationException("removal is added by a later change")

    override fun getFiles(location: Location): Result<List<Location>, ErrorResponse> =
        throw UnsupportedOperationException("listings are added by a later change")

    override fun getLastModifiedTime(location: Location): Result<FileTime, ErrorResponse> =
        throw UnsupportedOperationException("properties are added by a later change")

    override fun getFileSize(location: Location): Result<Long, ErrorResponse> =
        throw UnsupportedOperationException("properties are added by a later change")

    override fun exists(location: Location): Boolean =
        throw UnsupportedOperationException("properties are added by a later change")

    override fun usage(): Result<Long, ErrorResponse> =
        ok(-1)

    override fun canHold(contentLength: Long): Result<Long, ErrorResponse> =
        ok(Long.MAX_VALUE)

    override fun shutdown() {
        client.close()
    }

}
