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

package com.reposilite.plugin.prometheus

import com.reposilite.audit.AuditFacade
import com.reposilite.cleanup.CleanupService
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.MavenOperationTotals
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.prometheus.metrics.ClusterBusState
import com.reposilite.plugin.prometheus.metrics.OperationalSource
import com.reposilite.plugin.prometheus.metrics.OutboxBacklog
import com.reposilite.status.StatusFacade
import net.onelitefeather.ingot.search.application.SearchFacade
import java.time.Instant

/**
 * Reads the operational values from the facades that own them. The facades are looked up at scrape time, because they are
 * registered after the plugins initialize. A feature whose facade is not registered answers null, and its series are omitted.
 */
internal class FacadeOperationalSource(
    private val facades: () -> Collection<Facade>,
) : OperationalSource {

    private inline fun <reified F : Facade> find(): F? =
        facades().filterIsInstance<F>().firstOrNull()

    override fun maven(): MavenOperationTotals? =
        find<MavenFacade>()?.operationTotals()

    override fun mirrorCacheEntries(): Long? =
        find<MavenFacade>()?.mirrorCacheEntryCount()

    override fun clusterBus(): ClusterBusState? =
        find<StatusFacade>()?.clusterBus()?.let { ClusterBusState(connected = it.state == "connected", dropped = it.dropped) }

    override fun jobLastSuccess(): Map<String, Instant?> = mapOf(
        "cleanup" to find<CleanupService>()?.lastScheduledRunSucceededAt(),
        "audit-purge" to find<AuditFacade>()?.lastPurgeCompletedAt(),
    )

    override fun outbox(): OutboxBacklog? =
        find<SearchFacade>()?.engine?.status()?.let { OutboxBacklog(pending = it.pendingEntries, oldestAgeSeconds = it.lagSeconds) }

}
