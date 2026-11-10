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

import net.onelitefeather.ingot.nexusimport.jobs.AssetResult
import net.onelitefeather.ingot.nexusimport.nexus.NexusAsset
import net.onelitefeather.ingot.nexusimport.nexus.NexusHttpException
import net.onelitefeather.ingot.nexusimport.nexus.NexusReply
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

enum class ConflictPolicy { REFUSE, ABORT, OVERWRITE }

/** What the target holds at a path: its size and the sha1 of its bytes. */
data class ExistingFile(val size: Long, val sha1: String)

/**
 * The Ingot side of a copy. [write] receives a complete local file, so a target never holds a partial file: a body that
 * fails its checksums is never handed over. The production implementation goes through the Maven facade.
 */
interface TargetWriter {
    fun existing(repository: String, path: String): ExistingFile?
    fun list(repository: String): Map<String, ExistingFile>
    fun write(repository: String, path: String, content: Path)
    fun delete(repository: String, path: String)
}

/**
 * Copies one Nexus file. The order of decisions is fixed: a file with the listed sha1 and size is skipped, a differing file
 * is a conflict unless the policy overwrites it, and only then is the body downloaded into a local file, checked against
 * every checksum Nexus listed and the local sha256, and handed to the target. A 404 is GONE, a 401 or 403 is thrown so
 * that the job can pause for credentials, and other failures are FAILED.
 */
class FileCopier(
    private val open: (NexusAsset) -> NexusReply,
    private val target: TargetWriter,
    private val tempDir: Path,
) {

    fun copy(asset: NexusAsset, policy: ConflictPolicy, targetRepository: String): AssetResult {
        val existing = target.existing(targetRepository, asset.path)
        if (existing != null) {
            if (asset.checksums.sha1 != null && existing.sha1 == asset.checksums.sha1 && existing.size == asset.fileSize) {
                return AssetResult.SKIPPED_SAME
            }
            if (policy != ConflictPolicy.OVERWRITE) return AssetResult.CONFLICT
        }

        val part = Files.createTempFile(tempDir, "nexus-import", ".part")
        try {
            val reply = try {
                open(asset)
            } catch (exception: NexusHttpException) {
                return when (exception.status) {
                    404 -> AssetResult.GONE
                    401, 403 -> throw exception
                    else -> AssetResult.FAILED
                }
            }
            val digests = reply.body.use { Digests.copy(it, part) }
            if (!digests.matches(asset)) {
                return AssetResult.CHECKSUM_FAILED
            }
            if (existing != null) {
                target.delete(targetRepository, asset.path)
            }
            target.write(targetRepository, asset.path, part)
            return AssetResult.COPIED
        } catch (exception: IOException) {
            return AssetResult.FAILED
        } finally {
            Files.deleteIfExists(part)
        }
    }
}

/** The digests and the size of a body as it was written. */
internal class Digests(
    val size: Long,
    val md5: String,
    val sha1: String,
    val sha256: String,
    val sha512: String,
) {

    fun matches(asset: NexusAsset): Boolean {
        val listed = asset.checksums
        return (asset.fileSize < 0 || size == asset.fileSize) &&
            (listed.md5 == null || listed.md5.equals(md5, ignoreCase = true)) &&
            (listed.sha1 == null || listed.sha1.equals(sha1, ignoreCase = true)) &&
            (listed.sha256 == null || listed.sha256.equals(sha256, ignoreCase = true)) &&
            (listed.sha512 == null || listed.sha512.equals(sha512, ignoreCase = true))
    }

    companion object {
        /** Copies [input] into [output] while computing every digest in one pass. */
        fun copy(input: InputStream, output: Path): Digests {
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            val sha256 = MessageDigest.getInstance("SHA-256")
            val sha512 = MessageDigest.getInstance("SHA-512")
            var size = 0L
            Files.newOutputStream(output).use { out: OutputStream ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    md5.update(buffer, 0, read)
                    sha1.update(buffer, 0, read)
                    sha256.update(buffer, 0, read)
                    sha512.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                    size += read
                }
            }
            return Digests(size, md5.hex(), sha1.hex(), sha256.hex(), sha512.hex())
        }

        private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }

        private const val BUFFER = 64 * 1024
    }
}
