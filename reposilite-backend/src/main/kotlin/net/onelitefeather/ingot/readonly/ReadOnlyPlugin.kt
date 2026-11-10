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

package net.onelitefeather.ingot.readonly

import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.reposilite
import com.reposilite.web.api.RoutingSetupEvent
import java.time.Clock

/**
 * Provides the read-only switch. The environment is read when the instance is built, and the stored state comes from the
 * configuration store, shared by every replica that shares the database.
 */
@Plugin(name = "read-only", dependencies = ["configuration"])
internal class ReadOnlyPlugin : ReposilitePlugin() {

    override fun initialize(): ReadOnlyFacade {
        val setting = reposilite().readOnlySetting
        val store = ConfigurationReadOnlyStore(facade<ConfigurationFacade>())
        val readOnlyFacade = ReadOnlyFacade(
            cache = ReadOnlyStateCache(Clock.systemUTC(), store, setting),
            store = store,
            setting = setting,
            extensions = reposilite().extensions,
        )

        event { event: RoutingSetupEvent ->
            event.registerRoutes(ReadOnlyEndpoints(readOnlyFacade))
        }

        return readOnlyFacade
    }

}
