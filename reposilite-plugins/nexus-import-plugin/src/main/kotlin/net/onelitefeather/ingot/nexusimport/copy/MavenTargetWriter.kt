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
package net.onelitefeather.ingot.nexusimport.copy

import com.reposilite.maven.MavenFacade
import com.reposilite.maven.Repository
import com.reposilite.maven.api.DeployRequest
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import panda.std.Result
import java.security.MessageDigest

/**
 * The production [TargetWriter]: files go through the Maven facade, so storage, metadata, the search index and the events
 * behave as for any deploy. Every write is attributed to [by], which the audit trail reads as the migration actor. Checksums
 * are never generated here, because the copied sidecars are the Nexus bytes.
 */
internal class MavenTargetWriter(private val maven: MavenFacade, private val by: String) : TargetWriter {

    override fun existing(repository: String, path: String): ExistingFile? {
        val storage = storageOf(repository)
        val location = path.toLocation()
        return if (storage.exists(location)) describe(storage, location) else null
    }

    /** Every file of the repository, walked directory by directory. Keys are the Maven paths without a leading slash. */
    override fun list(repository: String): Map<String, ExistingFile> {
        val storage = storageOf(repository)
        val files = linkedMapOf<String, ExistingFile>()
        fun walk(directory: Location) {
            for (child in storage.getFiles(directory).unwrap()) {
                when (storage.getFileDetails(child).unwrap()) {
                    is DocumentInfo -> files[child.toString()] = describe(storage, child)
                    else -> walk(child)
                }
            }
        }
        walk(Location.empty())
        return files
    }

    override fun write(repository: String, path: String, content: Path) {
        val target = repositoryOf(repository)
        Files.newInputStream(content).use { input ->
            maven.deployFile(
                DeployRequest(repository = target, gav = path.toLocation(), by = by, content = input, generateChecksums = false),
            ).unwrap()
        }
    }

    override fun delete(repository: String, path: String) {
        maven.deleteUnchecked(repositoryOf(repository), path.toLocation(), by).unwrap()
    }

    private fun repositoryOf(name: String): Repository =
        maven.getRepository(name) ?: throw IOException("The repository  does not exist")

    private fun storageOf(name: String): StorageProvider = repositoryOf(name).storageProvider

    private fun describe(storage: StorageProvider, location: Location): ExistingFile {
        val digest = MessageDigest.getInstance("SHA-1")
        var size = 0L
        storage.getFile(location).unwrap().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                size += read
            }
        }
        return ExistingFile(size, digest.digest().joinToString("") { "%02x".format(it) })
    }

    private fun <T> Result<T, ErrorResponse>.unwrap(): T =
        if (isOk) get() else throw IOException(error.message)

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
