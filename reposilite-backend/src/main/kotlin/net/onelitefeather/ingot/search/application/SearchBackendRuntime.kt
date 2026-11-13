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

package net.onelitefeather.ingot.search.application

import net.onelitefeather.ingot.search.backend.AgreementDecision
import net.onelitefeather.ingot.search.backend.ClusterBackendAgreement
import net.onelitefeather.ingot.search.backend.SearchBackendHolder
import net.onelitefeather.ingot.search.backend.SearchBackendState
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The state of the backend of this replica that the poller, the schedulers and the routes read (search backend registration,
 * D3 and D5). [tick] runs on the IO pool every few seconds: it moves the holder, and while the backend is available it writes
 * the heartbeat of this replica and decides the cluster agreement. A replica drains only while the agreement lets it.
 */
internal class SearchBackendRuntime(
    private val holder: SearchBackendHolder,
    private val agreement: ClusterBackendAgreement?,
    private val clock: Clock,
    private val backendId: String,
    private val redact: (String) -> String,
    private val accessMismatches: () -> Long,
) {

    @Volatile
    private var agreed = false

    private var nextHeartbeatAt: Instant = clock.instant()

    /** Moves the holder, then writes the heartbeat and decides the agreement while the backend is available. */
    fun tick() {
        holder.tick()
        val cluster = agreement ?: return
        if (holder.state != SearchBackendState.AVAILABLE) return
        val now = clock.instant()
        if (!now.isBefore(nextHeartbeatAt)) {
            cluster.heartbeat()
            nextHeartbeatAt = now.plus(HEARTBEAT_INTERVAL)
        }
        agreed = when (cluster.agree()) {
            AgreementDecision.CLAIM, AgreementDecision.DRAIN, AgreementDecision.SWITCH -> true
            AgreementDecision.MISMATCH, AgreementDecision.SWITCH_DEFERRED -> false
        }
    }

    /** Whether the poller may drain the outbox now. */
    fun drainAllowed(): Boolean = agreed && holder.drainAllowed()

    /** Whether the backend answers searches and administration right now. */
    fun available(): Boolean = holder.state == SearchBackendState.AVAILABLE && agreed

    fun drainFailed() = holder.drainFailed()

    /** The state for the routes and the Maintenance block. An available backend that the cluster does not agree on is a mismatch. */
    fun status(): BackendStatus {
        val mismatch = holder.state == SearchBackendState.AVAILABLE && !agreed
        val state = if (mismatch) SearchBackendState.CLUSTER_MISMATCH else holder.state
        val detail = when {
            mismatch -> "this replica runs '$backendId', but the cluster stores another backend"
            state == SearchBackendState.AVAILABLE -> ""
            else -> holder.detail
        }
        val health = when (holder.state) {
            SearchBackendState.AVAILABLE -> "UP"
            SearchBackendState.DOWN -> "DOWN"
            else -> "UNKNOWN"
        }
        return BackendStatus(
            id = backendId,
            state = state,
            detail = detail,
            health = health,
            healthDetail = if (holder.state == SearchBackendState.DOWN) holder.detail else "",
            accessMismatches = accessMismatches(),
            properties = properties(),
        )
    }

    /** The properties the backend reports, with every key and value redacted. Empty while no backend is open or it fails to answer. */
    private fun properties(): Map<String, String> {
        val reported = holder.backendOrNull()?.let { runCatching { it.properties() }.getOrNull() }.orEmpty()
        return reported.entries.associate { (key, value) -> redact(key) to redact(value) }
    }

    private companion object {
        val HEARTBEAT_INTERVAL: Duration = Duration.ofMinutes(1)
    }

}
