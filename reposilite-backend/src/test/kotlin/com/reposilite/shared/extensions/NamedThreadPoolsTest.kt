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

package com.reposilite.shared.extensions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class NamedThreadPoolsTest {

    private val awaitTimeoutSeconds = 10L

    @Test
    fun `should run tasks concurrently up to the configured maximum`() {
        val maximum = 4
        val pool = newFixedThreadPool(min = 0, max = maximum, prefix = "Test | IO")
        val started = CountDownLatch(maximum)
        val release = CountDownLatch(1)

        try {
            repeat(maximum) {
                pool.execute {
                    started.countDown()
                    release.await(awaitTimeoutSeconds, TimeUnit.SECONDS)
                }
            }

            assertThat(started.await(awaitTimeoutSeconds, TimeUnit.SECONDS))
                .withFailMessage("Expected $maximum tasks to run concurrently, but only ${maximum - started.count} started")
                .isTrue()
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `should queue tasks beyond the configured maximum`() {
        val maximum = 4
        val pool = newFixedThreadPool(min = 0, max = maximum, prefix = "Test | IO")
        val started = CountDownLatch(maximum)
        val release = CountDownLatch(1)

        try {
            repeat(maximum) {
                pool.execute {
                    started.countDown()
                    release.await(awaitTimeoutSeconds, TimeUnit.SECONDS)
                }
            }
            assertThat(started.await(awaitTimeoutSeconds, TimeUnit.SECONDS))
                .withFailMessage("Expected $maximum tasks to occupy the pool before queueing")
                .isTrue()

            pool.execute { }

            val executor = pool as ThreadPoolExecutor
            assertThat(executor.maximumPoolSize)
                .withFailMessage("Pool maximum should equal the configured $maximum")
                .isEqualTo(maximum)
            assertThat(executor.queue.size)
                .withFailMessage("Task beyond the configured maximum should wait in the queue")
                .isEqualTo(1)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

}
