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

package com.reposilite.audit.sink

import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission.GROUP_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Where the audit sink writes its lines. [write] receives complete lines, each ending with a newline, and throws when
 * they cannot be written. It is called from the sink's writer thread only.
 */
interface AuditLineWriter : AutoCloseable {

    fun write(lines: List<String>)

    override fun close() {}

}

/** Writes to a stream that belongs to the caller, such as standard output, which is flushed and never closed here. */
class StreamAuditLineWriter(private val out: OutputStream) : AuditLineWriter {

    override fun write(lines: List<String>) {
        lines.forEach { out.write(it.toByteArray(UTF_8)) }
        out.flush()
    }

    override fun close() {
        out.flush()
    }

}

/**
 * Appends to a file and never rotates, compresses or deletes it. The path is checked before a write, at most once per
 * second by the injected clock, and the file is opened again when the path no longer refers to the open file. An
 * external `logrotate` with `create`, or a rename by an operator, therefore needs no signal.
 *
 * The file is created with mode `0640` where the file system has POSIX permissions, because its content is personal data.
 */
class FileAuditLineWriter(
    private val path: Path,
    private val clock: Clock,
) : AuditLineWriter {

    private var stream: OutputStream? = null
    private var openedKey: Any? = null
    private var lastCheck: Instant? = null

    override fun write(lines: List<String>) {
        try {
            val out = currentStream()
            lines.forEach { out.write(it.toByteArray(UTF_8)) }
            out.flush()
        } catch (exception: IOException) {
            closeStream()
            throw IOException("Cannot append audit lines to $path: ${exception.message}", exception)
        }
    }

    override fun close() {
        closeStream()
    }

    private fun currentStream(): OutputStream {
        val now = clock.instant()
        val open = stream
        val checkedRecently = lastCheck?.let { Duration.between(it, now) < RECHECK_INTERVAL } == true
        if (open != null && checkedRecently) {
            return open
        }

        lastCheck = now
        if (open != null && isStillOpened()) {
            return open
        }

        closeStream()
        return open().also { stream = it }
    }

    private fun isStillOpened(): Boolean =
        try {
            Files.exists(path) && fileKey() == openedKey
        } catch (exception: IOException) {
            false
        }

    private fun open(): OutputStream {
        path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        if (!Files.exists(path)) {
            createOwnerAndGroupOnly()
        }

        val out = Files.newOutputStream(path, *OPEN_OPTIONS)
        openedKey = fileKey()
        return out
    }

    private fun createOwnerAndGroupOnly() {
        val attributes: Array<FileAttribute<*>> =
            if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
                arrayOf(PosixFilePermissions.asFileAttribute(OWNER_GROUP_READ_WRITE))
            } else {
                emptyArray()
            }

        try {
            Files.createFile(path, *attributes)
        } catch (exists: FileAlreadyExistsException) {
            // Another writer created it in between, which is what this method wanted.
        }
    }

    private fun fileKey(): Any? =
        Files.readAttributes(path, BasicFileAttributes::class.java).fileKey()

    private fun closeStream() {
        runCatching { stream?.close() }
        stream = null
        openedKey = null
    }

    private companion object {
        val RECHECK_INTERVAL: Duration = Duration.ofSeconds(1)
        val OWNER_GROUP_READ_WRITE = setOf(OWNER_READ, OWNER_WRITE, GROUP_READ)
        val OPEN_OPTIONS: Array<OpenOption> = arrayOf(CREATE, APPEND, WRITE)
    }

}
