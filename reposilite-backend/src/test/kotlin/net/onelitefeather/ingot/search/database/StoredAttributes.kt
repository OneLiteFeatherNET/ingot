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

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * The attributes stored in `search_attribute` for one row, read with plain SQL so that the column names of the design (D6)
 * are checked as well. Keys come in key order, the values of a key in the order they were written. The arguments are test
 * constants without quotes.
 */
internal fun storedAttributes(database: Database, repository: String, path: String): Map<String, List<String>> =
    transaction(database) {
        val found = linkedMapOf<String, MutableList<String>>()
        exec(
            "SELECT attr_key, attr_value FROM search_attribute WHERE repository = '$repository' AND path = '$path' " +
                "ORDER BY attr_key, value_index",
        ) { result ->
            while (result.next()) found.getOrPut(result.getString(1)) { mutableListOf() } += result.getString(2)
        }
        found
    }
