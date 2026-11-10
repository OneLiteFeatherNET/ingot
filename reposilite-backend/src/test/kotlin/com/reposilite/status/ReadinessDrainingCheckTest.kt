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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference

internal class ReadinessDrainingCheckTest {

    private fun statusFacade(
        status: () -> Boolean = { true },
        readinessChecks: List<ReadinessCheck> = emptyList(),
    ): StatusFacade =
        StatusFacade(
            testEnv = true,
            maxThreads = Reference.reference(40),
            status = status,
            remoteVersionUrl = "",
            failureFacade = FailureFacade(InMemoryLogger()),
            readinessChecks = readinessChecks,
        )

    @Test
    fun `should not be draining on a new status facade`() {
        // given: a status facade that has just been created
        val status = statusFacade()

        // when: the draining flag is read
        // then: the instance is not draining
        assertThat(status.isDraining()).`as`("a new instance is not draining").isFalse
    }

    @Test
    fun `should stay draining once the flag is set`() {
        // given: a status facade that starts draining
        val status = statusFacade()

        // when: the flag is set and the readiness is evaluated again afterwards
        status.startDraining()
        status.readinessProbe.evaluate()

        // then: the flag is still set, it is not cleared again in the same process
        assertThat(status.isDraining()).`as`("the flag stays set").isTrue
    }

    @Test
    fun `should list draining first in the failing checks when the flag is set`() {
        // given: a draining instance whose plugin check also fails
        val status = statusFacade(
            readinessChecks = listOf(
                ReadinessCheck("plugins") { false },
                ReadinessCheck("storage") { true },
            ),
        )
        status.startDraining()

        // when: the readiness is evaluated
        val response = status.readinessProbe.evaluate()

        // then: the answer is down and draining is the first failing check, ahead of the plugin check
        assertThat(response.status).`as`("a draining instance is not ready").isEqualTo("DOWN")
        assertThat(response.failing).`as`("draining is listed first").containsExactly("draining", "plugins")
    }

    @Test
    fun `should not list draining when the flag is not set`() {
        // given: an instance that is not draining and whose checks pass
        val status = statusFacade(
            readinessChecks = listOf(ReadinessCheck("plugins") { true }),
        )

        // when: the readiness is evaluated
        val response = status.readinessProbe.evaluate()

        // then: the instance is ready and draining is not listed
        assertThat(response.status).`as`("a healthy instance is ready").isEqualTo("UP")
        assertThat(response.failing).`as`("draining is not listed").isEmpty()
    }

    @Test
    fun `should keep the liveness answer up while draining`() {
        // given: a web server that is alive and an instance that starts draining
        val status = statusFacade(status = { true })

        // when: the flag is set
        status.startDraining()

        // then: the liveness answer does not change, so the orchestrator does not restart the instance
        assertThat(status.isAlive()).`as`("a draining instance is still alive").isTrue
    }

}
