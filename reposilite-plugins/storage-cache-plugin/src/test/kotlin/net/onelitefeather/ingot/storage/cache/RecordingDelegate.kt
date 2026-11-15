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

package net.onelitefeather.ingot.storage.cache

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import panda.std.Result

/**
 * An in-memory delegate that counts every call. The counters are what the tests assert, so a read that reached the store is visible
 * without a timing assumption. [onFullRead] runs before a full read answers, which lets a test hold a fill open with a latch.
 */
internal class RecordingDelegate(initial: Map<String, ByteArray> = emptyMap()) : StorageProvider {

    val files = ConcurrentHashMap<String, ByteArray>().apply { putAll(initial) }
    val fullReads = AtomicInteger()
    val rangedReads = AtomicInteger()
    val sizeQueries = AtomicInteger()
    val existenceQueries = AtomicInteger()
    val writes = AtomicInteger()
    val removals = AtomicInteger()

    /** When set, the size the delegate reports instead of the real length. */
    @Volatile
    var reportedSizeOverride: Long? = null

    /** When set, every write fails and stores nothing. */
    @Volatile
    var failWrites = false

    /** When set, every removal fails and removes nothing. */
    @Volatile
    var failRemovals = false

    /** Runs after a full read has taken its bytes, so a test can change the store while the read is still open. */
    @Volatile
    var onFullRead: () -> Unit = {}

    override fun putFile(location: Location, inputStream: InputStream): Result<Unit, ErrorResponse> {
        writes.incrementAndGet()
        val bytes = inputStream.readBytes()
        if (failWrites) return Result.error(ErrorResponse(500, "write refused by the test delegate"))
        files[location.toString()] = bytes
        return Result.ok(Unit)
    }

    override fun getFile(location: Location): Result<InputStream, ErrorResponse> {
        fullReads.incrementAndGet()
        val bytes = files[location.toString()] ?: return notFoundError("missing ${location}")
        onFullRead()
        return Result.ok(ByteArrayInputStream(bytes))
    }

    override fun getFile(location: Location, offset: Long, length: Long): Result<InputStream, ErrorResponse> {
        rangedReads.incrementAndGet()
        val bytes = files[location.toString()] ?: return notFoundError("missing ${location}")
        return Result.ok(ByteArrayInputStream(bytes.copyOfRange(offset.toInt(), (offset + length).toInt())))
    }

    override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> =
        notFoundError("not used by the tests")

    override fun removeFile(location: Location): Result<Unit, ErrorResponse> {
        removals.incrementAndGet()
        if (failRemovals) return Result.error(ErrorResponse(500, "removal refused by the test delegate"))
        files.remove(location.toString())
        return Result.ok(Unit)
    }

    override fun getFiles(location: Location): Result<List<Location>, ErrorResponse> =
        Result.ok(emptyList())

    override fun getLastModifiedTime(location: Location): Result<FileTime, ErrorResponse> =
        notFoundError("not used by the tests")

    override fun getFileSize(location: Location): Result<Long, ErrorResponse> {
        sizeQueries.incrementAndGet()
        reportedSizeOverride?.let { return Result.ok(it) }
        val bytes = files[location.toString()] ?: return notFoundError("missing ${location}")
        return Result.ok(bytes.size.toLong())
    }

    override fun exists(location: Location): Boolean {
        existenceQueries.incrementAndGet()
        return files.containsKey(location.toString())
    }

    override fun usage(): Result<Long, ErrorResponse> = Result.ok(files.values.sumOf { it.size.toLong() })

    override fun canHold(contentLength: Long): Result<Long, ErrorResponse> = Result.ok(Long.MAX_VALUE)

}
