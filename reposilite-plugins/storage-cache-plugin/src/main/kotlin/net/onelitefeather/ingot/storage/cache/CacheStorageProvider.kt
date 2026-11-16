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
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import panda.std.Result

/**
 * A read-through cache over [delegate]. Immutable files are copied once into [directory] and served from there; everything else is
 * answered by the delegate, which stays authoritative. A cache problem is reported through [onFailure] and never fails a read.
 *
 * Writes and removals still go straight to the delegate in this type. The write path follows in its own change.
 */
internal class CacheStorageProvider(
    private val delegate: StorageProvider,
    private val directory: Path,
    private val maxFileSize: Long? = null,
    private val onFailure: (String) -> Unit = {},
) : StorageProvider {

    private val dataDirectory: Path = directory.resolve("data")
    private val fillDirectory: Path = directory.resolve("fill")

    /** The fills in progress, by key. A request for a key that is being filled waits for that fill instead of asking the delegate. */
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<Boolean>>()

    init {
        Files.createDirectories(dataDirectory)
        Files.createDirectories(fillDirectory)
    }

    override fun getFile(location: Location): Result<InputStream, ErrorResponse> {
        val key = location.toString()
        if (isCacheMutableKey(key)) {
            return delegate.getFile(location)
        }
        val cached = dataFile(key)
        if (Files.isRegularFile(cached)) {
            return openCopy(cached, location)
        }
        if (!fitsFileSizeLimit(location)) {
            return delegate.getFile(location)
        }
        return fill(location, key)
    }

    override fun getFile(location: Location, offset: Long, length: Long): Result<InputStream, ErrorResponse> {
        val key = location.toString()
        val cached = dataFile(key)
        if (isCacheMutableKey(key) || !Files.isRegularFile(cached)) {
            // A ranged miss never starts a fill, see the spec of the storage cache.
            return delegate.getFile(location, offset, length)
        }
        return try {
            val channel = FileChannel.open(cached, StandardOpenOption.READ)
            channel.position(offset)
            Result.ok(BoundedInputStream(Channels.newInputStream(channel), length))
        } catch (exception: IOException) {
            onFailure("Cannot read the cached copy of $key: ${exception.message}")
            delegate.getFile(location, offset, length)
        }
    }

    override fun putFile(location: Location, inputStream: InputStream): Result<Unit, ErrorResponse> =
        delegate.putFile(location, inputStream)

    override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> =
        delegate.getFileDetails(location)

    override fun removeFile(location: Location): Result<Unit, ErrorResponse> =
        delegate.removeFile(location)

    override fun getFiles(location: Location): Result<List<Location>, ErrorResponse> =
        delegate.getFiles(location)

    override fun getLastModifiedTime(location: Location): Result<FileTime, ErrorResponse> =
        delegate.getLastModifiedTime(location)

    override fun getFileSize(location: Location): Result<Long, ErrorResponse> =
        delegate.getFileSize(location)

    override fun exists(location: Location): Boolean =
        delegate.exists(location)

    override fun usage(): Result<Long, ErrorResponse> =
        delegate.usage()

    override fun canHold(contentLength: Long): Result<Long, ErrorResponse> =
        delegate.canHold(contentLength)

    override fun shutdown() {
        delegate.shutdown()
    }

    private fun dataFile(key: String): Path =
        dataDirectory.resolve(key)

    private fun fitsFileSizeLimit(location: Location): Boolean {
        val limit = maxFileSize ?: return true
        val size = delegate.getFileSize(location)
        return size.isOk && size.get() <= limit
    }

    /** Serves a committed copy. A copy that cannot be opened is reported and the request goes to the delegate. */
    private fun openCopy(cached: Path, location: Location): Result<InputStream, ErrorResponse> =
        try {
            Result.ok(Files.newInputStream(cached))
        } catch (exception: IOException) {
            onFailure("Cannot open the cached copy of $location: ${exception.message}")
            Files.deleteIfExists(cached)
            delegate.getFile(location)
        }

    /**
     * Fills the cache for [key] once. A request that arrives during the fill waits for it and then reads the committed copy. Every
     * request that cannot use a copy is answered by the delegate.
     */
    private fun fill(location: Location, key: String): Result<InputStream, ErrorResponse> {
        val pending = CompletableFuture<Boolean>()
        val owner = inFlight.putIfAbsent(key, pending)
        if (owner != null) {
            return if (owner.join()) openCopy(dataFile(key), location) else delegate.getFile(location)
        }
        var committed = false
        try {
            committed = fetchIntoCache(location, key)
        } finally {
            inFlight.remove(key, pending)
            pending.complete(committed)
        }
        return if (committed) openCopy(dataFile(key), location) else delegate.getFile(location)
    }

    /**
     * Copies the delegate's bytes into a temporary file and moves it into the data folder once the copy is complete. A copy whose byte
     * count differs from the size the delegate reports is discarded.
     */
    private fun fetchIntoCache(location: Location, key: String): Boolean {
        val expected = delegate.getFileSize(location)
        val source = delegate.getFile(location)
        if (!expected.isOk || !source.isOk) {
            return false
        }
        val temporary = Files.createTempFile(fillDirectory, "fill-", ".part")
        try {
            val copied = source.get().use { input -> Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING) }
            if (copied != expected.get()) {
                onFailure("A copy of $key has $copied bytes, the delegate reports ${expected.get()}, so it is not cached")
                return false
            }
            val target = dataFile(key)
            Files.createDirectories(target.parent)
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return true
        } catch (exception: IOException) {
            onFailure("Cannot cache $key: ${exception.message}")
            return false
        } finally {
            // A failed cleanup must not fail the read that was being served, so it is only reported.
            runCatching { Files.deleteIfExists(temporary) }
                .onFailure { onFailure("Cannot remove the temporary copy of $key: ${it.message}") }
        }
    }

}

/** The first [remaining] bytes of [input], so that a ranged read never returns more than the range. */
private class BoundedInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {

    override fun read(): Int {
        if (remaining <= 0) return -1
        val value = super.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0) return -1
        val count = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count > 0) remaining -= count
        return count
    }

    override fun available(): Int =
        minOf(super.available().toLong(), remaining).toInt()

}
