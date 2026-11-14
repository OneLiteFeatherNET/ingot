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

@file:OptIn(ExperimentalIngotStorageApi::class)

package net.onelitefeather.ingot.storage.capability

import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi

/**
 * The repositories whose namespaces collide, each with the message its settings give for the refusal. A repository whose settings
 * have no namespace key for it takes no part. Every repository of a collision is reported, not only the later one.
 */
internal fun findSharedNamespaceConflicts(repositories: List<Pair<String, SharedNamespaceAware>>): Map<String, String> {
    val keyed = repositories.mapNotNull { (id, settings) -> settings.namespaceKey(id)?.let { Triple(id, settings, it) } }
    return keyed
        .filter { (id, settings, key) ->
            keyed.any { (otherId, _, otherKey) -> otherId != id && settings.overlaps(key, otherKey) }
        }
        .associate { (id, settings, _) -> id to settings.overlapMessage() }
}
