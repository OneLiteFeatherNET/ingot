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

package net.onelitefeather.ingot.search.runs

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.internalServerError
import com.reposilite.storage.StorageEntry
import com.reposilite.storage.StorageEntryPage
import com.reposilite.storage.StorageEntrySource
import com.reposilite.storage.api.Location
import panda.std.Result
import panda.std.asSuccess
import java.time.Instant
import java.util.TreeMap

/** Thrown from a listing to stand in for a process that dies: it is an [Error], so no handler of the service swallows it. */
internal class SimulatedCrash : Error("simulated crash")

/**
 * Repositories whose files the test sets. Every repository lists its files in path order, strictly after the cursor, and
 * records each request. No storage provider is involved.
 */
internal class FakeRepositoryEntries : RepositoryEntries {

    data class Request(val repository: String, val prefix: String, val after: String?)

    private val repositories = TreeMap<String, TreeMap<String, StorageEntry>>()

    /** Every listing request in the order it arrived. */
    val requests = mutableListOf<Request>()

    /** Repositories whose listing answers with an error. */
    val failing = mutableSetOf<String>()

    /** The 1-based number of the listing request that crashes, or null for none. */
    var crashOnRequest: Int? = null

    /** Called with the 1-based request number before the request is answered, for a test that acts mid-run. */
    var onRequest: (Int) -> Unit = {}

    fun put(repository: String, path: String, size: Long = 1, lastModified: Instant = Instant.parse("2026-10-01T00:00:00Z")) {
        repositories.getOrPut(repository) { TreeMap() }[path] = StorageEntry(Location.of(path), size, lastModified)
    }

    fun remove(repository: String, path: String) {
        repositories[repository]?.remove(path)
    }

    fun addRepository(repository: String) {
        repositories.getOrPut(repository) { TreeMap() }
    }

    fun removeRepository(repository: String) {
        repositories.remove(repository)
    }

    override fun names(): List<String> = repositories.keys.toList()

    override fun source(repository: String): StorageEntrySource? {
        val files = repositories[repository] ?: return null
        return object : StorageEntrySource {
            override fun listEntries(prefix: Location, after: String?, limit: Int) = list(repository, files, prefix, after, limit)
        }
    }

    private fun list(
        repository: String,
        files: TreeMap<String, StorageEntry>,
        prefix: Location,
        after: String?,
        limit: Int,
    ): Result<StorageEntryPage, ErrorResponse> {
        requests += Request(repository, prefix.toString(), after)
        onRequest(requests.size)
        if (requests.size == crashOnRequest) throw SimulatedCrash()
        if (repository in failing) return internalServerError("storage unavailable")

        val directory = prefix.toString().let { if (it.isEmpty()) "" else "$it/" }
        val matching = files.values.filter { it.path.toString().startsWith(directory) && (after == null || it.path.toString() > after) }
        val page = matching.take(limit)
        return StorageEntryPage(page, if (matching.size > limit) page.last().path.toString() else null).asSuccess()
    }

}
