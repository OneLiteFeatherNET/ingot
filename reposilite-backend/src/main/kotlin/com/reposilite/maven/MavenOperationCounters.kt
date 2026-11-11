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
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** The result of one deploy request, as the metrics label it. */
enum class DeployOutcome { ACCEPTED, REJECTED }

/** The result of one fetch from an upstream host of a proxy repository, as the metrics label it. */
enum class UpstreamOutcome {
    SUCCESS,
    NOT_FOUND,
    FAILURE,
    TIMEOUT;

    companion object {
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_GATEWAY_TIMEOUT = 504

        /** Classifies the answer of one upstream fetch. A timeout is answered as 504 by the remote client. */
        fun of(result: panda.std.Result<*, ErrorResponse>): UpstreamOutcome = when {
            result.isOk -> SUCCESS
            result.error.status == HTTP_NOT_FOUND -> NOT_FOUND
            result.error.status == HTTP_GATEWAY_TIMEOUT -> TIMEOUT
            else -> FAILURE
        }
    }
}

/** A point-in-time copy of the counters. Every label value is present, so a series never disappears between scrapes. */
data class MavenOperationTotals(
    val deploys: Map<DeployOutcome, Long>,
    val upstream: Map<UpstreamOutcome, Long>,
    /** Refusals of protected groups by repository. Only repositories that refused a request appear. */
    val namespaceRefusals: Map<String, Long> = emptyMap(),
    /** Checksum results of the policies that check copies, warn and fail. Every result of both is present. */
    val checksumResults: Map<ChecksumPolicy, Map<ChecksumResult, Long>> = emptyMap(),
)

/**
 * Counts deploys and upstream fetches since the start of this instance. Shared by the components that observe them;
 * the metrics read a [snapshot] at scrape time.
 */
class MavenOperationCounters {

    private val deploys = EnumMap<DeployOutcome, AtomicLong>(DeployOutcome::class.java)
    private val upstream = EnumMap<UpstreamOutcome, AtomicLong>(UpstreamOutcome::class.java)
    private val namespaceRefusals = ConcurrentHashMap<String, AtomicLong>()

    /** Only the policies that check copies are counted; ignore has no series. */
    private val checksumResults: Map<ChecksumPolicy, Map<ChecksumResult, AtomicLong>> =
        listOf(ChecksumPolicy.WARN, ChecksumPolicy.FAIL).associateWith { ChecksumResult.entries.associateWith { AtomicLong() } }

    init {
        DeployOutcome.entries.forEach { deploys[it] = AtomicLong() }
        UpstreamOutcome.entries.forEach { upstream[it] = AtomicLong() }
    }

    fun recordDeploy(outcome: DeployOutcome) {
        deploys.getValue(outcome).incrementAndGet()
    }

    fun recordUpstream(outcome: UpstreamOutcome) {
        upstream.getValue(outcome).incrementAndGet()
    }

    /** Counts one request that the protected group [repository] refused. */
    fun recordNamespaceRefusal(repository: String) {
        namespaceRefusals.computeIfAbsent(repository) { AtomicLong() }.incrementAndGet()
    }

    /** Counts one checksum result of a copy under [policy]. A policy that does not check copies counts nothing. */
    fun recordChecksumResult(policy: ChecksumPolicy, result: ChecksumResult) {
        checksumResults[policy]?.get(result)?.incrementAndGet()
    }

    fun snapshot(): MavenOperationTotals = MavenOperationTotals(
        deploys = deploys.mapValues { it.value.get() },
        upstream = upstream.mapValues { it.value.get() },
        namespaceRefusals = namespaceRefusals.mapValues { it.value.get() },
        checksumResults = checksumResults.mapValues { (_, byResult) -> byResult.mapValues { it.value.get() } },
    )

}
