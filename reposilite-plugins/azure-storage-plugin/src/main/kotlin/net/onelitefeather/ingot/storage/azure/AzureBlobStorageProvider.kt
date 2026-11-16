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

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequestError
import com.reposilite.shared.internalServerError
import com.reposilite.shared.notFoundError
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.SimpleDirectoryInfo
import io.javalin.http.ContentType
import io.javalin.http.ContentType.APPLICATION_OCTET_STREAM
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.NOT_FOUND
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.capability.ReadinessOutcome
import net.onelitefeather.ingot.storage.capability.ReadinessProbe
import net.onelitefeather.ingot.storage.capability.UsageCapability
import io.javalin.http.ContentType.Companion.OCTET_STREAM
import panda.std.Result
import panda.std.Result.ok
import java.io.ByteArrayInputStream
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
) : StorageProvider, ReadinessProbe, UsageCapability {

    /** Azure reports neither the usage nor a quota of a container without Azure Monitor, so the dashboard shows the repository as unlimited. */
    override val reportsUsage: Boolean = false

    override val supportsQuota: Boolean = false

    /** The container answers its properties for a healthy identity, and an empty container is healthy too. */
    override fun probe(): ReadinessOutcome =
        try {
            client.checkContainer()
            ReadinessOutcome.Ok
        } catch (_: AzureBlobNotFoundException) {
            ReadinessOutcome.Failed("container missing")
        } catch (exception: AzureServiceException) {
            when (exception.statusCode) {
                FORBIDDEN.code -> ReadinessOutcome.Failed(AUTHORIZATION_REASON)
                else -> ReadinessOutcome.Failed("the service answered HTTP ${exception.statusCode}")
            }
        } catch (_: Exception) {
            ReadinessOutcome.Failed("the container could not be reached")
        }


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

    private companion object {
        const val AUTHORIZATION_REASON =
            "authorization: the identity needs the role Storage Blob Data Contributor on the container"
    }

    /** Logs an unexpected failure with its exception and answers it as a server error. */
    private fun <T> failed(operation: String, exception: Exception): Result<T, ErrorResponse> {
        failureFacade.throwException("Azure Blob storage provider failed to $operation", exception)
        return internalServerError("Failed to $operation")
    }

    /** Answers a missing blob as not found, and any other failure as a server error. */
    private fun <T> answer(location: Location, operation: String, action: () -> Result<T, ErrorResponse>): Result<T, ErrorResponse> =
        try {
            action()
        } catch (_: AzureBlobNotFoundException) {
            notFoundError("File not found: $location")
        } catch (exception: Exception) {
            failed("$operation $location", exception)
        }

    override fun getFile(location: Location): Result<InputStream, ErrorResponse> =
        answer(location, "read") {
            ok(client.download(blobName(location), 0, null))
        }

    /**
     * The window of design D5. The size is read first, so the range is truncated to the blob as HTTP ranges are, and a range that
     * starts at or after the end is an empty stream. A range of length zero asks for no content at all.
     */
    override fun getFile(location: Location, offset: Long, length: Long): Result<InputStream, ErrorResponse> {
        if (offset < 0 || length < 0) {
            return badRequestError("The range of $location must not start or end before zero")
        }
        return answer(location, "read") {
            val name = blobName(location)
            val size = client.properties(name).contentLength
            val count = minOf(length, maxOf(0L, size - offset))
            if (count == 0L) {
                return@answer ok(ByteArrayInputStream(ByteArray(0)))
            }
            try {
                ok(client.download(name, offset, count))
            } catch (_: AzureRangeNotSatisfiableException) {
                // The size changed since it was read. The service answers such a range with 416, which is an empty window.
                ok(ByteArrayInputStream(ByteArray(0)))
            }
        }
    }

    /**
     * A blob answers with its details. A location without a blob is a directory when a listing with one result finds a child, as
     * design D6 describes. The repository root is always a directory.
     */
    override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> =
        if (location.toString().isEmpty()) {
            listDirectoryDetails(location)
        } else {
            answer<FileDetails>(location, "read the details of") {
                ok(client.properties(blobName(location)).toDocumentInfo(location))
            }.flatMapErr { error ->
                if (error.status == NOT_FOUND.code) listDirectoryDetails(location) else Result.error(error)
            }
        }

    private fun listDirectoryDetails(location: Location): Result<FileDetails, ErrorResponse> =
        answer<FileDetails>(location, "list") {
            val probe = children(location, maxResults = 1)
            if (probe.isEmpty()) {
                notFoundError("Directory not found or is empty")
            } else {
                ok(DirectoryInfo(name = location.getSimpleName(), files = children(location).mapNotNull { it.toFileDetails() }))
            }
        }

    /** The entries that a listing of [location] shows: its directories and its blobs, without the folder markers of blobs. */
    private fun children(location: Location, maxResults: Int? = null): List<AzureBlobChild> =
        client.listChildren(directoryPrefix(location), maxResults)
            .filter { it.isDirectory || !it.name.endsWith("/") }

    /** The prefix of the blobs under [location], with the repository prefix and the trailing slash. */
    private fun directoryPrefix(location: Location): String {
        val path = location.toString().replace('\\', '/')
        return blobPrefix + if (path.isEmpty()) "" else "$path/"
    }

    /** The location of an entry relative to the repository, without its trailing slash. */
    private fun AzureBlobChild.toLocation(): Location? =
        Location.ofRequest(name.removePrefix(blobPrefix).trimEnd('/')).orNull()

    private fun AzureBlobChild.toFileDetails(): FileDetails? {
        val entry = toLocation() ?: return null
        val properties = properties ?: return SimpleDirectoryInfo(name = entry.getSimpleName())
        return entry.toDocumentInfo(properties)
    }

    private fun Location.toDocumentInfo(properties: AzureBlobProperties): DocumentInfo =
        DocumentInfo(
            name = getSimpleName(),
            contentType = ContentType.contentTypeByExtension(getExtension()) ?: APPLICATION_OCTET_STREAM,
            contentLength = properties.contentLength,
            lastModifiedTime = properties.lastModified,
        )

    private fun AzureBlobProperties.toDocumentInfo(location: Location): DocumentInfo =
        DocumentInfo(
            name = location.getSimpleName(),
            contentType = ContentType.contentTypeByExtension(location.getExtension()) ?: APPLICATION_OCTET_STREAM,
            contentLength = contentLength,
            lastModifiedTime = lastModified,
        )

    /**
     * Removes a blob and everything under its name as a directory (design D14). The root is refused before any call, so a bad location
     * cannot empty the container. The removal is not atomic: a failure part way leaves some entries, and a repeated call removes the rest,
     * because a blob that is already gone counts as removed.
     */
    override fun removeFile(location: Location): Result<Unit, ErrorResponse> {
        val path = location.toString().replace('\\', '/')
        if (path.isBlank() || path == ".") {
            return badRequestError("Cannot remove repository root")
        }
        return try {
            deleteIfPresent(blobName(location))
            removeEntriesUnder(directoryPrefix(location))
            ok(Unit)
        } catch (exception: Exception) {
            failed("remove $location", exception)
        }
    }

    private fun removeEntriesUnder(prefix: String) {
        client.listChildren(prefix, maxResults = null).forEach { child ->
            if (child.isDirectory) {
                removeEntriesUnder(child.name)
            } else {
                deleteIfPresent(child.name)
            }
        }
    }

    private fun deleteIfPresent(name: String) {
        try {
            client.delete(name)
        } catch (_: AzureBlobNotFoundException) {
            // Already removed, by an earlier call or another writer
        }
    }

    override fun getFiles(location: Location): Result<List<Location>, ErrorResponse> =
        answer(location, "list") {
            val entries = children(location).mapNotNull { it.toLocation() }
            if (entries.isEmpty()) notFoundError("Directory not found or is empty") else ok(entries)
        }

    override fun getLastModifiedTime(location: Location): Result<FileTime, ErrorResponse> =
        answer(location, "read the time of") {
            ok(FileTime.from(client.properties(blobName(location)).lastModified))
        }

    override fun getFileSize(location: Location): Result<Long, ErrorResponse> =
        answer(location, "read the size of") {
            ok(client.properties(blobName(location)).contentLength)
        }

    /** A blob exists, and so does a location with a child under it. */
    override fun exists(location: Location): Boolean =
        try {
            client.properties(blobName(location))
            true
        } catch (_: AzureBlobNotFoundException) {
            client.listChildren(directoryPrefix(location), maxResults = 1).isNotEmpty()
        } catch (exception: Exception) {
            failureFacade.throwException("Azure Blob storage provider failed to check $location", exception)
            false
        }

    override fun usage(): Result<Long, ErrorResponse> =
        ok(-1)

    override fun canHold(contentLength: Long): Result<Long, ErrorResponse> =
        ok(Long.MAX_VALUE)

    override fun shutdown() {
        client.close()
    }

}
