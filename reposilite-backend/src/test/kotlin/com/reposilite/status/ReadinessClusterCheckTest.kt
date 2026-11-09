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

package com.reposilite.status

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.api.ClusterBusResponse
import com.reposilite.status.api.ClusterStatusResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference

internal class ReadinessClusterCheckTest {

    private fun statusFacade(): StatusFacade =
        StatusFacade(
            testEnv = true,
            maxThreads = Reference.reference(40),
            status = { true },
            remoteVersionUrl = "",
            failureFacade = FailureFacade(InMemoryLogger()),
        )

    private fun busReporting(state: String): ClusterStatusSource =
        object : ClusterStatusSource {
            override fun clusterStatus(local: InstanceFigures) =
                ClusterStatusResponse(
                    bus = ClusterBusResponse(
                        state = state,
                        transport = "nats",
                        url = "nats://bus",
                        published = 0,
                        applied = 0,
                        ignored = 0,
                    ),
                    instances = emptyList(),
                )
        }

    @Test
    fun `should not evaluate the cluster check when no bus is attached`() {
        // given: a status facade without a cluster bus
        val status = statusFacade()

        // when: readiness is evaluated
        val response = status.readinessProbe.evaluate()

        // then: the cluster check does not apply and the instance is ready
        assertThat(response.status).`as`("no bus means no cluster check").isEqualTo("UP")
        assertThat(response.failing).`as`("the cluster is not listed without a bus").isEmpty()
    }

    @Test
    fun `should pass the cluster check when the bus is connected`() {
        // given: a cluster bus attached in the connected state
        val status = statusFacade()
        status.attachClusterStatus(busReporting("connected"))

        // when: readiness is evaluated
        val response = status.readinessProbe.evaluate()

        // then: the cluster check passes
        assertThat(response.failing).`as`("a connected bus is ready").isEmpty()
    }

    @Test
    fun `should fail the cluster check when the bus is reconnecting`() {
        // given: a cluster bus attached while it is reconnecting
        val status = statusFacade()
        status.attachClusterStatus(busReporting("reconnecting"))

        // when: readiness is evaluated
        val response = status.readinessProbe.evaluate()

        // then: the cluster check fails and the instance is not ready
        assertThat(response.status).`as`("a reconnecting bus is not ready").isEqualTo("DOWN")
        assertThat(response.failing).`as`("the cluster check is named").containsExactly("cluster")
    }

    @Test
    fun `should fail the cluster check when the bus is disabled`() {
        // given: a cluster bus attached in the disabled state
        val status = statusFacade()
        status.attachClusterStatus(busReporting("disabled"))

        // when: readiness is evaluated
        val response = status.readinessProbe.evaluate()

        // then: the cluster check fails and the instance is not ready
        assertThat(response.status).`as`("a disabled bus is not ready").isEqualTo("DOWN")
        assertThat(response.failing).`as`("the cluster check is named").containsExactly("cluster")
    }

}
