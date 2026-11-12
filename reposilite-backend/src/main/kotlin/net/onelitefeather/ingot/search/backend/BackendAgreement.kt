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

package net.onelitefeather.ingot.search.backend

import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.runs.RunKind
import net.onelitefeather.ingot.search.runs.RunStore
import net.onelitefeather.ingot.search.runs.RunTrigger
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** What a replica does with the outbox after it compared its backend id with the stored one. */
internal enum class AgreementDecision {
    /** No id is stored: this replica wrote its id and drains from now on. */
    CLAIM,

    /** The stored id is this replica's id: drain. */
    DRAIN,

    /** Another id is stored and its replicas are still seen: drain nothing, report the mismatch. */
    MISMATCH,

    /** Another id is stored and none of its replicas was seen recently: the id changes and a rebuild starts. */
    SWITCH,

    /**
     * Another id is stored and none of its replicas was seen recently, but a rebuild or verify run holds the lock, so the
     * switch could not start its run. Nothing changed: the next agreement tick asks again.
     */
    SWITCH_DEFERRED,
}

/** The cluster rule of the backend id (search backend registration, D5), as a decision on plain values. */
internal object BackendAgreementRule {

    /** A stored backend whose replicas were all silent for this long may be replaced. Longer than the heartbeat interval. */
    val SWITCH_WINDOW: Duration = Duration.ofMinutes(3)

    /**
     * [lastSeenOfStored] is the newest heartbeat of any replica that runs the stored backend, or null when none exists. A
     * heartbeat exactly [SWITCH_WINDOW] old no longer blocks the switch.
     */
    fun decide(stored: String?, own: String, lastSeenOfStored: Instant?, now: Instant): AgreementDecision = when {
        stored == null -> AgreementDecision.CLAIM
        stored == own -> AgreementDecision.DRAIN
        lastSeenOfStored != null && Duration.between(lastSeenOfStored, now) < SWITCH_WINDOW -> AgreementDecision.MISMATCH
        else -> AgreementDecision.SWITCH
    }

}

/**
 * The cluster agreement of one replica (search backend registration, D5). [heartbeat] writes this replica's line in
 * `search_state`; [agree] reads the stored id and the heartbeats and acts on the rule. A switch stores the new id, clears
 * the last full rebuild and queues a run with the trigger [RunTrigger.BACKEND].
 */
internal class ClusterBackendAgreement(
    private val state: SearchStateStore,
    private val runs: RunStore,
    private val clock: Clock,
    private val instanceId: String,
    private val backendId: String,
) {

    /** Writes the heartbeat of this replica with its backend id and the current time. */
    fun heartbeat() {
        state.put(SearchStateKey.BACKEND_HEARTBEAT_PREFIX + instanceId, "$backendId|${clock.millis()}")
    }

    /** Decides what this replica does, and makes the change the decision asks for. */
    fun agree(): AgreementDecision {
        val stored = state.get(SearchStateKey.BACKEND_ID)
        val decision = BackendAgreementRule.decide(stored, backendId, stored?.let { lastSeenOf(it) }, clock.instant())
        return when (decision) {
            AgreementDecision.CLAIM -> if (state.putIfAbsent(SearchStateKey.BACKEND_ID, backendId)) decision else agree()
            AgreementDecision.SWITCH -> switch()
            AgreementDecision.DRAIN, AgreementDecision.MISMATCH, AgreementDecision.SWITCH_DEFERRED -> decision
        }
    }

    /**
     * Stores this replica's id, clears the last full rebuild and starts the run, all in one transaction. A live rebuild or verify
     * lock makes the run refuse to start, and then nothing is stored, so the switch is decided again on the next tick.
     */
    private fun switch(): AgreementDecision = runs.atomically {
        if (runs.start(RunKind.REBUILD, RunTrigger.BACKEND, null, instanceId) == null) return@atomically AgreementDecision.SWITCH_DEFERRED
        state.put(SearchStateKey.BACKEND_ID, backendId)
        state.remove(SearchStateKey.LAST_FULL_REBUILD)
        AgreementDecision.SWITCH
    }

    private fun lastSeenOf(stored: String): Instant? = state.entries(SearchStateKey.BACKEND_HEARTBEAT_PREFIX)
        .values
        .mapNotNull { value -> value.split('|', limit = 2).takeIf { it.size == 2 && it[0] == stored }?.get(1)?.toLongOrNull() }
        .maxOrNull()
        ?.let { Instant.ofEpochMilli(it) }

}
