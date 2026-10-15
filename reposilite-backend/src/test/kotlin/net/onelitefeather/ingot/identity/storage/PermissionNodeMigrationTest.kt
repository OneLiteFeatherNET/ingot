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

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.NODE_SUBJECT_TEAM
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.Role
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Migration `002-permission-nodes`: repository roles, instance roles and token scopes become nodes; path grants and legacy
 * grants stay. Each test starts from an empty database and seeds the state an earlier version left behind.
 */
internal class PermissionNodeMigrationTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val createdAt = LocalDate.of(2026, 1, 1)

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `a repository-wide deploy grant becomes the read and deploy nodes of its team`() {
        withDatabase { database, store ->
            // given
            val deployers = store.createTeam("deployers", "", null)!!
            store.addGrant(deployers.id, "releases", null, Role.DEPLOY)

            // when
            PermissionNodeMigration(database, journalist).migrate()

            // then
            assertThat(nodesOf(store, deployers.id))
                .describedAs("nodes of deployers")
                .containsExactlyInAnyOrder("ingot.repository.releases.read" to true, "ingot.repository.releases.deploy" to true)
        }
    }

    @Test
    fun `a maintain grant becomes read, deploy and delete`() {
        withDatabase { database, store ->
            val maintainers = store.createTeam("maintainers", "", null)!!
            store.addGrant(maintainers.id, "snapshots", null, Role.MAINTAIN)

            PermissionNodeMigration(database, journalist).migrate()

            assertThat(nodesOf(store, maintainers.id))
                .describedAs("nodes of maintainers")
                .containsExactlyInAnyOrder(
                    "ingot.repository.snapshots.read" to true,
                    "ingot.repository.snapshots.deploy" to true,
                    "ingot.repository.snapshots.delete" to true,
                )
        }
    }

    @Test
    fun `an admin grant on every repository becomes four explicit nodes on the wildcard`() {
        withDatabase { database, store ->
            val admins = store.createTeam("admins", "", null)!!
            store.addGrant(admins.id, null, null, Role.ADMIN)

            PermissionNodeMigration(database, journalist).migrate()

            assertThat(nodesOf(store, admins.id))
                .describedAs("nodes of admins")
                .containsExactlyInAnyOrder(
                    "ingot.repository.*.read" to true,
                    "ingot.repository.*.deploy" to true,
                    "ingot.repository.*.delete" to true,
                    "ingot.repository.*.manage" to true,
                )
        }
    }

    @Test
    fun `the repository-wide grants it converted are removed`() {
        withDatabase { database, store ->
            val deployers = store.createTeam("deployers", "", null)!!
            store.addGrant(deployers.id, "releases", null, Role.DEPLOY)

            PermissionNodeMigration(database, journalist).migrate()

            assertThat(store.listGrants(deployers.id)).describedAs("grants left").isEmpty()
        }
    }

    @Test
    fun `path-scoped and legacy grants stay exactly as they were`() {
        withDatabase { database, store ->
            val deployers = store.createTeam("deployers", "", null)!!
            val bot = store.createTeam("machine:bot", "private", null)!!
            store.addGrant(deployers.id, "releases", "com/example", Role.DEPLOY)
            store.addGrant(bot.id, null, "/releases", Role.LEGACY_READ)

            PermissionNodeMigration(database, journalist).migrate()

            assertThat(store.listGrants(deployers.id).map { it.repository to it.pathPrefix to it.role })
                .describedAs("path grant of deployers")
                .containsExactly(("releases" to "com/example") to Role.DEPLOY)
            assertThat(store.listGrants(bot.id).map { it.pathPrefix to it.role })
                .describedAs("legacy grant of the private team")
                .containsExactly("/releases" to Role.LEGACY_READ)
        }
    }

    @Test
    fun `an instance role of a principal becomes the admin node of that principal`() {
        withDatabase { database, store ->
            val alice = store.createPrincipal(PrincipalKind.USER, "alice", "Alice", false, createdAt.atStartOfDay(ZoneOffset.UTC).toInstant())!!
            seedInstanceRole(database, principalId = alice.id, teamId = null)

            PermissionNodeMigration(database, journalist).migrate()

            assertThat(store.listNodes(PrincipalKind.USER.name, alice.id).map { it.node to it.allowed })
                .describedAs("nodes of alice")
                .containsExactly("ingot.admin.*" to true)
        }
    }

    @Test
    fun `an instance role of a team becomes the admin node of that team, and its rows are removed`() {
        withDatabase { database, store ->
            val ops = store.createTeam("ops", "", null)!!
            seedInstanceRole(database, principalId = null, teamId = ops.id)

            PermissionNodeMigration(database, journalist).migrate()

            assertThat(nodesOf(store, ops.id)).describedAs("nodes of ops").containsExactly("ingot.admin.*" to true)
            assertThat(instanceRoleRows(database)).describedAs("instance role rows left").isZero
        }
    }

    @Test
    fun `the scopes of a token become its node list`() {
        withDatabase { database, store ->
            val bot = store.createPrincipal(PrincipalKind.MACHINE, "ci-bot", "ci-bot", false, createdAt.atStartOfDay(ZoneOffset.UTC).toInstant())!!
            seedToken(database, principalId = bot.id, name = "ci-token", scopes = setOf(Action.READ, Action.WRITE))

            PermissionNodeMigration(database, journalist).migrate()

            assertThat(store.findToken("ci-token")!!.nodes?.map { it.node to it.allowed })
                .describedAs("node list of the token")
                .containsExactlyInAnyOrder("ingot.repository.*.read" to true, "ingot.repository.*.deploy" to true)
        }
    }

    @Test
    fun `a token without scopes keeps no node list`() {
        withDatabase { database, store ->
            val bot = store.createPrincipal(PrincipalKind.MACHINE, "legacy-bot", "legacy-bot", false, createdAt.atStartOfDay(ZoneOffset.UTC).toInstant())!!
            seedToken(database, principalId = bot.id, name = "legacy-token", scopes = null)

            PermissionNodeMigration(database, journalist).migrate()

            assertThat(store.findToken("legacy-token")!!.nodes).describedAs("node list").isNull()
        }
    }

    @Test
    fun `the migration runs once and a second start changes nothing`() {
        withDatabase { database, store ->
            val deployers = store.createTeam("deployers", "", null)!!
            store.addGrant(deployers.id, "releases", null, Role.DEPLOY)
            PermissionNodeMigration(database, journalist).migrate()
            val nodesAfterFirstRun = nodesOf(store, deployers.id)

            val second = PermissionNodeMigration(database, journalist).migrate()

            assertThat(second).describedAs("outcome of the second start").isEqualTo(MigrationOutcome.ALREADY_APPLIED)
            assertThat(nodesOf(store, deployers.id)).describedAs("nodes after the second start").isEqualTo(nodesAfterFirstRun)
            assertThat(markerRows(database)).describedAs("marker rows").isEqualTo(1)
        }
    }

    private fun nodesOf(store: IdentityStore, teamId: Int): List<Pair<String, Boolean>> =
        store.listNodes(NODE_SUBJECT_TEAM, teamId).map { it.node to it.allowed }

    private fun seedInstanceRole(database: Database, principalId: Int?, teamId: Int?) {
        transaction(database) {
            InstanceRoleTable.insert {
                it[InstanceRoleTable.principalId] = principalId?.let { value -> EntityID(value, PrincipalTable) }
                it[InstanceRoleTable.teamId] = teamId?.let { value -> EntityID(value, TeamTable) }
                it[InstanceRoleTable.role] = "INSTANCE_ADMIN"
            }
        }
    }

    private fun seedToken(database: Database, principalId: Int, name: String, scopes: Set<Action>?) {
        val day = createdAt
        transaction(database) {
            PrincipalTokenTable.insert {
                it[PrincipalTokenTable.principalId] = EntityID(principalId, PrincipalTable)
                it[PrincipalTokenTable.name] = name
                it[PrincipalTokenTable.secretHash] = "hash"
                it[PrincipalTokenTable.scopes] = IdentityStore.encodeScopes(scopes)
                it[PrincipalTokenTable.createdAt] = day
                it[PrincipalTokenTable.description] = ""
            }
        }
    }

    private fun instanceRoleRows(database: Database): Long =
        transaction(database) { InstanceRoleTable.selectAll().count() }

    private fun markerRows(database: Database): Long =
        transaction(database) { IdentityMigrationTable.selectAll().where { IdentityMigrationTable.name eq PermissionNodeMigration.MIGRATION_NAME }.count() }

    private fun withDatabase(block: (Database, IdentityStore) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val database = connection.database
            block(database, IdentityStore(database, clock))
        }
    }
}
