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

package com.reposilite.maven

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.errorResponse
import io.javalin.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result

internal class MavenOperationCountersTest {

    @Test
    fun `should count accepted and rejected deploys separately`() {
        // given: a fresh set of counters
        val counters = MavenOperationCounters()

        // when: two deploys are accepted and one is rejected
        counters.recordDeploy(DeployOutcome.ACCEPTED)
        counters.recordDeploy(DeployOutcome.ACCEPTED)
        counters.recordDeploy(DeployOutcome.REJECTED)

        // then: each outcome carries its own count
        assertThat(counters.snapshot().deploys)
            .describedAs("accepted and rejected deploys must be counted apart")
            .containsEntry(DeployOutcome.ACCEPTED, 2L)
            .containsEntry(DeployOutcome.REJECTED, 1L)
    }

    @Test
    fun `should report every upstream outcome even before the first fetch`() {
        // given: a fresh set of counters that has seen no upstream fetch
        val counters = MavenOperationCounters()

        // when: the totals are read
        val upstream = counters.snapshot().upstream

        // then: all four outcomes are present with zero, so the series exist from the first scrape
        assertThat(upstream)
            .describedAs("every upstream outcome must be reported, also when it is zero")
            .containsOnlyKeys(*UpstreamOutcome.entries.toTypedArray())
            .containsValues(0L)
    }

    @Test
    fun `should classify a successful upstream fetch as success`() {
        assertThat(UpstreamOutcome.of(Result.ok("content")))
            .describedAs("a fetch that returned content is a success")
            .isEqualTo(UpstreamOutcome.SUCCESS)
    }

    @Test
    fun `should classify an upstream miss as not found`() {
        assertThat(UpstreamOutcome.of(errorResponse<Any>(HttpStatus.NOT_FOUND, "Not found upstream")))
            .describedAs("a 404 from upstream is a miss, not a failure")
            .isEqualTo(UpstreamOutcome.NOT_FOUND)
    }

    @Test
    fun `should classify a gateway timeout as timeout`() {
        assertThat(UpstreamOutcome.of(errorResponse<Any>(HttpStatus.GATEWAY_TIMEOUT, "Upstream did not answer in time")))
            .describedAs("a 504 from the remote client marks a timeout")
            .isEqualTo(UpstreamOutcome.TIMEOUT)
    }

    @Test
    fun `should classify any other upstream error as failure`() {
        assertThat(UpstreamOutcome.of(errorResponse<Any>(HttpStatus.BAD_GATEWAY, "Upstream answered 500")))
            .describedAs("every other error answer is a failure")
            .isEqualTo(UpstreamOutcome.FAILURE)
    }

}
