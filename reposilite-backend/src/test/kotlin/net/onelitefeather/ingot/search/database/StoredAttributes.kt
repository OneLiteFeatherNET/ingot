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

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * The attributes stored in `search_attribute` for one row. Keys come in key order, the values of a key in the order they were
 * written.
 */
internal fun storedAttributes(database: Database, repository: String, path: String): Map<String, List<String>> =
    transaction(database) {
        val attributes = SearchTables(SearchDialect.of(database.vendor)).attribute
        attributes.selectAll()
            .where { (attributes.repository eq repository) and (attributes.path eq path) }
            .orderBy(attributes.key to SortOrder.ASC, attributes.valueIndex to SortOrder.ASC)
            .groupBy({ it[attributes.key] }, { it[attributes.value] })
    }
