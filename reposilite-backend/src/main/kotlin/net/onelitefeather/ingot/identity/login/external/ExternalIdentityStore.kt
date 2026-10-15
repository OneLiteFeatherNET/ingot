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

package net.onelitefeather.ingot.identity.login.external

import net.onelitefeather.ingot.identity.storage.PrincipalTable
import org.jetbrains.exposed.v1.core.ReferenceOption.CASCADE
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Which principal an external subject belongs to. The provider is `oidc:<id>` or `ldap:<url-host>`, and the subject is the
 * `sub` claim, the numeric GitHub id, or the DN of an LDAP entry. A link is created once and never changed.
 */
object ExternalIdentityTable : IntIdTable("user_identity") {
    val provider = varchar("provider", PROVIDER_LENGTH)
    val subject = varchar("subject", SUBJECT_LENGTH)
    val principalId = reference("principal_id", PrincipalTable, onDelete = CASCADE).index()

    init {
        uniqueIndex("uq_user_identity_subject", provider, subject)
    }

    const val PROVIDER_LENGTH = 64
    const val SUBJECT_LENGTH = 512
}

/** Reads and writes the `user_identity` links. */
internal class ExternalIdentityStore(private val database: Database) {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(ExternalIdentityTable)
        }
    }

    fun principalIdOf(provider: String, subject: String): Int? =
        transaction(database) {
            ExternalIdentityTable.selectAll()
                .where { (ExternalIdentityTable.provider eq provider) and (ExternalIdentityTable.subject eq subject) }
                .map { it[ExternalIdentityTable.principalId].value }
                .firstOrNull()
        }

    /** Links the subject to the principal. Returns `false` when another request linked it first. */
    fun link(provider: String, subject: String, principalId: Int): Boolean =
        try {
            transaction(database) {
                ExternalIdentityTable.insert {
                    it[ExternalIdentityTable.provider] = provider
                    it[ExternalIdentityTable.subject] = subject
                    it[ExternalIdentityTable.principalId] = EntityID(principalId, PrincipalTable)
                }
            }
            true
        } catch (_: ExposedSQLException) {
            false
        }

}

