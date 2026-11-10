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

package net.onelitefeather.ingot.backup

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.errorResponse
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR
import panda.std.Result
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The directory inside the working directory that holds the snapshots. It is created by the first snapshot only. */
const val BACKUP_DIRECTORY = "backups"

private const val SNAPSHOT_PREFIX = "ingot-snapshot-"
private const val SNAPSHOT_SUFFIX = ".db"
private const val OWNER_ONLY = "rw-------"
private val NAME_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

/** Writes a consistent copy of the database into [target], which exists and is empty when the writer runs. */
fun interface SnapshotWriter {
    fun write(target: Path)
}

/** What a finished snapshot reports. It names the file only, never its location. */
data class SnapshotInfo(
    val fileName: String,
    val sizeBytes: Long,
    val createdAt: Instant,
)

/**
 * Takes online snapshots of the SQLite database. Only the JDBC URL and a writer are needed, so the service runs
 * without a plugin context, both from the admin endpoint and from the command line flag.
 *
 * A snapshot is created empty first, so a name that is already taken is refused without touching the file that owns it.
 * A failed write removes the file again.
 */
class DatabaseSnapshotService(
    private val jdbcUrl: String,
    private val backupDirectory: Path,
    private val clock: Clock,
    private val writer: SnapshotWriter,
    private val audit: (AuditEvent) -> Unit,
) {

    fun snapshot(actor: AuditActor): Result<SnapshotInfo, ErrorResponse> {
        if (!isSqliteJdbcUrl(jdbcUrl)) {
            return errorResponse(
                CONFLICT,
                "Online snapshots are supported for SQLite only. Use the dump tool of your database instead, for example mariadb-dump for MariaDB, as described in the back up and restore guide."
            )
        }

        val createdAt = clock.instant()
        val fileName = "$SNAPSHOT_PREFIX${NAME_TIME.format(createdAt)}$SNAPSHOT_SUFFIX"
        val target = backupDirectory.resolve(fileName)

        try {
            Files.createDirectories(backupDirectory)
            createOwnerOnly(target)
        } catch (exception: FileAlreadyExistsException) {
            return errorResponse(CONFLICT, "A snapshot named $fileName already exists")
        } catch (exception: IOException) {
            return errorResponse(INTERNAL_SERVER_ERROR, "The snapshot directory could not be prepared")
        }

        val sizeBytes = try {
            writer.write(target)
            Files.size(target)
        } catch (exception: Exception) {
            deletePartialFile(target)
            return errorResponse(INTERNAL_SERVER_ERROR, "The snapshot could not be written and the partial file was removed")
        }

        audit(AuditEvent(action = AuditAction.DATABASE_SNAPSHOT_CREATED, actor = actor, details = mapOf("file" to fileName)))
        return Result.ok(SnapshotInfo(fileName = fileName, sizeBytes = sizeBytes, createdAt = createdAt))
    }

    private fun createOwnerOnly(target: Path) {
        if (target.fileSystem.supportedFileAttributeViews().contains("posix")) {
            Files.createFile(target, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_ONLY)))
        } else {
            Files.createFile(target)
        }
    }

    private fun deletePartialFile(target: Path) {
        try {
            Files.deleteIfExists(target)
        } catch (exception: IOException) {
            // The write has already failed and the answer reports that failure, so a failed removal does not change the answer.
        }
    }

}

/** SQLite databases are the only ones that get an online snapshot. Every other URL is refused before anything is written. */
fun isSqliteJdbcUrl(jdbcUrl: String): Boolean =
    jdbcUrl.startsWith("jdbc:sqlite:")
