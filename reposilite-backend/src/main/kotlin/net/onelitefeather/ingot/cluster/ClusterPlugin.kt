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

import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import java.util.UUID

/**
 * The optional cluster bus. Without a bus URL in any source it does nothing: no transport is opened, no thread starts and
 * no event is published. With a URL it creates the cluster identifier of the database and opens the transport of the scheme.
 *
 * [transports] maps each accepted scheme to its factory. A scheme without a factory leaves the instance without the bus.
 */
@Plugin(
    name = "cluster",
    dependencies = [
        "access-token",
        "authentication",
        "configuration",
        "shared-configuration",
        "maven",
        "console",
        "cleanup",
        "audit",
        "local-configuration",
    ],
)
class ClusterPlugin(
    private val sources: ClusterSources = ClusterSources.system(),
    private val transports: Map<String, ClusterTransportFactory> = emptyMap(),
) : ReposilitePlugin() {

    override fun initialize(): Facade? {
        val settings = ClusterSettings.read(sources)
        val url = settings.url ?: return null
        settings.warnings.forEach { logger.warn("Cluster | $it") }

        val clusterIdentifier = facade<ConfigurationFacade>()
            .findOrCreateConfiguration(CLUSTER_IDENTIFIER_NAME) { UUID.randomUUID().toString() }
        logger.debug("Cluster | cluster identifier $clusterIdentifier, instance ${settings.instanceId}")

        val factory = transports[url.scheme]
        if (factory == null) {
            logger.warn("Cluster | no transport is available for ${url.scheme} in this build, the instance runs without the bus")
            return null
        }

        logger.info("Cluster | connecting to ${url.redacted()}")
        val connection = factory.open(url, settings)
        event { _: ReposiliteDisposeEvent -> connection.close() }
        return null
    }

    private companion object {
        const val CLUSTER_IDENTIFIER_NAME = "cluster-identifier"
    }

}
