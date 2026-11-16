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
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.javatime.timestamp

/** One local password per principal. The hash is argon2id in PHC format, see `PasswordHasher`. */
object LocalCredentialTable : Table("local_credential") {
    val principalId = reference("principal_id", PrincipalTable, onDelete = CASCADE)
    val passwordHash = varchar("password_hash", 512)
    val mustChangePassword = bool("must_change_password").default(false)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(principalId)
}

/**
 * Refresh tokens of browser sessions. Only a SHA-256 hash of each token is stored. A token and every token issued after it
 * share a [familyId], so that one logout, or the reuse of a spent token, revokes the whole chain. [usedAt] is set when the
 * token is exchanged for the next one, which makes it single use.
 */
object RefreshTokenTable : IntIdTable("refresh_token") {
    val familyId = varchar("family_id", 64).index()
    val principalId = reference("principal_id", PrincipalTable, onDelete = CASCADE)
    val tokenHash = varchar("token_hash", 128).uniqueIndex("uq_refresh_token_hash")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    val revokedAt = timestamp("revoked_at").nullable()
    val userAgent = varchar("user_agent", 512).nullable()
    val ip = varchar("ip", 64).nullable()

    /** The last authentication of the family, the `ath` of its sessions. Null for a family from before the claim existed. */
    val authenticatedAt = timestamp("authenticated_at").nullable()

    /** Whether a second factor completed the family, the `mfa` of its sessions. */
    val mfa = bool("mfa").default(false)
}

/**
 * Markers that exist once per database, such as the claim of the first administrator. A claim is an insert that ignores an
 * existing row, so of several instances sharing the database only one gets it.
 */
object LoginSetupMarkerTable : Table("login_setup_marker") {
    val name = varchar("name", 64)
    val claimedAt = timestamp("claimed_at")

    override val primaryKey = PrimaryKey(name)
}

/**
 * The addresses a name signed in from, for the per-account delay. [subject] is the lower case name of the account, a user
 * name or a token name, and [addressHash] is the SHA-256 of the address, so the table holds no plain address. A row is
 * written by a successful sign-in only while the account throttle is on, and [lastSuccessAt] decides whether the address is
 * known.
 */
object LoginKnownAddressTable : Table("login_known_address") {
    val subject = varchar("subject", 128)
    val addressHash = varchar("address_hash", 64)
    val firstSeenAt = timestamp("first_seen_at")
    val lastSuccessAt = timestamp("last_success_at").index()

    override val primaryKey = PrimaryKey(subject, addressHash)
}

/**
 * Failed password logins, one row per key: a user name with an address, and an address on its own. The keys are hashes,
 * so the table holds no plain user name. [version] changes with every write, which makes concurrent updates from
 * several instances of the database conditional: the loser of a race retries against the state it finds.
 */
object LoginAttemptTable : Table("login_attempt") {
    val attemptKey = varchar("attempt_key", 64)
    val failures = integer("failures")
    val lockouts = integer("lockouts")
    val lastFailureAt = timestamp("last_failure_at").index()
    val lockedUntil = timestamp("locked_until").nullable()
    val version = integer("version")

    override val primaryKey = PrimaryKey(attemptKey)
}

/**
 * Second factors of a user. A confirmed row is a factor; a row without [confirmedAt] is an enrolment that waits for its first
 * code and counts for nothing at sign-in. For an authenticator, [secret] is the seed sealed with the settings key, and
 * [lastUsedStep] is the last time step whose code was accepted, which keeps a code from being used twice.
 * [factorKey] names the factor in the envelope of its seed, so the seed cannot be moved to another row.
 *
 * A passkey (kind `PASSKEY`) has no [secret]. It keeps its [credentialId] (base64url, unique), its attested credential data in
 * [publicKey] and the last signature counter in [signCount]. The columns are nullable because the other kinds do not use them.
 * The credential id is limited to 768 characters, which keeps the unique index within the key length of MariaDB and MySQL.
 */
object MfaFactorTable : IntIdTable("mfa_factor") {
    val principalId = reference("principal_id", PrincipalTable, onDelete = CASCADE).index()
    val kind = varchar("kind", 16)
    val name = varchar("name", 128)
    val factorKey = varchar("factor_key", 36).uniqueIndex()
    val secret = varchar("secret", 512).nullable()
    val confirmedAt = timestamp("confirmed_at").nullable()
    val lastUsedStep = long("last_used_step").nullable()
    val createdAt = timestamp("created_at")
    val lastUsedAt = timestamp("last_used_at").nullable()
    val credentialId = varchar("credential_id", 768).nullable().uniqueIndex("uq_mfa_factor_credential_id")
    val publicKey = binary("public_key", 2048).nullable()
    val signCount = long("sign_count").nullable()
}

/** Recovery codes of a user, as SHA-256 hashes of their normalised form. A code is spent by setting [usedAt]. */
object MfaRecoveryCodeTable : IntIdTable("mfa_recovery_code") {
    val principalId = reference("principal_id", PrincipalTable, onDelete = CASCADE).index()
    val codeHash = varchar("code_hash", 64).uniqueIndex()
    val createdAt = timestamp("created_at")
    val usedAt = timestamp("used_at").nullable()
}

/**
 * Sign-in challenges that wait for a second factor after a password was accepted, and the challenges of passkey ceremonies. Only a
 * SHA-256 hash of the challenge id is stored; the id itself lives in the browser cookie or with the client. A challenge is single
 * use ([usedAt]) and short lived ([expiresAt]). [challenge] holds the random bytes of a WebAuthn ceremony and is null otherwise.
 */
object AuthChallengeTable : Table("auth_challenge") {
    val idHash = varchar("id_hash", 64)
    val kind = varchar("kind", 32)
    val principalId = reference("principal_id", PrincipalTable, onDelete = CASCADE).index()
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at").index()
    val usedAt = timestamp("used_at").nullable()
    val challenge = binary("challenge", 64).nullable()

    override val primaryKey = PrimaryKey(idHash)
}
