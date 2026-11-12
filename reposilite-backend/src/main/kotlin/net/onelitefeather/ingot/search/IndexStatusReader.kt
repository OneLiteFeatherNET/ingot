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

package net.onelitefeather.ingot.search

import com.fasterxml.jackson.databind.ObjectMapper
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.database.SearchDialect
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchTables
import net.onelitefeather.ingot.search.database.indexStateOf
import net.onelitefeather.ingot.search.database.searchReadOnly
import net.onelitefeather.ingot.search.outbox.OutboxPolicy
import net.onelitefeather.ingot.search.runs.RunKind
import net.onelitefeather.ingot.search.runs.RunStatus
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Clock
import java.time.Instant

/**
 * The part of the index status (artifact search spec, 4.8) that the core reads from its own tables: the outbox, the rebuild
 * runs, the state rows and the verify report. The tables live in the instance's database whichever backend holds the rows, so
 * every backend shares this part. [coreFacts] reads it in the transaction of the caller; [compose] adds what the backend holds.
 */
internal class IndexStatusReader(
    private val database: Database,
    private val clock: Clock,
) {

    private val tables = SearchTables(SearchDialect.of(database.vendor))
    private val outbox = tables.outbox
    private val runs = tables.rebuildRun
    private val state = tables.state

    /** The status of [backend] for an index whose rows exist or not: the core counts in their own transaction, then the tokens. */
    fun statusOf(backend: ArtifactSearchBackend, rowsExist: Boolean): IndexStatus {
        val core = searchReadOnly(database) { coreFacts() }
        return compose(core, rowsExist = rowsExist, tokensComplete = backend.tokensComplete())
    }

    /** The core counts and history, read in the current transaction. */
    fun coreFacts(): CoreIndexFacts {
        val lastFull = stateValue(SearchStateKey.LAST_FULL_REBUILD)?.toLongOrNull()
        val dropped = stateValue(SearchStateKey.DROPPED_ENQUEUES)?.toLongOrNull() ?: 0L
        val drift = stateValue(SearchStateKey.VERIFY_LAST_REPORT)?.let(::driftOf)
        val rebuildRunning = runs.select(runs.id)
            .where { (runs.kind eq RunKind.REBUILD.name) and (runs.status eq RunStatus.RUNNING.name) }
            .limit(1)
            .firstOrNull() != null
        val lastRebuildFailed = runs.select(runs.status)
            .where { runs.kind eq RunKind.REBUILD.name }
            .orderBy(runs.id to SortOrder.DESC)
            .limit(1)
            .firstOrNull()?.get(runs.status) == RunStatus.FAILED.name
        val oldestPending = outbox.select(outbox.createdAt)
            .where { outbox.attempts less OutboxPolicy.MAX_ATTEMPTS }
            .orderBy(outbox.id to SortOrder.ASC)
            .limit(1)
            .firstOrNull()
        val lagSeconds = oldestPending?.let { ((clock.millis() - it[outbox.createdAt]) / MILLIS_PER_SECOND).coerceAtLeast(0L) } ?: 0L
        return CoreIndexFacts(
            lastFullRebuild = lastFull?.let(Instant::ofEpochMilli),
            droppedEnqueues = dropped,
            drift = drift,
            rebuildRunning = rebuildRunning,
            lastRebuildFailed = lastRebuildFailed,
            lagSeconds = lagSeconds,
            deadLetters = outbox.selectAll().where { outbox.attempts greaterEq OutboxPolicy.MAX_ATTEMPTS }.count(),
            pendingEntries = outbox.selectAll().where { outbox.attempts less OutboxPolicy.MAX_ATTEMPTS }.count(),
        )
    }

    /** The index status from the core counts and what the backend holds: whether rows exist and whether every row has its tokens. */
    fun compose(core: CoreIndexFacts, rowsExist: Boolean, tokensComplete: Boolean): IndexStatus = IndexStatus(
        state = indexStateOf(
            lastFullRebuild = core.lastFullRebuild,
            rowsExist = rowsExist,
            rebuildRunning = core.rebuildRunning,
            lastRebuildFailed = core.lastRebuildFailed,
            lagSeconds = core.lagSeconds,
            deadLetters = core.deadLetters,
            droppedEnqueues = core.droppedEnqueues,
            drift = core.drift,
            tokensComplete = tokensComplete,
        ),
        lagSeconds = core.lagSeconds,
        lastFullRebuild = core.lastFullRebuild,
        deadLetters = core.deadLetters,
        droppedEnqueues = core.droppedEnqueues,
        pendingEntries = core.pendingEntries,
        tokensComplete = tokensComplete,
    )

    private fun stateValue(key: String): String? =
        state.select(state.value).where { state.key eq key }.firstOrNull()?.get(state.value)

    private fun driftOf(report: String): Double? =
        runCatching { MAPPER.readTree(report).get("drift")?.asDouble() }.getOrNull()

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        val MAPPER = ObjectMapper()
    }

}

/** What the core reads from its own tables for the index status (4.8). */
internal data class CoreIndexFacts(
    val lastFullRebuild: Instant?,
    val droppedEnqueues: Long,
    val drift: Double?,
    val rebuildRunning: Boolean,
    val lastRebuildFailed: Boolean,
    val lagSeconds: Long,
    val deadLetters: Long,
    val pendingEntries: Long,
)
