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

@file:OptIn(net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi::class)

package net.onelitefeather.ingot.repository

import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.StorageUsageRefreshedEvent
import net.onelitefeather.ingot.storage.capability.ClusterBus

/**
 * The write side of the repository caches that a cluster bus drives: the resolution cache and the storage usage figures. It is
 * a port of its own, not part of [RepositoryRegistry], because these calls change state. Not yet stable for plugins.
 */
interface RepositoryCacheCoordinator : Facade {

    /** Drops the resolution entry of [path] in [repository], as a remote instance announced. */
    fun invalidateResolution(repository: String, path: String)

    /** Bounds the lifetime of resolution entries and the storage caches, because a cluster bus is open for this instance. */
    fun bindToClusterBus(clusterBus: ClusterBus)

    /** Keeps the usage figures another instance computed, see the storage usage service. */
    fun applyReceivedStorageUsage(event: StorageUsageRefreshedEvent): Boolean

}
