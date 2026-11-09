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

package com.reposilite.storage.s3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.retry.RetryPolicyContext
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy
import java.time.Duration

class S3RetryBackoffTest {

    private val samplesPerRetry = 200

    @Test
    fun `the S3 client waits between retries with the SDK full jitter strategy`() {
        assertThat(s3RetryBackoffStrategy())
            .`as`("retries use the SDK full jitter strategy instead of the fixed exponential schedule")
            .isInstanceOf(FullJitterBackoffStrategy::class.java)
    }

    @Test
    fun `each delay lies between zero and the capped exponential ceiling of its retry`() {
        val strategy = s3RetryBackoffStrategy()
        val retries = (0..4) + (6..10)

        retries.forEach { retry ->
            val ceiling = minOf(Duration.ofSeconds(1L shl retry), Duration.ofMinutes(1))
            repeat(samplesPerRetry) {
                val delay = strategy.computeDelayBeforeNextRetry(retryContext(retry))
                assertThat(delay)
                    .`as`("delay before retry $retry must not be negative")
                    .isGreaterThanOrEqualTo(Duration.ZERO)
                assertThat(delay)
                    .`as`("delay before retry $retry must not exceed $ceiling")
                    .isLessThanOrEqualTo(ceiling)
            }
        }
    }

    @Test
    fun `independent retries of the same request do not all wait the same time`() {
        val strategy = s3RetryBackoffStrategy()

        val distinctDelays = (1..samplesPerRetry)
            .map { strategy.computeDelayBeforeNextRetry(retryContext(4)) }
            .toSet()

        assertThat(distinctDelays.size)
            .`as`("$samplesPerRetry draws for the same retry must not all be equal, got ${distinctDelays.size} distinct delays")
            .isGreaterThan(1)
    }

    private fun retryContext(retriesAttempted: Int): RetryPolicyContext =
        RetryPolicyContext.builder()
            .retriesAttempted(retriesAttempted)
            .build()
}
