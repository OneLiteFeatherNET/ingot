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
import org.eclipse.jetty.server.handler.GracefulHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The default settings: no drain timeout and no pre-stop delay. The stop must be the one of earlier versions.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class GracefulDrainDisabledIntegrationTest : GracefulDrainSpecification(ShutdownSettings()) {

    @Test
    fun `should leave the handler chain without the graceful wrapper`() {
        // when: the instance is running with the default settings
        // then: the handler chain of the server is not wrapped in a graceful handler
        assertThat(reposilite.webServer.rootHandler())
            .`as`("the root handler of the server")
            .isNotInstanceOf(GracefulHandler::class.java)
    }

    @Test
    fun `should not call the pre-stop delay when it is zero`() {
        // when: the instance shuts down with no request in flight
        reposilite.shutdown()

        // then: no delay was taken and no drain began
        assertThat(events.toList())
            .`as`("the shutdown calls")
            .isEmpty()
    }

    @Test
    @Timeout(60)
    fun `should stop at once without waiting for a request in flight`() {
        // given: a request held in its handler, which the test does not release before the stop
        startHeldRequest()

        // when: the shutdown runs on this thread
        reposilite.shutdown()

        // then: the stop returned while the request was still held, so it did not wait for the request
        assertThat(release.count).`as`("the held request is still waiting when the stop returns").isEqualTo(1L)
    }

}
