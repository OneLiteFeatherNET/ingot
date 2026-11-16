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

package net.onelitefeather.ingot.deploy

import com.reposilite.Reposilite
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.plugin.api.EventListener
import com.reposilite.plugin.api.Priorities
import com.reposilite.web.api.RoutingSetupEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * On the targets `all` and `server` the instance registers every route the plugins offer, so the worker restriction
 * leaves them as they were before the worker target existed. The test compares the restricted set with the full set of
 * the same start instead of a fixed list, so a route added on main later does not break it.
 */
internal abstract class RegisteredRoutesSnapshotTest(
    private val target: String,
) : ReposiliteSpecification() {

    private var offered: List<String> = emptyList()
    private var registered: List<String> = emptyList()

    override fun overrideParameters(parameters: ReposiliteParameters) {
        parameters.target = target
    }

    override fun beforeLaunch(reposilite: Reposilite) {
        // Runs after every plugin has added its routes, so it sees the complete set the server registers.
        reposilite.extensions.registerEvent(object : EventListener<RoutingSetupEvent> {
            override fun priority(): Double = Priorities.LOW

            override fun onCall(event: RoutingSetupEvent) {
                val routes = event.getRoutes()
                offered = routes.flatMap { it.toDslRoutes() }.map { "${it.method.name} ${it.path}" }.distinct().sorted()
                registered = WorkerRoutes.restrict(routes, reposilite.parameters.deployTarget)
                    .flatMap { it.toDslRoutes() }
                    .map { "${it.method.name} ${it.path}" }
                    .distinct()
                    .sorted()
            }
        })
    }

    @Test
    fun `should register every offered route on the target`() {
        assertThat(offered)
            .describedAs("routes offered by the plugins on target $target")
            .contains("GET /api/status/instance", "GET /api/maven/details/{repository}")
        assertThat(registered)
            .describedAs("registered routes on target $target")
            .containsExactlyElementsOf(offered)
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RegisteredRoutesAllSnapshotTest : RegisteredRoutesSnapshotTest("all")

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RegisteredRoutesServerSnapshotTest : RegisteredRoutesSnapshotTest("server")
