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

import net.onelitefeather.ingot.search.SearchUnavailableException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLTimeoutException

/** The statement timeout of one read of the index, in seconds. */
internal const val QUERY_TIMEOUT_SECONDS = 10

/**
 * Runs [read] in one transaction with the statement timeout, and turns a timeout into [SearchUnavailableException]. Every read
 * of the index goes through it, whichever engine or backend asks.
 */
internal fun <T> searchReadOnly(database: Database, read: () -> T): T = try {
    transaction(database) {
        queryTimeout = QUERY_TIMEOUT_SECONDS
        read()
    }
} catch (failure: Exception) {
    if (generateSequence(failure as Throwable) { it.cause }.any { it is SQLTimeoutException }) {
        throw SearchUnavailableException("The search took longer than $QUERY_TIMEOUT_SECONDS seconds, narrow the filters")
    }
    throw failure
}
