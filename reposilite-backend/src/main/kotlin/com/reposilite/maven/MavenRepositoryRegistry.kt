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

package com.reposilite.maven

import com.reposilite.plugin.api.StorageUsageRefreshedEvent
import com.reposilite.storage.api.toLocation
import net.onelitefeather.ingot.repository.RegisteredRepository
import net.onelitefeather.ingot.repository.RepositoryCacheCoordinator
import net.onelitefeather.ingot.storage.capability.ClusterBus
import net.onelitefeather.ingot.repository.RepositoryRegistry

/**
 * The registry over the live repositories of [repositoryProvider], and the cache port of the cluster bus. Registered by the maven
 * plugin, see [MavenFacade]. The read side is what consumers see; the write side only delegates to the caches of the repositories.
 */
internal class MavenRepositoryRegistry(
    private val repositoryProvider: RepositoryProvider,
    private val storageUsageService: StorageUsageService,
) : RepositoryRegistry, RepositoryCacheCoordinator {

    override fun repository(name: String): RegisteredRepository? =
        repositoryProvider.getRepository(name)

    override fun repositories(): List<RegisteredRepository> =
        repositoryProvider.getRepositories().toList()

    override fun invalidateResolution(repository: String, path: String) {
        repositoryProvider.getRepository(repository)?.resolutionCache?.invalidate(path.toLocation())
    }

    override fun bindToClusterBus(clusterBus: ClusterBus) =
        repositoryProvider.bindToClusterBus(clusterBus)

    override fun applyReceivedStorageUsage(event: StorageUsageRefreshedEvent): Boolean =
        storageUsageService.applyReceived(event)

}
