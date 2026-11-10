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

package com.reposilite.web

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import net.onelitefeather.ingot.web.ShutdownSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

/**
 * A drain with a timeout of one second and no delay. The held request is never released during the stop, so the stop
 * can only finish by the timeout. The test's [Timeout] only guards against a stop that never returns.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class GracefulDrainTimeoutIntegrationTest : GracefulDrainSpecification(
    ShutdownSettings(timeout = Duration.ofSeconds(1)),
) {

    @Test
    @Timeout(60)
    fun `should stop when the timeout ends while a request is still in flight`() {
        // given: a request held in its handler, which the test does not release before the stop
        startHeldRequest()

        // when: the shutdown runs on this thread
        reposilite.shutdown()

        // then: the stop returned although the request is still held, so it did not wait for it beyond the timeout
        assertThat(release.count).`as`("the held request is still waiting when the stop returns").isEqualTo(1L)
    }

    @Test
    @Timeout(60)
    fun `should count the requests cut off when the timeout ends`() {
        // given: a request held in its handler, which the test does not release before the stop
        startHeldRequest()

        // when: the shutdown runs on this thread and the timeout ends
        reposilite.shutdown()

        // then: the one request that was still in flight is counted as cut off, the same count the warning reports
        assertThat(reposilite.webServer.cutOffRequests())
            .`as`("requests cut off by the timeout")
            .isEqualTo(1L)
    }

}
