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

package com.reposilite.image

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.testcontainers.containers.wait.strategy.Wait

/**
 * The deploy target as an operator sets it in a container: `INGOT_TARGET=server` and the legacy
 * `INGOT_LOCAL_DEFAULTFRONTEND=false` must both leave the API up and the dashboard document out.
 *
 * The probe is `/api/status/health` rather than `/`, because `/` is the dashboard on the default target and
 * is not answered at all on the server target.
 */
class DeployTargetContainerTest {

    @Test
    @DisplayName("INGOT_TARGET=server answers the health endpoint and does not serve the dashboard")
    fun `target server serves the api without the dashboard`() {
        assertServesApiWithoutDashboard(mapOf("INGOT_TARGET" to "server"))
    }

    @Test
    @DisplayName("INGOT_LOCAL_DEFAULTFRONTEND=false answers the health endpoint and does not serve the dashboard")
    fun `legacy default frontend switch off serves the api without the dashboard`() {
        assertServesApiWithoutDashboard(mapOf("INGOT_LOCAL_DEFAULTFRONTEND" to "false"))
    }

    private fun assertServesApiWithoutDashboard(environment: Map<String, String>) {
        serverContainer(
            image = ImageUnderTest.server,
            environment = environment,
        )
            .waitingFor(Wait.forHttp(HEALTH_PATH).forPort(DEFAULT_SERVER_PORT).forStatusCode(200))
            .startOrExplain()
            .use { server ->
                val health = Http.get(server.baseUrl() + HEALTH_PATH)
                assertThat(health.status)
                    .describedAs("status of %s with environment %s", HEALTH_PATH, environment)
                    .isEqualTo(200)

                val browse = Http.get(server.baseUrl() + "/browse")
                assertThat(browse.body)
                    .describedAs("GET /browse must not answer with the dashboard document for environment %s", environment)
                    .doesNotContain(DASHBOARD_MARKER)
            }
    }

    private companion object {
        const val HEALTH_PATH = "/api/status/health"
        const val DASHBOARD_MARKER = "<div id=\"app\""
    }

}
