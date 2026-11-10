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

package com.reposilite.storage.filesystem

import com.reposilite.journalist.Journalist
import com.reposilite.status.FailureFacade
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequest
import com.reposilite.shared.notFound
import com.reposilite.shared.stream.BoundedInputStream
import com.reposilite.shared.stream.BoundedInputStreamLimitExceededException
import com.reposilite.shared.toErrorResponse
import com.reposilite.shared.toErrorResult
import com.reposilite.storage.FilesComparator
import com.reposilite.storage.StorageEntrySource
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.VersionComparator
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.FileType.DIRECTORY
import com.reposilite.storage.api.FileType.FILE
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.SimpleDirectoryInfo
import com.reposilite.storage.inputStream
import com.reposilite.storage.type
import io.javalin.http.ContentType
import io.javalin.http.ContentType.APPLICATION_OCTET_STREAM
import io.javalin.http.HttpStatus.INSUFFICIENT_STORAGE
import io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR
import panda.std.Result
import panda.std.asSuccess
import panda.std.mapToUnit
import java.io.Closeable
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readAttributes
import kotlin.io.path.useDirectoryEntries

/**
 * @param rootDirectory root directory of storage space
 */
abstract class FileSystemStorageProvider protected constructor(
    val journalist: Journalist,
    val rootDirectory: Path,
    private val failureFacade: FailureFacade,
    private val clock: Clock,
) : StorageProvider, StorageEntrySource by FileSystemEntrySource(rootDirectory, failureFacade) {

    private enum class LockMode {
        READ,
        WRITE
    }

    private class FileAccessor(
        val rwLock: ReentrantReadWriteLock = ReentrantReadWriteLock(),
        /** Refcount of in-flight acquires; the accessor is evicted from the map only when this hits zero. */
        val holders: AtomicInteger = AtomicInteger(0),
    )

    private val lockedLocations = ConcurrentHashMap<Location, FileAccessor>()

    internal fun lockedLocationsSize(): Int =
        lockedLocations.size

    /**
     * Bytes of all files below the root, null until the first measurement. Measured once by walking the tree, then
     * kept up to date by [commitFile] and [removeFile], so a quota check does not walk the tree on every upload.
     * Changes made to the disk outside of this provider are only seen after a restart.
     */
    private val usageLock = Any()
    private var storedBytes: Long? = null

    /** Set once the file system has refused an atomic move, so that the warning is logged once per provider. */
    private val atomicMoveWarned = AtomicBoolean(false)

    private fun acquireFileAccessLock(location: Location, lockMode: LockMode): Closeable {
        val accessor = lockedLocations.compute(location) { _, existing ->
            (existing ?: FileAccessor()).also { it.holders.incrementAndGet() }
        }!!

        val lock = when (lockMode) {
            LockMode.READ -> accessor.rwLock.readLock()
            LockMode.WRITE -> accessor.rwLock.writeLock()
        }

        try {
            lock.lockInterruptibly()
        } catch (interrupted: InterruptedException) {
            decrementHolders(location, accessor)
            throw IOException("Failed to acquire $lockMode lock for $location", interrupted)
        }

        return Closeable {
            try {
                lock.unlock()
            } finally {
                decrementHolders(location, accessor)
            }
        }
    }

    private fun decrementHolders(location: Location, accessor: FileAccessor) {
        lockedLocations.compute(location) { _, existing ->
            when {
                existing !== accessor -> existing
                accessor.holders.decrementAndGet() == 0 -> null
                else -> existing
            }
        }
    }

    override fun putFile(location: Location, inputStream: InputStream): Result<Unit, ErrorResponse> =
        inputStream.use { data ->
            canHold(0)
                .mapErr { INSUFFICIENT_STORAGE.toErrorResponse("Not enough storage space available: ${it.message}") }
                .flatMap { limit -> location.resolveWithRootDirectory().map { limit to it } }
                .peek { (_, file) -> file.createParentDirectories() }
                .flatMap { (limit, file) ->
                    // TO-FIX: FS locks are not truly respected, there might be a need to enhanced it with .lock file to be sure if it's respected.
                    // In theory people shouldn't redeploy multiple times the same file, but who knows.
                    // Let's try with temporary files.
                    // ~ https://github.com/dzikoysk/reposilite/issues/264

                    useTempFile(file.parent) { temporaryFile ->
                        copyBounded(source = data, destination = temporaryFile, maxBytes = limit)
                            // Re-check post-write: InputStream.available() is just a hint and returns 0 for HTTP request bodies.
                            .flatMap { canHold(temporaryFile.fileSize()).mapErr { INSUFFICIENT_STORAGE.toErrorResponse("Not enough storage space available: ${it.message}") } }
                            .peek {
                                acquireFileAccessLock(location, LockMode.WRITE).use {
                                    commitFile(temporaryFile, file)
                                }
                            }
                            .mapToUnit()
                    }
                }
        }

    /**
     * Moves the written file into place and adjusts the usage by the difference to the file it replaces. The temporary
     * file lies in the same directory as the target, so the move is a rename and readers see either the old or the new
     * bytes. Writes to the same location are already serialised by the caller's WRITE lock, which keeps the measured
     * replaced size valid.
     */
    private fun commitFile(temporaryFile: Path, file: Path) {
        val replaced = sizeOf(file)
        val written = temporaryFile.fileSize()
        commitMove(temporaryFile, file)
        synchronized(usageLock) {
            storedBytes = storedBytes?.let { it - replaced + written }
        }
    }

    /**
     * Commits with an atomic move. Only a file system that refuses atomic moves gets the plain replacing move, and the
     * refusal is logged once.
     */
    private fun commitMove(temporaryFile: Path, file: Path) {
        try {
            moveIntoPlace(temporaryFile, file, atomic = true)
        } catch (unsupported: AtomicMoveNotSupportedException) {
            if (atomicMoveWarned.compareAndSet(false, true)) {
                journalist.logger.warn("Storage | $rootDirectory does not support atomic moves, uploads are replaced with a plain move")
            }
            moveIntoPlace(temporaryFile, file, atomic = false)
        }
    }

    /** Moves [source] onto [target], replacing it. [atomic] asks for a move that the file system completes in one step. */
    internal open fun moveIntoPlace(source: Path, target: Path, atomic: Boolean) {
        if (atomic) {
            Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } else {
            Files.move(source, target, REPLACE_EXISTING)
        }
    }

    /** Creates the temporary file of an upload in [directory], which is the directory of its target. */
    private inline fun <T> useTempFile(directory: Path, block: (Path) -> T): T {
        val temporaryFile = createTempFile(directory, PARTIAL_UPLOAD_PREFIX, PARTIAL_UPLOAD_SUFFIX)
        return try {
            block(temporaryFile)
        } finally {
            temporaryFile.deleteIfExists()
        }
    }

    private fun copyBounded(source: InputStream, destination: Path, maxBytes: Long): Result<Unit, ErrorResponse> =
        try {
            destination.outputStream().use { out -> BoundedInputStream(source, maxBytes).copyTo(out) }
            Unit.asSuccess()
        } catch (overflow: BoundedInputStreamLimitExceededException) {
            INSUFFICIENT_STORAGE.toErrorResult("Upload exceeded the available quota of ${overflow.limitBytes} bytes")
        }

    private class LockedFilterInputStream(
        private val inputStream: InputStream,
        private val lock: Closeable,
    ) : FilterInputStream(inputStream) {
        // Javalin/Jetty may call close() more than once; without the guard the read counter goes negative and wedges future writers.
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                lock.use {
                    inputStream.close()
                }
            }
        }
    }

    override fun getFile(location: Location): Result<InputStream, ErrorResponse> =
        location.resolveWithRootDirectory()
            .exists()
            .flatMap { resource ->
                val lock = acquireFileAccessLock(location, LockMode.READ)

                resource.inputStream()
                    .onError { lock.close() }
                    .map { LockedFilterInputStream(it, lock) }
            }

    /**
     * Reads under the same lock as [getFile]: a write moves its copy into place under the write lock, and the details
     * must not describe a partially copied file that the streamed body would then not match.
     */
    override fun getFileDetails(location: Location): Result<out FileDetails, ErrorResponse> =
        acquireFileAccessLock(location, LockMode.READ).use {
            location.resolveWithRootDirectory()
                .exists()
                .flatMap { toFileDetails(it) }
        }

    private fun toFileDetails(file: Path): Result<out FileDetails, ErrorResponse> =
        Result.`when`(file.exists(), file, notFound("File not found"))
            .flatMap {
                when (it.type()) {
                    FILE -> toDocumentInfo(it)
                    DIRECTORY -> toDirectoryInfo(it)
                }
            }

    private fun toDocumentInfo(path: Path): Result<DocumentInfo, ErrorResponse> =
        path
            .let { path.readAttributes<BasicFileAttributes>() }
            .let {
                DocumentInfo(
                    name = path.name,
                    contentType = ContentType.contentTypeByExtension(path.extension) ?: APPLICATION_OCTET_STREAM,
                    contentLength = it.size(),
                    lastModifiedTime = it.lastModifiedTime().toInstant(),
                )
            }.asSuccess()

    private fun toDirectoryInfo(directory: Path): Result<DirectoryInfo, ErrorResponse> =
        DirectoryInfo(
            name = directory.name,
            files =
            directory.useDirectoryEntries { directoryStream ->
                directoryStream.map { toSimpleFileDetails(it).orThrow { error -> IOException(error.message) } }
                    .sortedWith(FilesComparator({ VersionComparator.asVersion(it.name) }, { it.type == DIRECTORY }))
                    .toList()
            }
        ).asSuccess()

    private fun toSimpleFileDetails(file: Path): Result<out FileDetails, ErrorResponse> =
        when (file.type()) {
            FILE -> toDocumentInfo(file)
            DIRECTORY -> SimpleDirectoryInfo(file.name).asSuccess()
        }

    @OptIn(ExperimentalPathApi::class)
    override fun removeFile(location: Location): Result<Unit, ErrorResponse> =
        // No read/write lock yet — concurrent delete + read/write on the same location is rare in practice.
        location
            .resolveWithRootDirectory()
            .filter({ !it.normalize().equals(rootDirectory.normalize()) }, { badRequest("Cannot remove repository root") })
            .map { rootPath ->
                val removed = sizeOf(rootPath)
                try {
                    when {
                        rootPath.isDirectory() -> rootPath.deleteRecursively()
                        // Removing what is already gone is not an error: upstream answers a repeated DELETE with success, and so must Ingot.
                        else -> rootPath.deleteIfExists().let { }
                    }
                    synchronized(usageLock) { storedBytes = storedBytes?.let { it - removed } }
                } catch (exception: Exception) {
                    // A partial removal leaves an unknown amount behind, so the next usage() measures the tree again.
                    synchronized(usageLock) { storedBytes = null }
                    throw exception
                }
            }

    override fun getFiles(location: Location): Result<List<Location>, ErrorResponse> =
        location.resolveWithRootDirectory()
            .exists()
            .map {
                it.useDirectoryEntries { directoryStream ->
                    directoryStream.filter { path -> path != it }
                        .map { path -> Location.of(rootDirectory, path) }
                        .toList()
                }
            }

    override fun getLastModifiedTime(location: Location): Result<FileTime, ErrorResponse> =
        location.resolveWithRootDirectory()
            .exists()
            .map { it.getLastModifiedTime() }

    override fun getFileSize(location: Location): Result<Long, ErrorResponse> =
        location.resolveWithRootDirectory()
            .exists()
            .map {
                when (it.type()) {
                    FILE -> it.fileSize()
                    DIRECTORY -> -1
                }
            }

    override fun exists(location: Location): Boolean =
        location.resolveWithRootDirectory()
            .exists()
            .fold({ true }, { false })

    override fun usage(): Result<Long, ErrorResponse> =
        synchronized(usageLock) {
            val known = storedBytes
            if (known != null) {
                known.asSuccess()
            } else {
                measureStoredBytes().peek { storedBytes = it }
            }
        }

    private fun measureStoredBytes(): Result<Long, ErrorResponse> =
        try {
            sizeOf(rootDirectory.toRealPath()).asSuccess()
        } catch (exception: IOException) {
            failureFacade.throwException("Cannot measure the storage usage of $rootDirectory", exception)
            INTERNAL_SERVER_ERROR.toErrorResult("Cannot measure the storage usage")
        } catch (exception: UncheckedIOException) {
            failureFacade.throwException("Cannot measure the storage usage of $rootDirectory", exception)
            INTERNAL_SERVER_ERROR.toErrorResult("Cannot measure the storage usage")
        }

    /** Bytes of the regular files at or below the given path. Symbolic links are neither followed nor counted. */
    private fun sizeOf(path: Path): Long =
        when {
            Files.isDirectory(path, NOFOLLOW_LINKS) -> Files.walk(path).use { entries ->
                entries.filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }.mapToLong { Files.size(it) }.sum()
            }
            Files.isRegularFile(path, NOFOLLOW_LINKS) -> Files.size(path)
            else -> 0
        }

    private fun Result<Path, ErrorResponse>.exists(): Result<Path, ErrorResponse> =
        filter({ it.exists() }) { notFound("File not found") }

    private fun Location.resolveWithRootDirectory(): Result<Path, ErrorResponse> =
        rootDirectory.resolve(toPath()).asSuccess()

}
