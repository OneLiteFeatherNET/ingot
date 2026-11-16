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
package net.onelitefeather.ingot.identity.storage

import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The last policy revision this replica has read from the shared database. [refreshOnce] reads the row once, so a test
 * can drive a refresh without a timer; [start] runs the same refresh on an interval for the lifetime of the server.
 * A failed read keeps the last value seen: the loop must survive it, and the cache bound still applies in the meantime.
 */
class AuthorizationRevision(private val store: IdentityStore) : AutoCloseable {

    @Volatile
    var lastSeen: Long = 0L
        private set

    private var executor: ScheduledExecutorService? = null

    fun refreshOnce(): Long {
        lastSeen = store.policyRevision()
        return lastSeen
    }

    @Synchronized
    fun start(interval: Duration) {
        check(executor == null) { "The policy revision refresh is already running" }
        executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "identity-policy-revision").apply { isDaemon = true }
        }.also { scheduler ->
            scheduler.scheduleWithFixedDelay({ runCatching { refreshOnce() } }, 0, interval.toMillis(), TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized
    override fun close() {
        executor?.shutdownNow()
        executor = null
    }
}
