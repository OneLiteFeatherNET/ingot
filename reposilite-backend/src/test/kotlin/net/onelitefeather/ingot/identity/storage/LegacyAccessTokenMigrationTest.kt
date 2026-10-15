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
import com.reposilite.token.AccessToken
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.AccessTokenType.PERSISTENT
import com.reposilite.token.Route
import com.reposilite.token.RoutePermission.READ
import com.reposilite.token.RoutePermission.WRITE
import com.reposilite.token.infrastructure.AccessTokenTable
import com.reposilite.token.infrastructure.PermissionToAccessTokenTable
import com.reposilite.token.infrastructure.PermissionToRouteTable
import com.reposilite.token.infrastructure.SqlAccessTokenRepository
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.privateTeamName
import net.onelitefeather.ingot.identity.storage.MigrationOutcome.ALREADY_APPLIED
import net.onelitefeather.ingot.identity.storage.MigrationOutcome.MIGRATED
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class LegacyAccessTokenMigrationTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.systemUTC()

    @TempDir
    lateinit var workingDirectory: Path

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `migrates each access token to a machine principal with the same name and secret`(databaseConfiguration: String) {
        withLegacyDatabase(databaseConfiguration) { database ->
            // given: a legacy database with a token that has metadata
            seedLegacyTokens(database)
            val store = IdentityStore(database, clock)

            // when: the migration runs
            val outcome = LegacyAccessTokenMigration(database, journalist).migrate()

            // then: the token and its principal carry the legacy name, secret and metadata
            assertThat(outcome).describedAs("first run should migrate").isEqualTo(MIGRATED)
            assertThat(store.findPrincipal("ci-bot")?.kind?.name).describedAs("principal kind").isEqualTo("MACHINE")
            val token = store.findToken("ci-bot")!!
            assertThat(token.secretHash).describedAs("secret hash is copied as it is").isEqualTo("hash-ci")
            assertThat(token.description).isEqualTo("pipeline")
            assertThat(token.createdAt).isEqualTo(LocalDate.of(2025, 3, 1))
            assertThat(token.expiresAt).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"))
            assertThat(token.scopes).describedAs("legacy tokens are not narrowed").isNull()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `keeps the identifiers of the migrated tokens`(databaseConfiguration: String) {
        withLegacyDatabase(databaseConfiguration) { database ->
            // given: a legacy token with a known identifier
            seedLegacyTokens(database)
            val legacyId = legacyIdOf(database, "ci-bot")

            // when: the migration runs
            LegacyAccessTokenMigration(database, journalist).migrate()

            // then: the token keeps the identifier it had before the upgrade
            assertThat(IdentityStore(database, clock).findToken("ci-bot")?.id).describedAs("identifier after migration").isEqualTo(legacyId)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `maps the manager permission to an instance role and each route to a legacy grant`(databaseConfiguration: String) {
        withLegacyDatabase(databaseConfiguration) { database ->
            // given: a manager token with two routes and a reader with one route
            seedLegacyTokens(database)

            // when: the migration runs
            LegacyAccessTokenMigration(database, journalist).migrate()
            PermissionNodeMigration(database, journalist).migrate()

            // then: the manager is an instance admin, the reader is not, and the routes are legacy grants on the private team
            val store = IdentityStore(database, clock)
            val ci = store.findPrincipal("ci-bot")!!
            val reader = store.findPrincipal("reader")!!
            assertThat(store.hasDirectInstanceRole(ci.id)).describedAs("manager permission becomes an instance role").isTrue
            assertThat(store.hasDirectInstanceRole(reader.id)).describedAs("a token without manager is not an instance admin").isFalse

            val ciGrants = store.listGrants(store.findTeam(privateTeamName("ci-bot"))!!.id)
            assertThat(ciGrants.map { it.pathPrefix to it.role })
                .describedAs("grants of the manager token")
                .containsExactlyInAnyOrder("/releases" to Role.LEGACY_READ, "/snapshots/com/example" to Role.LEGACY_WRITE)
            assertThat(ciGrants.map { it.repository }.distinct()).describedAs("legacy grants have no repository field").containsExactly(null)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `running the migration a second time changes nothing`(databaseConfiguration: String) {
        withLegacyDatabase(databaseConfiguration) { database ->
            // given: a migrated database
            seedLegacyTokens(database)
            IdentityStore(database, clock)
            LegacyAccessTokenMigration(database, journalist).migrate()
            val before = identityCounts(database)

            // when: the migration runs again
            val outcome = LegacyAccessTokenMigration(database, journalist).migrate()

            // then: nothing is claimed twice and no row is added
            assertThat(outcome).describedAs("second run should see the marker").isEqualTo(ALREADY_APPLIED)
            assertThat(identityCounts(database)).describedAs("identity row counts after the second run").isEqualTo(before)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `leaves the legacy tables intact`(databaseConfiguration: String) {
        withLegacyDatabase(databaseConfiguration) { database ->
            // given: a legacy database with tokens, permissions and routes
            seedLegacyTokens(database)
            val before = legacyRows(database)

            // when: the migration runs
            IdentityStore(database, clock)
            LegacyAccessTokenMigration(database, journalist).migrate()

            // then: the legacy rows are exactly as they were, so a downgrade can read them
            assertThat(legacyRows(database)).describedAs("legacy rows after migration").isEqualTo(before)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `a token created after the migration gets an identifier of its own`(databaseConfiguration: String) {
        withLegacyDatabase(databaseConfiguration) { database ->
            // given: a migrated database whose largest identifier is that of the last legacy token
            seedLegacyTokens(database)
            val legacyIds = legacyIdsOf(database)
            LegacyAccessTokenMigration(database, journalist).migrate()
            val repository = IdentityAccessTokenRepository(IdentityStore(database, clock))

            // when: a new token is created through the repository
            val created = repository.saveAccessToken(AccessToken(name = "fresh", encryptedSecret = "hash-fresh"))

            // then: the new identifier does not collide with a migrated one
            assertThat(created.identifier.type).isEqualTo(PERSISTENT)
            assertThat(created.identifier.value).describedAs("identifier of the new token").isNotIn(legacyIds)
        }
    }

    @Test
    fun `concurrent starts migrate exactly once`() {
        // given: a legacy SQLite file and two independent connections to it, both about to start
        val file = workingDirectory.resolve("reposilite.db").toAbsolutePath()
        val first = Database.connect("jdbc:sqlite:$file", driver = "org.sqlite.JDBC")
        val second = Database.connect("jdbc:sqlite:$file", driver = "org.sqlite.JDBC")
        seedLegacyTokens(first)
        IdentityStore(first, clock)

        // when: both instances run the migration at the same moment
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(first, second).map { database ->
                executor.submit<MigrationOutcome> {
                    start.await(10, TimeUnit.SECONDS)
                    LegacyAccessTokenMigration(database, journalist).migrate()
                }
            }
            start.countDown()
            val outcomes = futures.map { it.get(30, TimeUnit.SECONDS) }

            // then: exactly one instance migrates, and every identity row exists once
            assertThat(outcomes).describedAs("outcomes of the two instances")
                .containsExactlyInAnyOrder(ALREADY_APPLIED, MIGRATED)
            val store = IdentityStore(first, clock)
            assertThat(store.listTokens()).describedAs("tokens after the race").hasSize(3)
            assertThat(store.listPrincipals()).describedAs("principals after the race").hasSize(3)
            assertThat(store.listGrants(store.findTeam(privateTeamName("ci-bot"))!!.id)).describedAs("grants after the race").hasSize(2)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun withLegacyDatabase(databaseConfiguration: String, block: (Database) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, databaseConfiguration, 1).use { connection ->
            block(connection.database)
        }
    }

    /** Writes the legacy state through the legacy repository, so the rows look exactly as an old installation wrote them. */
    private fun seedLegacyTokens(database: Database) {
        val legacy = SqlAccessTokenRepository(database, journalist, emptyArray())
        val ci = legacy.saveAccessToken(
            AccessToken(
                name = "ci-bot",
                encryptedSecret = "hash-ci",
                createdAt = LocalDate.of(2025, 3, 1),
                description = "pipeline",
                expiresAt = Instant.parse("2027-01-01T00:00:00Z"),
            )
        )
        legacy.addPermission(ci.identifier, MANAGER)
        legacy.addRoute(ci.identifier, Route("/releases", READ))
        legacy.addRoute(ci.identifier, Route("/snapshots/com/example", WRITE))

        val reader = legacy.saveAccessToken(AccessToken(name = "reader", encryptedSecret = "hash-reader", createdAt = LocalDate.of(2025, 3, 2)))
        legacy.addRoute(reader.identifier, Route("/releases", READ))

        legacy.saveAccessToken(AccessToken(name = "no-rights", encryptedSecret = "hash-plain", createdAt = LocalDate.of(2025, 3, 3)))
    }

    private fun legacyIdOf(database: Database, name: String): Int =
        transaction(database) {
            AccessTokenTable.selectAll().where { AccessTokenTable.name eq name }.single()[AccessTokenTable.id].value
        }

    private fun legacyIdsOf(database: Database): List<Int> =
        transaction(database) { AccessTokenTable.selectAll().map { it[AccessTokenTable.id].value } }

    private fun legacyRows(database: Database): List<String> =
        transaction(database) {
            val tokens = AccessTokenTable.selectAll().map { "token ${it[AccessTokenTable.id].value} ${it[AccessTokenTable.name]} ${it[AccessTokenTable.secret]} ${it[AccessTokenTable.description]}" }
            val permissions = PermissionToAccessTokenTable.selectAll().map { "permission ${it[PermissionToAccessTokenTable.accessTokenId].value} ${it[PermissionToAccessTokenTable.permission]}" }
            val routes = PermissionToRouteTable.selectAll().map { "route ${it[PermissionToRouteTable.accessTokenId].value} ${it[PermissionToRouteTable.route]} ${it[PermissionToRouteTable.permission]}" }
            (tokens + permissions + routes).sorted()
        }

    private fun identityCounts(database: Database): List<Long> {
        val store = IdentityStore(database, clock)
        return listOf(
            store.listPrincipals().size.toLong(),
            store.listTeams().size.toLong(),
            store.countTokens(),
            store.listTeams().sumOf { store.listGrants(it.id).size.toLong() },
        )
    }

}
