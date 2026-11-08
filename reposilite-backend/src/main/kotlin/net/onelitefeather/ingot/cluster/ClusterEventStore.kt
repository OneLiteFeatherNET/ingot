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

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * The events of the database transport. The auto-increment id is the order of delivery; the time is only used to remove old rows.
 */
internal object ClusterEventTable : Table("cluster_event") {

    val id = long("id").autoIncrement()
    val cluster = varchar("cluster", CLUSTER_LENGTH)
    val origin = varchar("origin", ORIGIN_LENGTH)
    val createdAt = long("created_at")
    val payload = text("payload")

    override val primaryKey = PrimaryKey(id)

    init {
        index(customIndexName = "ix_cluster_event_created_at", isUnique = false, createdAt)
    }

    const val CLUSTER_LENGTH = 64
    const val ORIGIN_LENGTH = 255

    /** The envelope limit of this transport. A larger envelope is not stored. */
    const val MAX_PAYLOAD_BYTES = 16 * 1024
}

/** One event to store. [createdAt] is epoch milliseconds from the publisher's clock. */
internal data class ClusterEventRow(val cluster: String, val origin: String, val createdAt: Long, val payload: String)

/** One stored event as read back by a poll. */
internal data class StoredClusterEvent(val id: Long, val origin: String, val payload: String)

/** The storage the database transport needs. The production implementation is [SqlClusterEventStore]. */
internal interface ClusterEventStore {

    fun insert(row: ClusterEventRow)

    /** The largest stored id, or zero when no event is stored. */
    fun maxId(): Long

    /** Every stored event with an id above [lowerExclusive], in ascending id order. */
    fun since(lowerExclusive: Long): List<StoredClusterEvent>

    /** Removes every event created before [cutoffMillis]. */
    fun deleteCreatedBefore(cutoffMillis: Long)
}

/**
 * Stores the events in the `cluster_event` table of the instance database. The table is created when the store is opened,
 * the same way the scheduled job claims are created.
 */
internal class SqlClusterEventStore(private val database: Database) : ClusterEventStore {

    init {
        transaction(database) {
            SchemaUtils.create(ClusterEventTable)
        }
    }

    override fun insert(row: ClusterEventRow) {
        transaction(database) {
            ClusterEventTable.insert {
                it[cluster] = row.cluster
                it[origin] = row.origin
                it[createdAt] = row.createdAt
                it[payload] = row.payload
            }
        }
    }

    override fun maxId(): Long {
        val largestId = ClusterEventTable.id.max()
        val largest: Long? = transaction(database) {
            ClusterEventTable.select(largestId).map { it[largestId] }.firstOrNull()
        }
        return largest ?: 0L
    }

    override fun since(lowerExclusive: Long): List<StoredClusterEvent> =
        transaction(database) {
            ClusterEventTable.selectAll()
                .where { ClusterEventTable.id greater lowerExclusive }
                .orderBy(ClusterEventTable.id)
                .map { StoredClusterEvent(it[ClusterEventTable.id], it[ClusterEventTable.origin], it[ClusterEventTable.payload]) }
        }

    override fun deleteCreatedBefore(cutoffMillis: Long) {
        transaction(database) {
            ClusterEventTable.deleteWhere { createdAt less cutoffMillis }
        }
    }
}
