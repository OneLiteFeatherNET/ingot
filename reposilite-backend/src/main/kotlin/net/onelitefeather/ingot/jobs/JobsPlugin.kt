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
package net.onelitefeather.ingot.jobs

import com.reposilite.cleanup.CleanupService
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.api.ReposilitePostInitializeEvent
import com.reposilite.plugin.api.jobs.JobRegistrationEvent
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.parameters
import com.reposilite.plugin.reposilite
import com.reposilite.token.AccessTokenFacade
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.search.runs.RunStore
import java.time.Clock

/** The state of the Jobs page: the aggregator that answers its endpoints. */
class JobsFacade internal constructor(val aggregator: JobAggregator) : Facade

/**
 * Wires the Jobs page. The core sources (the search index and the cleanup policies) register at once. After every plugin has
 * initialised, the registration event lets the plugins add their sources, and the registry is then sealed.
 */
@Plugin(name = "jobs", dependencies = ["search", "cleanup"])
internal class JobsPlugin : ReposilitePlugin() {

    override fun initialize(): JobsFacade {
        val clock = parameters().clock ?: Clock.systemDefaultZone()
        val registry = JobSourceRegistry { source ->
            logger.warn("JOBS | The job source $source registered after the registration event and is not listed")
        }
        registry.register(IndexJobSource(RunStore(reposilite().database, clock)))
        registry.register(CleanupJobSource(facade<CleanupService>()))
        val aggregator = JobAggregator(registry)
        val accessTokens = facade<AccessTokenFacade>()

        event { _: ReposilitePostInitializeEvent ->
            reposilite().extensions.emitEvent(JobRegistrationEvent(registry))
            registry.seal()
        }
        // Registered in both states: a disabled instance answers the same routes like any other.
        event { event: RoutingSetupEvent -> event.registerRoutes(JobsEndpoints(aggregator, accessTokens)) }
        return JobsFacade(aggregator)
    }

}
