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

package net.onelitefeather.ingot.storage.gcs

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequestError
import com.reposilite.shared.internalServerError
import com.reposilite.shared.notFoundError
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

    /** The name of the object of [location] in the bucket, with the prefix of the repository. */
    private fun objectName(location: Location): String =
        objectPrefix + location.toString().replace('\\', '/')

    private fun contentTypeOf(location: Location): String =
        ContentType.mimeTypeByExtension(location.getExtension()) ?: OCTET_STREAM

    /** Logs an unexpected failure with its exception and answers it as a server error. */
    private fun <T> failed(operation: String, exception: Exception): Result<T, ErrorResponse> {
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

    override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> = TODO("details land in a later change of the series")

    override fun removeFile(location: Location): Result<Unit, ErrorResponse> = TODO("removal lands in a later change of the series")

    override fun getFiles(location: Location): Result<List<Location>, ErrorResponse> = TODO("listing lands in a later change of the series")

    override fun getLastModifiedTime(location: Location): Result<FileTime, ErrorResponse> = TODO("metadata lands in a later change of the series")

    override fun getFileSize(location: Location): Result<Long, ErrorResponse> = TODO("metadata lands in a later change of the series")

    override fun exists(location: Location): Boolean = TODO("metadata lands in a later change of the series")

    override fun usage(): Result<Long, ErrorResponse> = TODO("capabilities land in a later change of the series")

    override fun canHold(contentLength: Long): Result<Long, ErrorResponse> = TODO("capabilities land in a later change of the series")

}
