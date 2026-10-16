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

package net.onelitefeather.ingot.search.database

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock

/** The keys of `search_state` that the index writes (artifact search spec, 2.7). */
internal object SearchStateKey {
    /** Epoch milliseconds of the last heartbeat of any replica's worker. */
    const val HEARTBEAT = "heartbeat"

    /** Enqueue failures since the last completed rebuild. */
    const val DROPPED_ENQUEUES = "dropped_enqueues"

    /** Epoch milliseconds of the last completed full rebuild. Absent until one completed. */
    const val LAST_FULL_REBUILD = "last_full_rebuild"

    /** The single-run lock of rebuilds: the live run id, with a lease. */
    const val REBUILD_ACTIVE = "rebuild.active"

    /** The single-run lock of verify runs. */
    const val VERIFY_ACTIVE = "verify.active"
}

/**
 * Reads and writes `search_state`: heartbeat, schema marker, counters. Every method runs in its own transaction, and
 * a write of an existing key is an update, so the table never holds two rows for one key.
 */
internal class SearchStateStore(
    private val database: Database,
    private val clock: Clock,
) {

    private val state = SearchTables(SearchDialect.of(database.vendor)).state

    fun put(key: String, value: String) {
        transaction(database) { putInCurrentTransaction(key, value) }
    }

    fun get(key: String): String? = transaction(database) {
        state.selectAll().where { state.key eq key }.map { it[state.value] }.firstOrNull()
    }

    /** Adds one to the counter [key] and returns the new value. A counter that is not a number starts at zero. */
    fun increment(key: String): Long = transaction(database) {
        val current = state.selectAll().where { state.key eq key }.map { it[state.value] }.firstOrNull()
        val next = (current?.toLongOrNull() ?: 0L) + 1
        putInCurrentTransaction(key, next.toString())
        next
    }

    private fun putInCurrentTransaction(key: String, value: String) {
        val now = clock.millis()
        val updated = state.update(where = { state.key eq key }, body = {
            it[state.value] = value
            it[state.updatedAt] = now
        })
        if (updated == 0) {
            val inserted = state.insertIgnore {
                it[state.key] = key
                it[state.value] = value
                it[state.updatedAt] = now
            }.insertedCount
            if (inserted == 0) {
                // Another instance inserted the key between the update and the insert; its value is replaced by ours.
                state.update(where = { state.key eq key }, body = {
                    it[state.value] = value
                    it[state.updatedAt] = now
                })
            }
        }
    }

}
