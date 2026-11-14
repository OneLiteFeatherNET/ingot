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

package net.onelitefeather.ingot.cluster

import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.javadocs.JavadocFacade
import net.onelitefeather.ingot.repository.RepositoryCacheCoordinator
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.facade
import net.onelitefeather.ingot.readonly.ReadOnlyFacade
import net.onelitefeather.ingot.storage.capability.ClusterBus

/** Applies the cache hooks through the facades of the running server. Facades are looked up when a hook runs, not earlier. */
internal class FacadeCacheHooks(private val plugin: ReposilitePlugin) : ClusterCacheHooks {

    override fun resolutionInvalidated(repository: String, path: String) {
        plugin.facade<RepositoryCacheCoordinator>().invalidateResolution(repository, path)
    }

    override fun sharedConfigurationChanged(domain: String) {
        plugin.facade<SharedConfigurationFacade>().reloadIfChanged()
    }

    override fun javadocInvalidated(repository: String, path: String) {
        val javadoc = plugin.extensions().getFacades().filterIsInstance<JavadocFacade>().firstOrNull()
        if (javadoc == null) {
            plugin.logger.debug("Cluster | javadoc invalidation of $repository/$path not applied, no javadoc facade")
            return
        }
        javadoc.evictReceived(repository, path).onError {
            plugin.logger.error("Cluster | javadoc invalidation of $repository/$path refused: ${it.message}")
        }
    }

    override fun clusterBusOpened() {
        plugin.facade<RepositoryCacheCoordinator>().bindToClusterBus(ClusterBusPort)
    }

    override fun readOnlyChanged() {
        plugin.facade<ReadOnlyFacade>().refresh()
    }
}

/** The cluster bus of this instance, as the storage capabilities see it. The bus has no members yet, so the port only identifies it. */
internal object ClusterBusPort : ClusterBus
