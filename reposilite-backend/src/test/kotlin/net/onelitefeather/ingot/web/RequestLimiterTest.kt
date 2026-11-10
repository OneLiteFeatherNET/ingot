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

package net.onelitefeather.ingot.web

import net.onelitefeather.ingot.web.RequestLimiter.Admission
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RequestLimiterTest {

    @Test
    fun `should admit requests up to the limit`() {
        // given: a limiter with two slots
        val limiter = RequestLimiter(limit = 2)

        // when: two requests arrive
        val first = limiter.admit("/maven/releases/a.jar")
        val second = limiter.admit("/maven/releases/b.jar")

        // then: both are admitted and both slots are in use
        assertThat(first).`as`("first request").isInstanceOf(Admission.Admitted::class.java)
        assertThat(second).`as`("second request").isInstanceOf(Admission.Admitted::class.java)
        assertThat(limiter.inFlight).`as`("requests in progress").isEqualTo(2)
    }

    @Test
    fun `should refuse a request above the limit without holding it`() {
        // given: a limiter with one slot that is taken
        val limiter = RequestLimiter(limit = 1)
        limiter.admit("/maven/releases/a.jar")

        // when: a second request arrives
        val refused = limiter.admit("/maven/releases/b.jar")

        // then: it is refused at once and does not take a slot
        assertThat(refused).`as`("request above the limit").isEqualTo(Admission.Refused)
        assertThat(limiter.inFlight).`as`("requests in progress").isEqualTo(1)
    }

    @Test
    fun `should free one slot when a request completes`() {
        // given: a limiter with one slot that is taken
        val limiter = RequestLimiter(limit = 1)
        val admitted = limiter.admit("/maven/releases/a.jar") as Admission.Admitted

        // when: the request completes and the next one arrives
        admitted.lease.release()
        val next = limiter.admit("/maven/releases/b.jar")

        // then: the slot is free again
        assertThat(next).`as`("request after the completion").isInstanceOf(Admission.Admitted::class.java)
    }

    @Test
    fun `should free nothing when a request completes twice`() {
        // given: two requests in progress with a limit of two, and the first completes twice
        val limiter = RequestLimiter(limit = 2)
        val first = limiter.admit("/maven/releases/a.jar") as Admission.Admitted
        limiter.admit("/maven/releases/b.jar")
        first.lease.release()

        // when: the first request completes a second time
        first.lease.release()

        // then: still one request is in progress, so only one slot is free
        assertThat(limiter.inFlight).`as`("requests in progress after the second completion").isEqualTo(1)
        assertThat(limiter.admit("/maven/releases/c.jar")).`as`("the free slot").isInstanceOf(Admission.Admitted::class.java)
        assertThat(limiter.admit("/maven/releases/d.jar")).`as`("no slot beyond the limit").isEqualTo(Admission.Refused)
    }

    @Test
    fun `should never count the liveness path`() {
        // given: a limiter with one slot
        val limiter = RequestLimiter(limit = 1)

        // when: the liveness path is requested
        val admission = limiter.admit("/api/status/health")

        // then: it is exempt and takes no slot
        assertThat(admission).`as`("liveness request").isEqualTo(Admission.Exempt)
        assertThat(limiter.inFlight).`as`("requests in progress").isZero
    }

    @Test
    fun `should never count the readiness path`() {
        // given: a limiter with one slot
        val limiter = RequestLimiter(limit = 1)

        // when: the readiness path is requested
        val admission = limiter.admit("/api/status/ready")

        // then: it is exempt and takes no slot
        assertThat(admission).`as`("readiness request").isEqualTo(Admission.Exempt)
        assertThat(limiter.inFlight).`as`("requests in progress").isZero
    }

    @Test
    fun `should count paths that only start like an exempt path`() {
        // given: a limiter with one slot
        val limiter = RequestLimiter(limit = 1)

        // when: paths that extend or prefix the exempt paths are requested
        val healthy = limiter.admit("/api/status/healthy")
        val extra = limiter.admit("/api/status/health/extra")

        // then: the first one takes the only slot and the second one is refused
        assertThat(healthy).`as`("path that extends health by letters").isInstanceOf(Admission.Admitted::class.java)
        assertThat(extra).`as`("path below health").isEqualTo(Admission.Refused)
    }

    @Test
    fun `should match the exempt path after the base path`() {
        // given: a limiter under a base path with one slot
        val limiter = RequestLimiter(limit = 1, basePath = "/reposilite")

        // when: the liveness path under the base path is requested
        val admission = limiter.admit("/reposilite/api/status/health")

        // then: it is exempt
        assertThat(admission).`as`("liveness request under the base path").isEqualTo(Admission.Exempt)
    }

    @Test
    fun `should free the slot when a request fails`() {
        // given: a request in progress
        val limiter = RequestLimiter(limit = 1)
        val admitted = limiter.admit("/maven/releases/a.jar") as Admission.Admitted

        // when: the request fails and its completion releases the slot
        admitted.lease.release()

        // then: the count is back at zero
        assertThat(limiter.inFlight).`as`("requests in progress after the failure").isZero
    }

}
