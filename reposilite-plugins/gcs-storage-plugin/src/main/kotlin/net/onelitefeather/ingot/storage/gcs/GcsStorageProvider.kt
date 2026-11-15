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

package net.onelitefeather.ingot.storage.gcs

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequestError
import com.reposilite.shared.errorResponse
import com.reposilite.shared.internalServerError
import com.reposilite.shared.notFoundError
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageEntry
import com.reposilite.storage.StorageEntryPage
import com.reposilite.storage.StorageEntrySource
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.SimpleDirectoryInfo
import io.javalin.http.ContentType
import io.javalin.http.ContentType.APPLICATION_OCTET_STREAM
import io.javalin.http.ContentType.Companion.OCTET_STREAM
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.NOT_FOUND
import java.io.InputStream
import java.io.PushbackInputStream
import java.nio.file.attribute.FileTime
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.capability.ReadinessOutcome
import net.onelitefeather.ingot.storage.capability.ReadinessProbe
import net.onelitefeather.ingot.storage.capability.UsageCapability
import panda.std.Result
import panda.std.Result.ok

/**
 * The storage provider of one `gcs` repository. The objects of the repository live under [objectPrefix] in one bucket, which [client]
 * reaches. Writes follow design D4. The other methods are added by the following changes of the series, so until then they answer
 * with a placeholder that no caller reaches: the factory that registers the type is not part of this change.
 */
class GcsStorageProvider internal constructor(
    private val failureFacade: FailureFacade,
    private val client: GcsObjectClient,
    private val objectPrefix: String,
    private val chunkSize: Int,
    /** The bucket of the repository. It is named in the message of a refused request, so the operator knows where to grant the role. */
    private val bucket: String = "",
) : StorageProvider, StorageEntrySource, ReadinessProbe, UsageCapability {

    /** Google Cloud Storage reports no usage of a bucket to the plugin, so the dashboard shows the repository as unlimited. */
    override val reportsUsage: Boolean = false

    override val supportsQuota: Boolean = false

    /** A bucket that answers its metadata is ready, empty or not. A refused, missing or silent bucket names its reason. */
    override fun probe(): ReadinessOutcome =
        try {
            client.checkBucket()
            ReadinessOutcome.Ok
        } catch (exception: GcsServiceException) {
            when (exception.statusCode) {
                in REFUSED_STATUSES -> ReadinessOutcome.Failed("no permission on the bucket '$bucket'")
                NOT_FOUND.code -> ReadinessOutcome.Failed("bucket '$bucket' is missing")
                else -> ReadinessOutcome.Failed("the service answered HTTP ${exception.statusCode}")
            }
        } catch (_: Exception) {
            ReadinessOutcome.Failed("bucket '$bucket' is unreachable")
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
     * The first chunk decides the request, as in the S3 provider: a stream that ends within it is one request with its exact content.
     * A longer stream is sent as resumable chunks, and the object becomes visible only when the upload is finished. Only one chunk is
     * held at a time, and the stream never passes through the disk.
     */
    private fun upload(location: Location, data: InputStream) {
        val name = objectName(location)
        val contentType = contentTypeOf(location)
        val input = PushbackInputStream(data, 1)
        val first = input.readNBytes(chunkSize)
        // A full first chunk is followed by one more byte only when the stream continues past it
        val next = if (first.size == chunkSize) input.read() else -1

        if (next == -1) {
            client.create(name, first, contentType)
            return
        }
        input.unread(next)

        val upload = client.startUpload(name, contentType, chunkSize)
        var chunk = first
        upload.write(chunk)
        // A chunk shorter than the chunk size is the last one, and an empty read after a full chunk ends the upload
        while (chunk.size == chunkSize) {
            chunk = input.readNBytes(chunkSize)
            if (chunk.isNotEmpty()) {
                upload.write(chunk)
            }
        }
        upload.finish()
    }

    /** Takes one page of [limit] entries, and the cursor of its last entry when the sequence holds more. */
    private fun page(entries: Sequence<StorageEntry>, limit: Int): StorageEntryPage {
        val window = entries.take(limit + 1).toList()
        val kept = window.take(limit)
        return StorageEntryPage(entries = kept, next = if (window.size > limit) kept.last().path.toString() else null)
    }

    /** The name of the object of [location] in the bucket, with the prefix of the repository. */
    private fun objectName(location: Location): String =
        objectPrefix + location.toString().replace('\\', '/')

    private fun contentTypeOf(location: Location): String =
        ContentType.mimeTypeByExtension(location.getExtension()) ?: OCTET_STREAM

    /**
     * Logs a failure with its exception. A refused identity (401 or 403) is answered as forbidden and names the bucket and the role, and
     * any other failure is an internal server error. The answer never carries the text of the service error.
     */
    private fun <T> failed(operation: String, exception: Exception): Result<T, ErrorResponse> {
        if (exception is GcsServiceException && exception.statusCode in REFUSED_STATUSES) {
            failureFacade.throwException("Google Cloud Storage refused to $operation in bucket '$bucket'", exception)
            return errorResponse(FORBIDDEN, "Google Cloud Storage refused to $operation: the identity needs the role $REQUIRED_ROLE on bucket '$bucket'")
        }
        failureFacade.throwException("Google Cloud Storage provider failed to $operation", exception)
        return internalServerError("Failed to $operation")
    }

    override fun getFile(location: Location): Result<InputStream, ErrorResponse> =
        answer(location, "read") {
            ok(client.read(objectName(location), 0, null))
        }

    /**
     * The window of design D5. The size is read first, so the range is truncated to the object as HTTP ranges are, and a range that
     * starts at or after the end is an empty stream. A range of length zero asks for no content at all.
     */
    override fun getFile(location: Location, offset: Long, length: Long): Result<InputStream, ErrorResponse> {
        if (offset < 0 || length < 0) {
            return badRequestError("The range of $location must not start or end before zero")
        }
        if (length == 0L) {
            return ok(InputStream.nullInputStream())
        }
        return answer(location, "read") {
            val name = objectName(location)
            val count = minOf(length, maxOf(0L, client.metadata(name).size - offset))
            if (count == 0L) {
                ok(InputStream.nullInputStream())
            } else {
                ok(client.read(name, offset, count))
            }
        }
    }

    /** Answers a missing object as not found, and any other failure as a server error. */
    private fun <T> answer(location: Location, operation: String, action: () -> Result<T, ErrorResponse>): Result<T, ErrorResponse> =
        try {
            action()
        } catch (_: GcsNotFoundException) {
            notFoundError("File not found: $location")
        } catch (exception: Exception) {
            failed("$operation $location", exception)
        }

    /**
     * An object answers with its metadata. A location without an object is a directory when a listing with one result finds an
     * object below it, as design D5 describes. The repository root is always a directory.
     */
    override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> {
        if (location.toString().isEmpty()) {
            return directoryDetails(location)
        }
        return answer<FileDetails>(location, "read the details of") {
            ok(client.metadata(objectName(location)).toDocumentInfo(location))
        }.flatMapErr { error ->
            if (error.status == NOT_FOUND.code) directoryDetails(location) else Result.error(error)
        }
    }

    private fun directoryDetails(location: Location): Result<FileDetails, ErrorResponse> =
        answer<FileDetails>(location, "list") {
            val prefix = directoryPrefix(location)
            if (location.toString().isNotEmpty() && client.list(prefix, null, 1).isEmpty()) {
                notFoundError("Directory not found or is empty")
            } else {
                ok(DirectoryInfo(name = location.getSimpleName(), files = client.listDelimited(prefix).map { it.toFileDetails() }))
            }
        }

    /** The prefix of the objects under [location], with the prefix of the repository and one trailing slash. */
    private fun directoryPrefix(location: Location): String {
        val path = location.toString().replace('\\', '/')
        return objectPrefix + if (path.isEmpty()) "" else "$path/"
    }

    private fun GcsEntry.toFileDetails(): FileDetails = when (this) {
        is GcsObjectInfo -> toDocumentInfo(toLocation())
        is GcsPrefix -> SimpleDirectoryInfo(name = toLocation().getSimpleName())
    }

    /** The location of an entry relative to the repository, without the trailing slash of a directory. */
    private fun GcsEntry.toLocation(): Location =
        Location.of(name.removePrefix(objectPrefix).trimEnd('/'))

    private fun GcsObjectInfo.toDocumentInfo(location: Location): DocumentInfo =
        DocumentInfo(
            name = location.getSimpleName(),
            contentType = ContentType.contentTypeByExtension(location.getExtension()) ?: APPLICATION_OCTET_STREAM,
            contentLength = size,
            lastModifiedTime = updated,
        )

    /**
     * Removes the object of [location] and every object below it as a directory (design D7). The root is refused before any request, so a
     * bad location cannot empty the bucket. The removal is not atomic: a failure part way leaves some objects, and a repeated call removes
     * the rest, because an object that is already gone counts as removed.
     */
    override fun removeFile(location: Location): Result<Unit, ErrorResponse> {
        val path = location.toString().replace('\\', '/')
        if (path.isBlank() || path == ".") {
            return badRequestError("Cannot remove repository root")
        }
        return try {
            deleteIfPresent(objectName(location))
            removeObjectsUnder(directoryPrefix(location))
            ok(Unit)
        } catch (exception: Exception) {
            failed("remove $location", exception)
        }
    }

    /** Deletes the objects under [prefix] in pages. Each page is listed again after its deletes, so no cursor is needed. */
    private fun removeObjectsUnder(prefix: String) {
        while (true) {
            val page = client.list(prefix, null, MAX_DELETE_PAGE)
            if (page.isEmpty()) {
                return
            }
            page.forEach { deleteIfPresent(it.name) }
        }
    }

    private fun deleteIfPresent(name: String) {
        try {
            client.delete(name)
        } catch (_: GcsNotFoundException) {
            // Already removed, by an earlier call or another writer
        }
    }

    /** The entries directly below [location]: its objects, and one entry for each deeper directory. An empty directory is not found. */
    override fun getFiles(location: Location): Result<List<Location>, ErrorResponse> =
        answer(location, "list") {
            val entries = client.listDelimited(directoryPrefix(location)).map { it.toLocation() }
            if (entries.isEmpty()) notFoundError("Directory not found or is empty") else ok(entries)
        }

    /**
     * A flat listing of the objects below [prefix], in the order of the object names. The service starts at the cursor and includes it,
     * so the entry of the cursor is dropped here. Two more objects are requested than the page holds: one for the cursor and one that
     * shows whether another page follows.
     */
    override fun listEntries(prefix: Location, after: String?, limit: Int): Result<StorageEntryPage, ErrorResponse> {
        require(limit > 0) { "limit must be positive, was $limit" }
        return answer(prefix, "list") {
            val directory = directoryPrefix(prefix)
            val start = after?.let { objectPrefix + it }
            val entries = client.list(directory, start ?: directory, limit + 2).asSequence()
                .filter { start == null || it.name != start }
                .map { StorageEntry(path = Location.of(it.name.removePrefix(objectPrefix)), size = it.size, lastModified = it.updated) }
            ok(page(entries, limit))
        }
    }

    override fun getLastModifiedTime(location: Location): Result<FileTime, ErrorResponse> =
        answer(location, "read the time of") {
            ok(FileTime.from(client.metadata(objectName(location)).updated))
        }

    override fun getFileSize(location: Location): Result<Long, ErrorResponse> =
        answer(location, "read the size of") {
            ok(client.metadata(objectName(location)).size)
        }

    /** An object exists, and so does a location with an object below it. */
    override fun exists(location: Location): Boolean =
        try {
            client.metadata(objectName(location))
            true
        } catch (_: GcsNotFoundException) {
            client.list(directoryPrefix(location), null, 1).isNotEmpty()
        } catch (exception: Exception) {
            failureFacade.throwException("Google Cloud Storage provider failed to check $location", exception)
            false
        }

    override fun usage(): Result<Long, ErrorResponse> = ok(-1)

    override fun canHold(contentLength: Long): Result<Long, ErrorResponse> = ok(Long.MAX_VALUE)

    private companion object {
        /** The most objects that one listing of a removal asks for. */
        const val MAX_DELETE_PAGE = 1000

        /** The statuses of a request that the identity may not make. */
        val REFUSED_STATUSES = setOf(401, 403)

        /** The predefined role that grants the object operations of the provider at bucket scope (design D2, open question 3). */
        const val REQUIRED_ROLE = "roles/storage.objectUser"
    }

}
