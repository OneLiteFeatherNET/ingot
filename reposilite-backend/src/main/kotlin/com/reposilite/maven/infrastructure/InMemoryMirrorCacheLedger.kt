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

package com.reposilite.maven.infrastructure

import com.reposilite.maven.MirrorCacheLedger
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Keeps the provenance in memory. Used by tests, where no instance database exists. */
internal class InMemoryMirrorCacheLedger : MirrorCacheLedger {

    /** Key: repository and version path. Value: last access. */
    private val rows = ConcurrentHashMap<Pair<String, String>, Instant>()

    override fun insertIfAbsent(repository: String, path: String, now: Instant) {
        rows.putIfAbsent(repository to path, now)
    }

    override fun contains(repository: String, path: String): Boolean =
        rows.containsKey(repository to path)

    override fun markAccessed(repository: String, path: String, now: Instant): Boolean =
        rows.computeIfPresent(repository to path) { _, lastAccess -> maxOf(lastAccess, now) } != null

    override fun deleteVersion(repository: String, path: String) {
        rows.remove(repository to path)
    }

    override fun deleteSubtree(repository: String, path: String) {
        rows.keys.removeIf { (repo, entry) -> repo == repository && (entry == path || entry.startsWith("$path/")) }
    }

    override fun find(repository: String): Map<String, Instant> =
        rows.entries
            .filter { (key, _) -> key.first == repository }
            .associate { (key, lastAccess) -> key.second to lastAccess }

}
