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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.elasticsearch

import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Stops the traffic to a cluster that does not answer (design D4). After [threshold] transport failures in a row the circuit
 * opens for [openFor]; a call made meanwhile is rejected with a [BackendBusyException] whose delay is the time left. When the
 * time has passed, exactly one call goes through (half open): its success closes the circuit, its failure opens it again.
 * Any answer of the cluster, including an error status, counts as a success, because the cluster was reached.
 *
 * Time comes from [clock] only, so that the tests move it by hand.
 */
internal class ElasticsearchCircuitBreaker(
    private val clock: Clock,
    private val threshold: Int = FAILURES_TO_OPEN,
    private val openFor: Duration = OPEN_FOR,
) {

    private var failures = 0
    private var openUntil: Instant? = null
    private var probing = false

    /** The time the circuit stays open, or null when a call may be sent now. */
    @Synchronized
    fun openRemaining(): Duration? {
        val until = openUntil ?: return null
        val remaining = Duration.between(clock.instant(), until)
        return if (remaining > Duration.ZERO) remaining else null
    }

    /** Runs [block] when the circuit admits a call, and records its outcome. */
    fun <T> call(block: () -> T): T {
        admit()
        val result = try {
            block()
        } catch (failure: IOException) {
            settle(transportFailed = true)
            throw failure
        } catch (other: Throwable) {
            settle(transportFailed = false)
            throw other
        }
        settle(transportFailed = false)
        return result
    }

    @Synchronized
    private fun admit() {
        val until = openUntil ?: return
        val now = clock.instant()
        if (now.isBefore(until)) {
            throw BackendBusyException("the search cluster is not reachable, requests are paused", Duration.between(now, until))
        }
        if (probing) {
            throw BackendBusyException("the search cluster is being probed, the request waits for its answer", DEFAULT_RETRY_AFTER)
        }
        probing = true
    }

    @Synchronized
    private fun settle(transportFailed: Boolean) {
        probing = false
        if (transportFailed) {
            failures += 1
            if (openUntil != null || failures >= threshold) {
                openUntil = clock.instant().plus(openFor)
            }
        } else {
            failures = 0
            openUntil = null
        }
    }

    companion object {
        const val FAILURES_TO_OPEN: Int = 5
        val OPEN_FOR: Duration = Duration.ofSeconds(30)
    }

}
