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

package net.onelitefeather.ingot.identity.login.storage

import net.onelitefeather.ingot.identity.storage.PrincipalTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Instant

data class LocalCredential(
    val principalId: Int,
    val passwordHash: String,
    val mustChangePassword: Boolean,
    val updatedAt: Instant,
)

/** Reads and writes the login tables. Every method runs in its own transaction. */
class LoginStore(
    private val database: Database,
    private val clock: Clock = Clock.systemUTC(),
) {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(LocalCredentialTable)
        }
    }

    fun findCredential(principalId: Int): LocalCredential? =
        transaction(database) {
            LocalCredentialTable.selectAll()
                .where { LocalCredentialTable.principalId eq principalId }
                .map { it.toCredential() }
                .firstOrNull()
        }

    /** Sets the password hash of a principal, creating its credential when it has none yet. */
    fun saveCredential(principalId: Int, passwordHash: String, mustChangePassword: Boolean) {
        transaction(database) {
            val updatedAt = Instant.now(clock)
            val exists = LocalCredentialTable.selectAll().where { LocalCredentialTable.principalId eq principalId }.any()
            if (exists) {
                LocalCredentialTable.update(where = { LocalCredentialTable.principalId eq principalId }, body = {
                    it[LocalCredentialTable.passwordHash] = passwordHash
                    it[LocalCredentialTable.mustChangePassword] = mustChangePassword
                    it[LocalCredentialTable.updatedAt] = updatedAt
                })
            } else {
                LocalCredentialTable.insert {
                    it[LocalCredentialTable.principalId] = EntityID(principalId, PrincipalTable)
                    it[LocalCredentialTable.passwordHash] = passwordHash
                    it[LocalCredentialTable.mustChangePassword] = mustChangePassword
                    it[LocalCredentialTable.updatedAt] = updatedAt
                }
            }
        }
    }

    private fun ResultRow.toCredential() = LocalCredential(
        principalId = this[LocalCredentialTable.principalId].value,
        passwordHash = this[LocalCredentialTable.passwordHash],
        mustChangePassword = this[LocalCredentialTable.mustChangePassword],
        updatedAt = this[LocalCredentialTable.updatedAt],
    )

}
