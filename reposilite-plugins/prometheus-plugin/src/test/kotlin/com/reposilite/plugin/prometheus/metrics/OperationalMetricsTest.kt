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

package com.reposilite.plugin.prometheus.metrics

import com.reposilite.maven.ChecksumPolicy
import com.reposilite.maven.ChecksumResult
import com.reposilite.maven.DeployOutcome
import com.reposilite.maven.MavenOperationTotals
import com.reposilite.maven.UpstreamOutcome
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.CounterSnapshot
import io.prometheus.metrics.model.snapshots.GaugeSnapshot
import io.prometheus.metrics.model.snapshots.MetricSnapshot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** A fixed source of operational values. Each test sets the values it needs; nothing else is read. */
private class FakeOperationalSource(
    var maven: MavenOperationTotals? = null,
    var mirrorCacheEntries: Long? = null,
    var clusterBus: ClusterBusState? = null,
    var jobs: Map<String, Instant?> = emptyMap(),
    var outbox: OutboxBacklog? = null,
) : OperationalSource {
    override fun maven(): MavenOperationTotals? = maven
    override fun mirrorCacheEntries(): Long? = mirrorCacheEntries
    override fun clusterBus(): ClusterBusState? = clusterBus
    override fun jobLastSuccess(): Map<String, Instant?> = jobs
    override fun outbox(): OutboxBacklog? = outbox
}

internal class OperationalMetricsTest {

    private val registry = PrometheusRegistry()
    private val source = FakeOperationalSource()

    private fun register() = OperationalMetrics(registry, source)

    private fun snapshot(name: String): MetricSnapshot? =
        registry.scrape().singleOrNull { it.metadata.prometheusName == name }

    private fun samples(name: String): List<Pair<Map<String, String>, Double>> =
        when (val metric = snapshot(name)) {
            null -> emptyList()
            is CounterSnapshot -> metric.dataPoints.map { point -> point.labels.toMap() to point.value }
            is GaugeSnapshot -> metric.dataPoints.map { point -> point.labels.toMap() to point.value }
            else -> error("Unexpected snapshot type for $name")
        }

    private fun io.prometheus.metrics.model.snapshots.Labels.toMap(): Map<String, String> =
        (0 until size()).associate { getName(it) to getValue(it) }

    private fun maven(
        deployAccepted: Long = 0,
        deployRejected: Long = 0,
        upstream: Map<UpstreamOutcome, Long> = emptyMap(),
        namespaceRefusals: Map<String, Long> = emptyMap(),
    ) =
        MavenOperationTotals(
            deploys = mapOf(DeployOutcome.ACCEPTED to deployAccepted, DeployOutcome.REJECTED to deployRejected),
            upstream = UpstreamOutcome.entries.associateWith { upstream[it] ?: 0L },
            namespaceRefusals = namespaceRefusals,
        )

    @Test
    fun `should register exactly the operational series under their ingot names`() {
        // given: every source is present
        source.maven = maven()
        source.mirrorCacheEntries = 0
        source.clusterBus = ClusterBusState(connected = true, dropped = 0)
        source.outbox = OutboxBacklog(pending = 0, oldestAgeSeconds = 0)

        // when: the series are registered and the registry is scraped
        register()
        val names = registry.scrape().map { it.metadata.prometheusName }.filter { it.startsWith("ingot_") }

        // then: the names are exactly the ones the operator documentation lists
        assertThat(names).containsExactlyInAnyOrder(
            "ingot_deploy_results",
            "ingot_upstream_requests",
            "ingot_namespace_refusals",
            "ingot_mirror_checksum_results",
            "ingot_mirror_cache_entries",
            "ingot_cluster_connected",
            "ingot_cluster_dropped_events",
            "ingot_batch_job_last_success_timestamp_seconds",
            "ingot_search_outbox_pending",
            "ingot_search_outbox_oldest_age_seconds",
        )
    }

    @Test
    fun `should expose all six checksum series at zero before the first fetch`() {
        // given: no fetch has checked a copy yet, so every checksum result of warn and fail is zero
        source.maven = maven().copy(
            checksumResults = listOf(ChecksumPolicy.WARN, ChecksumPolicy.FAIL).associateWith { ChecksumResult.entries.associateWith { 0L } },
        )

        // when: the series are scraped
        register()

        // then: warn and fail each carry verified, mismatch and missing, so the series exist from the first scrape
        assertThat(samples("ingot_mirror_checksum_results"))
            .describedAs("checksum series before the first fetch")
            .hasSize(6)
            .allSatisfy { (_, value) -> assertThat(value).isZero() }
    }

    @Test
    fun `should expose namespace refusals per protected group under the repository label`() {
        // given: two refusals of one group and one of another
        source.maven = maven(namespaceRefusals = mapOf("guarded" to 2L, "partners" to 1L))

        // when: the series are scraped
        register()

        // then: each group carries its own count under the repository label
        assertThat(samples("ingot_namespace_refusals"))
            .describedAs("refusals must be counted per protected group")
            .containsExactlyInAnyOrder(
                mapOf("repository" to "guarded") to 2.0,
                mapOf("repository" to "partners") to 1.0,
            )
    }

    @Test
    fun `should expose accepted and rejected deploys under the result label`() {
        // given: two accepted and one rejected deploy
        source.maven = maven(deployAccepted = 2, deployRejected = 1)

        // when: the series are scraped
        register()

        // then: the result label carries exactly accepted and rejected
        assertThat(samples("ingot_deploy_results"))
            .describedAs("deploys must be counted per result")
            .containsExactlyInAnyOrder(
                mapOf("result" to "accepted") to 2.0,
                mapOf("result" to "rejected") to 1.0,
            )
    }

    @Test
    fun `should keep the number of deploy series when deploys reach ten repositories`() {
        // given: deploys that only ever touch the two fixed results, however many repositories received them
        source.maven = maven(deployAccepted = 10, deployRejected = 0)

        // when: the series are scraped
        register()

        // then: the series count is fixed at two, one per result, and no series carries a repository name
        assertThat(samples("ingot_deploy_results"))
            .describedAs("the series count must not depend on the number of repositories")
            .hasSize(2)
            .allSatisfy { (labels, _) -> assertThat(labels.keys).containsExactly("result") }
    }

    @Test
    fun `should expose every upstream outcome under the outcome label`() {
        // given: one timeout and one miss, the other outcomes unseen
        source.maven = maven(upstream = mapOf(UpstreamOutcome.TIMEOUT to 1, UpstreamOutcome.NOT_FOUND to 1))

        // when: the series are scraped
        register()

        // then: all four outcomes are present, zero where nothing happened
        assertThat(samples("ingot_upstream_requests"))
            .describedAs("each upstream outcome must be a series, also when it is zero")
            .containsExactlyInAnyOrder(
                mapOf("outcome" to "success") to 0.0,
                mapOf("outcome" to "not_found") to 1.0,
                mapOf("outcome" to "failure") to 0.0,
                mapOf("outcome" to "timeout") to 1.0,
            )
    }

    @Test
    fun `should expose the mirror cache entries as a gauge`() {
        // given: four mirror cache rows
        source.mirrorCacheEntries = 4

        // when: the series are scraped
        register()

        // then: the gauge reads four
        assertThat(samples("ingot_mirror_cache_entries"))
            .describedAs("the mirror cache gauge must read the row count")
            .containsExactly(emptyMap<String, String>() to 4.0)
    }

    @Test
    fun `should expose the cluster connection and dropped events when a bus is attached`() {
        // given: a connected bus that dropped five envelopes
        source.clusterBus = ClusterBusState(connected = true, dropped = 5)

        // when: the series are scraped
        register()

        // then: the connection reads one and the dropped counter reads five
        assertThat(samples("ingot_cluster_connected")).containsExactly(emptyMap<String, String>() to 1.0)
        assertThat(samples("ingot_cluster_dropped_events")).containsExactly(emptyMap<String, String>() to 5.0)
    }

    @Test
    fun `should report a disconnected bus as zero`() {
        // given: a bus that is attached but not connected, and a transport without a dropped count
        source.clusterBus = ClusterBusState(connected = false, dropped = null)

        // when: the series are scraped
        register()

        // then: the connection reads zero and no dropped series is present
        assertThat(samples("ingot_cluster_connected")).containsExactly(emptyMap<String, String>() to 0.0)
        assertThat(samples("ingot_cluster_dropped_events")).isEmpty()
    }

    @Test
    fun `should omit every cluster series when no bus is configured`() {
        // given: no cluster bus attached
        source.clusterBus = null

        // when: the series are scraped
        register()

        // then: no cluster sample exists
        assertThat(samples("ingot_cluster_connected")).describedAs("no bus, no connection series").isEmpty()
        assertThat(samples("ingot_cluster_dropped_events")).describedAs("no bus, no dropped series").isEmpty()
    }

    @Test
    fun `should expose the last success of each batch job by job label`() {
        // given: the cleanup job succeeded at a known instant, the audit purge has not succeeded yet
        source.jobs = mapOf("cleanup" to Instant.ofEpochSecond(1_700_000_000L), "audit-purge" to null)

        // when: the series are scraped
        register()

        // then: only the job with a success carries a timestamp, labelled by job
        assertThat(samples("ingot_batch_job_last_success_timestamp_seconds"))
            .describedAs("a job without a success has no sample")
            .containsExactly(mapOf("job" to "cleanup") to 1_700_000_000.0)
    }

    @Test
    fun `should expose the outbox backlog and its oldest age`() {
        // given: three pending outbox entries and the oldest was written 40 seconds ago
        source.outbox = OutboxBacklog(pending = 3, oldestAgeSeconds = 40)

        // when: the series are scraped
        register()

        // then: the pending count reads three and the age reads forty
        assertThat(samples("ingot_search_outbox_pending")).containsExactly(emptyMap<String, String>() to 3.0)
        assertThat(samples("ingot_search_outbox_oldest_age_seconds")).containsExactly(emptyMap<String, String>() to 40.0)
    }

    @Test
    fun `should omit the outbox series when search is not configured`() {
        // given: no search facade
        source.outbox = null

        // when: the series are scraped
        register()

        // then: no outbox sample exists
        assertThat(samples("ingot_search_outbox_pending")).isEmpty()
        assertThat(samples("ingot_search_outbox_oldest_age_seconds")).isEmpty()
    }

}
