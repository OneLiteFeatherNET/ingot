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

package net.onelitefeather.ingot.cluster

import java.util.concurrent.atomic.AtomicLong

/** Counts of the bus traffic of this instance, shown in the status output. */
class ClusterCounters {

    private val publishedCount = AtomicLong()
    private val appliedCount = AtomicLong()
    private val ignoredCount = AtomicLong()

    val published: Long
        get() = publishedCount.get()

    val applied: Long
        get() = appliedCount.get()

    val ignored: Long
        get() = ignoredCount.get()

    fun countPublished() {
        publishedCount.incrementAndGet()
    }

    fun countApplied() {
        appliedCount.incrementAndGet()
    }

    fun countIgnored() {
        ignoredCount.incrementAndGet()
    }
}

/**
 * Decides which received messages are applied. A message is ignored when it cannot be read, when its version or type is
 * unknown, when it belongs to another cluster, or when this instance published it itself.
 */
class ClusterReceiver(
    private val clusterIdentifier: String,
    private val instanceIdentifier: String,
    private val counters: ClusterCounters,
) {

    /**
     * @return the envelope to apply, or `null` when the message is ignored
     */
    fun accept(raw: String): ClusterEnvelope? {
        val envelope = when (val decoded = ClusterEnvelopeCodec.decode(raw)) {
            is ClusterDecodeResult.Accepted -> decoded.envelope
            is ClusterDecodeResult.Ignored -> return ignore()
        }

        if (envelope.cluster != clusterIdentifier || envelope.origin == instanceIdentifier) {
            return ignore()
        }

        counters.countApplied()
        return envelope
    }

    private fun ignore(): ClusterEnvelope? {
        counters.countIgnored()
        return null
    }
}
