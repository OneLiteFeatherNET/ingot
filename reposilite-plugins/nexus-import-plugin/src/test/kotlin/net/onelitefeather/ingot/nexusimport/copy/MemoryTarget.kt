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

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** An in-memory [TargetWriter] for tests: the Ingot storage the copy writes into, without the Maven facade. */
internal class MemoryTarget : TargetWriter {

    private val files = mutableMapOf<Pair<String, String>, ByteArray>()

    fun read(repository: String, path: String): ByteArray? = files[repository to path]

    fun write(repository: String, path: String, bytes: ByteArray) {
        files[repository to path] = bytes
    }

    fun remove(repository: String, path: String) {
        files.remove(repository to path)
    }

    override fun existing(repository: String, path: String): ExistingFile? =
        files[repository to path]?.let { ExistingFile(it.size.toLong(), sha1(it)) }

    override fun list(repository: String): Map<String, ExistingFile> =
        files.filterKeys { it.first == repository }.mapKeys { it.key.second }
            .mapValues { ExistingFile(it.value.size.toLong(), sha1(it.value)) }

    override fun write(repository: String, path: String, content: Path) {
        files[repository to path] = Files.readAllBytes(content)
    }

    override fun delete(repository: String, path: String) {
        files.remove(repository to path)
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
}
