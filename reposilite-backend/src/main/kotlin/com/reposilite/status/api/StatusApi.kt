/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.status.api

import com.reposilite.statistics.toUTCMillis
import java.time.LocalDateTime

data class InstanceStatusResponse(
    val version: String,
    val latestVersion: String,
    val uptime: Long,
    val usedMemory: Double,
    val maxMemory: Int,
    val usedThreads: Int,
    val maxThreads: Int,
    val failuresCount: Int,
    /** Whether writes are refused on this instance, and whether the environment or the stored state says so */
    val readOnly: Boolean = false,
    val readOnlySource: String = "stored",
    /** Present only when a cluster bus is configured, so that a response without a bus keeps its shape */
    val cluster: ClusterStatusResponse? = null
)

data class ClusterStatusResponse(
    val bus: ClusterBusResponse,
    /** The local instance first, then the instances received from the bus, ordered by identifier */
    val instances: List<ClusterInstanceResponse>
)

data class ClusterInstanceResponse(
    val id: String,
    val version: String,
    /** Epoch milliseconds of the last restart of that instance */
    val uptimeStart: Long,
    val usedMemory: Double,
    val maxMemory: Int,
    val usedThreads: Int,
    val maxThreads: Int,
    val failuresCount: Int,
    /** up, down or unknown */
    val health: String,
    /** True after one interval without a snapshot. Instances silent for three intervals are dropped. */
    val stale: Boolean,
    /** Milliseconds between two snapshots of that instance */
    val interval: Long,
    /** Oldest first, epoch milliseconds in at */
    val samples: List<StatusSnapshot>
)

data class ClusterBusResponse(
    /** connected, reconnecting or disabled */
    val state: String,
    /** The transport, named after the scheme of the bus URL */
    val transport: String,
    /** The bus URL with its credentials replaced by a placeholder */
    val url: String,
    /** Epoch milliseconds of the last message received from the bus, absent before the first one */
    val lastReceivedAt: Long? = null,
    val published: Long,
    val applied: Long,
    val ignored: Long,
    /** Envelopes the transport did not send because they exceed its limits, absent on transports without such a limit */
    val dropped: Long? = null
)

data class StatusSnapshot(
    val at: Long = LocalDateTime.now().toUTCMillis(),
    val memory: Int,
    val threads: Int
)

data class HealthResponse(
    val status: String
)

/** The answer of the readiness route: `UP` with no failing check, or `DOWN` with the names of the failing checks. */
data class ReadinessResponse(
    val status: String,
    val failing: List<String>
)

data class FailuresResponse(
    val failures: List<FailureResponse>
)

data class FailureResponse(
    val id: Long,
    /** ISO-8601 instant of the latest occurrence */
    val timestamp: String,
    /** Request URI or subsystem that reported the failure */
    val source: String,
    /** Simple name of the exception class */
    val type: String,
    val message: String?,
    /** Rendered exception chain, truncated to the frames the server keeps */
    val stacktrace: String,
    /** Number of occurrences folded into this record */
    val count: Int
)