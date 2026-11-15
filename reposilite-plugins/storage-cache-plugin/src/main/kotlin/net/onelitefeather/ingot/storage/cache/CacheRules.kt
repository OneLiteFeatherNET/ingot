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

import com.reposilite.storage.isMutableStorageKey

/**
 * Whether the cache must leave a key to the delegate. The shared rule of the storage (metadata and snapshots) applies, and the cache adds
 * the index files of the repository format: `archetype-catalog.xml` with its checksums, and anything under `.index/`.
 */
internal fun isCacheMutableKey(key: String): Boolean {
    val segments = key.split('/')
    return isMutableStorageKey(key) || segments.last().startsWith("archetype-catalog.xml") || ".index" in segments
}
