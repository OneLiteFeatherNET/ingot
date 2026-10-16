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

import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Clock

/**
 * Creates the search tables once per database, safe when several instances start at the same time (artifact search
 * spec, section 9.1; the guard of identity model 4.3).
 *
 * The marker row `schema-1` in `search_state` is the first statement of the creating transaction. Two starters
 * serialise on it (the unique key on PostgreSQL, MySQL and MariaDB, the write lock on SQLite and H2). The one that
 * inserts the marker creates the tables in the same transaction; the other inserts zero rows and skips the DDL.
 *
 * `search_state` has to exist before its own marker can be written, so it is created first, on its own. When two
 * starters race on that single statement and the database refuses the second one, the table is re-checked and the
 * refusal is only tolerated when the table then exists.
 */
internal object SearchSchema {

    /** The key of the marker row. The suffix is the version of the schema layout. */
    const val MARKER_KEY = "schema-1"

    enum class Outcome {
        /** This starter created the tables. */
        CREATED,

        /** The tables already existed, created by an earlier start or by another instance. */
        ALREADY_CREATED,
    }

    fun ensure(database: Database, clock: Clock = Clock.systemUTC()): Outcome {
        val tables = SearchTables(SearchDialect.of(database.vendor))
        createStateTable(database, tables)

        return transaction(database) {
            val marker = tables.state.insertIgnore {
                it[tables.state.key] = MARKER_KEY
                it[tables.state.value] = "created"
                it[tables.state.updatedAt] = clock.millis()
            }

            if (marker.insertedCount == 0) {
                return@transaction Outcome.ALREADY_CREATED
            }

            SchemaUtils.create(tables.artifact, tables.outbox, tables.rebuildRun)
            Outcome.CREATED
        }
    }

    private fun createStateTable(database: Database, tables: SearchTables) {
        try {
            transaction(database) { SchemaUtils.create(tables.state) }
        } catch (refused: ExposedSQLException) {
            val existing = transaction(database) { SchemaUtils.listTables() }
                .any { it.equals(tables.state.tableName, ignoreCase = true) }
            if (!existing) throw refused
        }
    }

}
