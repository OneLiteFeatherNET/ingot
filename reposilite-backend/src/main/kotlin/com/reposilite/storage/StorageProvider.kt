/*
 * Copyright (c) 2023 dzikoysk
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

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequestError
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.shared.stream.RangedInputStream
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.storage.StorageEntry
import java.io.InputStream
import java.nio.file.attribute.FileTime
import java.time.Duration
import panda.std.Result

interface StorageProvider {

    fun putFile(location: Location, inputStream: InputStream): Result<Unit, ErrorResponse>

    fun getFile(location: Location): Result<InputStream, ErrorResponse>

    /**
     * [length] bytes of a file from [offset]. The default opens the stream and skips, which seeks for a local file;
     * a remote storage overrides it with a ranged read so that the skipped bytes are never transferred.
     */
    fun getFile(location: Location, offset: Long, length: Long): Result<InputStream, ErrorResponse> =
        getFile(location).map { RangedInputStream(it, offset, length) }

    /**
     * The content, size and modification time of a file in one call. The default asks [getFileDetails] and then [getFile], as callers
     * do today, so a provider that does not override it, and every plugin compiled against the older interface, behaves as before. A
     * remote storage overrides it to answer with one request. The caller closes the content of the returned entry.
     */
    @ExperimentalIngotPluginApi
    fun readEntry(location: Location): Result<StorageEntry, ErrorResponse> =
        getFileDetails(location).flatMap { details ->
            when (details) {
                is DocumentInfo -> getFile(location).map { StorageEntry(it, details.contentLength, details.lastModifiedTime) }
                else -> getFile(location).flatMap { it.close(); badRequestError<StorageEntry>("$location is not a file") }
            }
        }

    fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse>

    fun removeFile(location: Location): Result<Unit, ErrorResponse>

    fun getFiles(location: Location): Result<List<Location>, ErrorResponse>

    fun getLastModifiedTime(location: Location): Result<FileTime, ErrorResponse>

    fun getFileSize(location: Location): Result<Long, ErrorResponse>

    fun exists(location: Location): Boolean

    fun usage(): Result<Long, ErrorResponse>

    fun canHold(contentLength: Long): Result<Long, ErrorResponse>

    /**
     * A presigned GET URL of [location] that is valid for [ttl], or null when the instance serves the file itself. The default is null,
     * so every storage that does not presign, and every plugin compiled against the older interface, keeps serving through the instance.
     */
    fun presignedGetUrl(location: Location, ttl: Duration): String? = null

    fun shutdown() {}

}
