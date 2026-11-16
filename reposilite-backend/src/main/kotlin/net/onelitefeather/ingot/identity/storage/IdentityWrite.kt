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
package net.onelitefeather.ingot.identity.storage

import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Runs an identity write in its own transaction and advances the policy revision in that same transaction. A write that
 * fails is rolled back together with the revision, so the revision moves exactly when identity changed. Usage records
 * (last seen times, spent recovery codes) do not go through here: they change no right and must not invalidate the caches of
 * every replica.
 */
internal fun <T> identityWrite(database: Database, block: JdbcTransaction.() -> T): T =
    transaction(database) {
        val result = block()
        PolicyRevisionTable.update(where = { PolicyRevisionTable.id eq POLICY_REVISION_ROW }, body = {
            it[PolicyRevisionTable.revision] = PolicyRevisionTable.revision + 1L
        })
        result
    }
