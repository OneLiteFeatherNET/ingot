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

package net.onelitefeather.ingot.search.application

import com.reposilite.maven.MavenFacade
import com.reposilite.maven.MirrorCacheLedger
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.Location
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.outbox.IndexStorage
import net.onelitefeather.ingot.search.outbox.OriginLookup
import net.onelitefeather.ingot.search.outbox.StoredFile
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant

/** Reads the stored files of the repositories through their storage providers, as they are now. */
internal class MavenIndexStorage(
    private val maven: MavenFacade,
    private val clock: Clock,
) : IndexStorage {

    override fun file(repository: String, path: String): StoredFile? {
        val provider = maven.getRepository(repository)?.storageProvider ?: return null
        val location = location(path) ?: return null
        val details = provider.getFileDetails(location)
        if (details.isErr) return null
        val document = details.get() as? DocumentInfo ?: return null

        val size = if (document.contentLength >= 0) document.contentLength else provider.getFileSize(location).let { if (it.isOk) it.get() else 0L }
        val lastModified: Instant = document.lastModifiedTime
            ?: provider.getLastModifiedTime(location).let { if (it.isOk) it.get().toInstant() else clock.instant() }
        return StoredFile(sizeBytes = size, lastModified = lastModified)
    }

    override fun exists(repository: String, path: String): Boolean {
        val provider = maven.getRepository(repository)?.storageProvider ?: return false
        val location = location(path) ?: return false
        return provider.exists(location)
    }

    override fun head(repository: String, path: String, maxBytes: Int): String? {
        val provider = maven.getRepository(repository)?.storageProvider ?: return null
        val location = location(path) ?: return null
        val stream = provider.getFile(location)
        if (stream.isErr) return null
        return stream.get().use { String(it.readNBytes(maxBytes), StandardCharsets.UTF_8) }
    }

    /** A path that storage cannot address is not stored, so it reads as missing. */
    private fun location(path: String): Location? = runCatching { Location.of(path) }.getOrNull()

}

/** The origin of a version directory, read from the mirror ledger (artifact search spec, 2.4). */
internal class LedgerOriginLookup(private val ledger: MirrorCacheLedger) : OriginLookup {

    override fun provenance(repository: String, directory: String): Provenance =
        if (ledger.contains(repository, directory)) Provenance(Origin.MIRROR, ledger.sourceHost(repository, directory))
        else Provenance.HOSTED

}
