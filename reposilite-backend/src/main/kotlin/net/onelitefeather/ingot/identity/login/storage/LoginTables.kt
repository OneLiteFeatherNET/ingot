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
import org.jetbrains.exposed.v1.core.ReferenceOption.CASCADE
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

/** One local password per principal. The hash is argon2id in PHC format, see `PasswordHasher`. */
object LocalCredentialTable : Table("local_credential") {
    val principalId = reference("principal_id", PrincipalTable, onDelete = CASCADE)
    val passwordHash = varchar("password_hash", 512)
    val mustChangePassword = bool("must_change_password").default(false)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(principalId)
}
