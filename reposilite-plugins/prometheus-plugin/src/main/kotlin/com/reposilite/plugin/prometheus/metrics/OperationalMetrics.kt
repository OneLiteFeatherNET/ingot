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

import com.reposilite.maven.MavenOperationTotals
import io.prometheus.metrics.core.metrics.CounterWithCallback
import io.prometheus.metrics.core.metrics.GaugeWithCallback
import io.prometheus.metrics.model.registry.PrometheusRegistry
import java.time.Instant

/** The state of the cluster bus as the metrics show it. */
internal data class ClusterBusState(
    val connected: Boolean,
    /** Envelopes the transport dropped because they exceeded its limits, null on transports without such a limit. */
    val dropped: Long?,
)

/** The search outbox as the metrics show it. */
internal data class OutboxBacklog(
    val pending: Long,
    val oldestAgeSeconds: Long,
)

/**
 * What the operational series read at scrape time. A null answer means the owning feature is not configured, and its
 * series are then left out. The plugin answers from the facades; tests answer with fixed values.
 */
internal interface OperationalSource {
    fun maven(): MavenOperationTotals?
    fun mirrorCacheEntries(): Long?
    fun clusterBus(): ClusterBusState?
    /** The job label to the completion time of its last successful run on this instance, null when there was none. */
    fun jobLastSuccess(): Map<String, Instant?>
    fun outbox(): OutboxBacklog?
}

/**
 * The operational series of a replica. Each value is a callback that reads [source] when the registry is scraped, so no thread
 * is started and no value can go stale. Every label value comes from a fixed set: the deploy result, the upstream outcome and
 * the job name. No series carries a repository, path, user or token name.
 */
internal class OperationalMetrics(
    registry: PrometheusRegistry,
    source: OperationalSource,
) {

    init {
        CounterWithCallback.builder()
            .name("ingot_deploy_results_total")
            .help("Deploy requests by result, accepted or rejected by the repository rules")
            .labelNames("result")
            .callback { callback ->
                source.maven()?.deploys?.forEach { (outcome, count) -> callback.call(count.toDouble(), outcome.name.lowercase()) }
            }
            .register(registry)

        CounterWithCallback.builder()
            .name("ingot_upstream_requests_total")
            .help("Fetches from upstream hosts of proxy repositories by outcome")
            .labelNames("outcome")
            .callback { callback ->
                source.maven()?.upstream?.forEach { (outcome, count) -> callback.call(count.toDouble(), outcome.name.lowercase()) }
            }
            .register(registry)

        GaugeWithCallback.builder()
            .name("ingot_mirror_cache_entries")
            .help("Rows of the mirror cache across all proxy repositories")
            .callback { callback -> source.mirrorCacheEntries()?.let { callback.call(it.toDouble()) } }
            .register(registry)

        GaugeWithCallback.builder()
            .name("ingot_cluster_connected")
            .help("1 when the cluster bus is connected, 0 when it is not, absent without a bus")
            .callback { callback -> source.clusterBus()?.let { callback.call(if (it.connected) 1.0 else 0.0) } }
            .register(registry)

        CounterWithCallback.builder()
            .name("ingot_cluster_dropped_events_total")
            .help("Cluster bus envelopes dropped because they exceeded the transport limits")
            .callback { callback -> source.clusterBus()?.dropped?.let { callback.call(it.toDouble()) } }
            .register(registry)

        GaugeWithCallback.builder()
            .name("ingot_batch_job_last_success_timestamp_seconds")
            .help("Unix time of the last successful run of a batch job on this instance")
            .labelNames("job")
            .callback { callback ->
                source.jobLastSuccess().forEach { (job, completedAt) ->
                    completedAt?.let { callback.call(it.epochSecond.toDouble(), job) }
                }
            }
            .register(registry)

        GaugeWithCallback.builder()
            .name("ingot_search_outbox_pending")
            .help("Search outbox entries that may still be claimed")
            .callback { callback -> source.outbox()?.let { callback.call(it.pending.toDouble()) } }
            .register(registry)

        GaugeWithCallback.builder()
            .name("ingot_search_outbox_oldest_age_seconds")
            .help("Age in seconds of the oldest pending search outbox entry, 0 when there is none")
            .callback { callback -> source.outbox()?.let { callback.call(it.oldestAgeSeconds.toDouble()) } }
            .register(registry)
    }

}
