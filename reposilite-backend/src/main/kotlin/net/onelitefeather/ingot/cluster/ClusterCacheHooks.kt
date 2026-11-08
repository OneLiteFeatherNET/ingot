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

/**
 * What the cluster plugin asks of the local caches and of the shared configuration when a cache event arrives or the bus opens.
 * The production implementation resolves the facades; tests supply their own.
 */
interface ClusterCacheHooks {

    /** A resolution-metadata file of [repository] at [path] changed on another instance, so its pin must be dropped here. */
    fun resolutionInvalidated(repository: String, path: String)

    /** Shared configuration of [domain] was stored on another instance, so it is loaded before the next scheduled poll. */
    fun sharedConfigurationChanged(domain: String)

    /** The server has started with a bus configured, so resolution entries are bounded from now on. */
    fun clusterBusOpened()
}
