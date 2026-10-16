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
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.privateTeamName
import net.onelitefeather.ingot.identity.storage.ProductionShapeDataset.ProbeKind
import net.onelitefeather.ingot.identity.storage.ProductionShapeDataset.TYPO_ROUTE
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Starts Ingot on a legacy database that holds the production shape (see [ProductionShapeDataset]) and checks what the
 * migration makes of it. The start is the real one: [AccessTokenComponents] runs the same migrations as the server does.
 * Every database is a fresh file in a temporary directory, and the clock is fixed for the identity store.
 */
internal class ProductionShapeMigrationIntegrationTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var workingDirectory: Path

    @ParameterizedTest
    @ValueSource(strings = ["sqlite", "h2"])
    fun `migrates the production shape without an exception or an error line into one machine principal per token`(kind: String) {
        withLegacyDatabase(kind) { database ->
            // given: the legacy tables of a 1.1.0 installation with the production tokens
            ProductionShapeDataset.seedLegacyTokens(database, journalist)
            val logBefore = journalist.getMessages().size

            // when: Ingot starts on that database
            assertThatCode { AccessTokenComponents(journalist, database).accessTokenFacade() }
                .describedAs("starting Ingot on the legacy database")
                .doesNotThrowAnyException()

            // then: the migration logged no error
            val errors = journalist.getMessages().drop(logBefore).filter { it.key == Channel.ERROR }.map { it.value }
            assertThat(errors).describedAs("error lines written by the migration").isEmpty()

            // and: every legacy token is a machine principal under its own name
            val store = IdentityStore(database, clock)
            val machines = store.listPrincipals(PrincipalKind.MACHINE)
            assertThat(machines.map { it.name })
                .describedAs("machine principals after the migration")
                .containsExactlyInAnyOrderElementsOf(ProductionShapeDataset.secrets.keys)

            // and: admin, and nothing else, is an instance admin
            val instanceAdmins = machines.filter { store.hasDirectInstanceRole(it.id) }.map { it.name }
            assertThat(instanceAdmins).describedAs("instance admins after the migration").containsExactly("admin")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite", "h2"])
    fun `each migrated token accepts its own secret and rejects the secret of another token`(kind: String) {
        withLegacyDatabase(kind) { database ->
            // given: the production shape, migrated
            ProductionShapeDataset.seedLegacyTokens(database, journalist)
            val facade = AccessTokenComponents(journalist, database).accessTokenFacade()

            // when / then: every token authenticates with its own name and secret
            ProductionShapeDataset.secrets.forEach { (name, secret) ->
                val identifier = facade.getAccessToken(name)!!.identifier
                assertThat(facade.secretMatches(identifier, secret)).describedAs("own secret of $name").isTrue
            }

            // and: the secret of the reader does not open the admin token
            val admin = facade.getAccessToken("admin")!!.identifier
            assertThat(facade.secretMatches(admin, ProductionShapeDataset.secrets.getValue(ProductionShapeDataset.READER_NAME)))
                .describedAs("secret of reposilite-reader used on admin")
                .isFalse
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite", "h2"])
    fun `admin is an instance admin and every probe is allowed, as the manager of the previous release was`(kind: String) {
        assertDecisionsMatchV110(kind, "admin")
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite", "h2"])
    fun `reposilite-publisher reads and writes its four repositories and only reads the other three`(kind: String) {
        assertDecisionsMatchV110(kind, "reposilite-publisher")
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite", "h2"])
    fun `reposilite-reader reads all seven repositories and writes to none of them`(kind: String) {
        assertDecisionsMatchV110(kind, ProductionShapeDataset.READER_NAME)
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite", "h2"])
    fun `reposilite-mirror reads the onelitefeather repositories with routes and writes nothing`(kind: String) {
        assertDecisionsMatchV110(kind, ProductionShapeDataset.MIRROR_NAME)
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite", "h2"])
    fun `dart-poet reads and writes only below its dartpoet route, the typo route included`(kind: String) {
        assertDecisionsMatchV110(kind, "dart-poet")
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite", "h2"])
    fun `keeps the typo route as an inert grant on a repository that does not exist and leaves dart-poet's real rights intact`(kind: String) {
        withLegacyDatabase(kind) { database ->
            // given: a legacy dart-poet token whose typo route names a repository that is not in the settings
            ProductionShapeDataset.seedLegacyTokens(database, journalist)

            // when: Ingot starts. The migration must not fail on the route.
            val facade = startIngot(database)

            // then: the typo route is carried over as a legacy grant, next to the four real dartpoet routes
            val store = IdentityStore(database, clock)
            val dartPoetTeam = store.findTeam(privateTeamName("dart-poet"))!!
            val grants = store.listGrants(dartPoetTeam.id)
            assertThat(grants.map { it.pathPrefix to it.role })
                .describedAs("grants of dart-poet after the migration")
                .containsExactlyInAnyOrderElementsOf(expectedDartPoetGrants())
            assertThat(grants.map { it.repository }.distinct()).describedAs("legacy grants have no repository field").containsExactly(null)

            // and: dart-poet keeps reading and writing its dartpoet artifacts in the four real repositories
            val dartPoet = facade.getAccessToken("dart-poet")!!.identifier
            listOf("releases", "snapshots", "onelitefeather-releases", "onelitefeather-snapshots").forEach { repository ->
                val artifact = "/$repository/${ProductionShapeDataset.DART_POET_DIRECTORY}x/1.0/x-1.0.jar"
                assertThat(facade.hasAccessTo(dartPoet, artifact, Action.READ)).describedAs("read $artifact").isTrue
                assertThat(facade.hasAccessTo(dartPoet, artifact, Action.WRITE)).describedAs("write $artifact").isTrue
            }

            // and: the typo route answers for its own path only, so the real repositories gain nothing from it
            val typoArtifact = "$TYPO_ROUTE/x/1.0/x-1.0.jar"
            assertThat(facade.hasAccessTo(dartPoet, typoArtifact, Action.WRITE))
                .describedAs("write on the typo path, which the route names literally")
                .isTrue
            assertThat(facade.hasAccessTo(facade.getAccessToken("reposilite-publisher")!!.identifier, typoArtifact, Action.WRITE))
                .describedAs("publisher writes to the typo path")
                .isFalse
            assertThat(facade.hasAccessTo(facade.getAccessToken("reposilite-mirror")!!.identifier, typoArtifact, Action.WRITE))
                .describedAs("mirror writes to the typo path")
                .isFalse
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite", "h2"])
    fun `a second start changes no identity row and logs no error`(kind: String) {
        withLegacyDatabase(kind) { database ->
            // given: a database that the migration has already run on
            ProductionShapeDataset.seedLegacyTokens(database, journalist)
            AccessTokenComponents(journalist, database).accessTokenFacade()
            val before = identitySnapshot(database)
            val logBefore = journalist.getMessages().size

            // when: the migration runs again, on its own and then as a full start
            val outcome = LegacyAccessTokenMigration(database, journalist).migrate()
            AccessTokenComponents(journalist, database).accessTokenFacade()

            // then: the marker answers, no identity row changed, and the run logged no error
            assertThat(outcome).describedAs("outcome of the second run").isEqualTo(MigrationOutcome.ALREADY_APPLIED)
            assertThat(identitySnapshot(database)).describedAs("identity rows after the second start").isEqualTo(before)
            val errors = journalist.getMessages().drop(logBefore).filter { it.key == Channel.ERROR }
            assertThat(errors).describedAs("error lines of the second start").isEmpty()
        }
    }

    /**
     * Starts the production shape and compares every probe with the v1.1.0 decision of [token]. Each probe is asked through
     * [AccessTokenFacade], the entry point the Maven and identity code use, so the test sees what a request sees.
     */
    private fun assertDecisionsMatchV110(kind: String, token: String) {
        withLegacyDatabase(kind) { database ->
            ProductionShapeDataset.seedLegacyTokens(database, journalist)
            val facade = startIngot(database)
            val identifier = facade.getAccessToken(token)!!.identifier

            val actual = ProductionShapeDataset.probes.associate { probe -> probe.key to decide(facade, identifier, probe) }

            assertThat(actual)
                .describedAs("decisions of $token compared with v1.1.0")
                .isEqualTo(ProductionShapeDataset.v110Decisions(token))
        }
    }

    private fun decide(facade: AccessTokenFacade, identifier: AccessTokenIdentifier, probe: ProductionShapeDataset.Probe): Boolean =
        when (probe.kind) {
            ProbeKind.READ -> facade.hasAccessTo(identifier, probe.target, Action.READ)
            ProbeKind.WRITE -> facade.hasAccessTo(identifier, probe.target, Action.WRITE)
            ProbeKind.SEE -> facade.canSee(identifier, probe.target)
        }

    /** Starts Ingot on [database]. An exception from the migrations fails the test, so the start is the assertion. */
    private fun startIngot(database: Database): AccessTokenFacade =
        AccessTokenComponents(journalist, database).accessTokenFacade()

    /** The grants dart-poet must hold after the migration: four dartpoet routes and the typo route, read and write each. */
    private fun expectedDartPoetGrants(): List<Pair<String?, Role>> {
        val paths = listOf("/releases", "/snapshots", "/onelitefeather-releases", "/onelitefeather-snapshots")
            .map { "$it/${ProductionShapeDataset.DART_POET_DIRECTORY}" } + TYPO_ROUTE
        return paths.flatMap { path -> listOf(path to Role.LEGACY_READ, path to Role.LEGACY_WRITE) }
    }

    /** Everything the identity tables hold for the migrated tokens, in a stable order. */
    private fun identitySnapshot(database: Database): List<String> {
        val store = IdentityStore(database, clock)
        val principals = store.listPrincipals().map { "principal ${it.id} ${it.name} ${it.kind} disabled=${it.disabled}" }
        val teams = store.listTeams().flatMap { team ->
            listOf("team ${team.id} ${team.name}") + store.listGrants(team.id).map { "grant ${team.id} ${it.repository} ${it.pathPrefix} ${it.role}" }
        }
        val instanceRoles = store.listInstanceRolePrincipalIds().map { "instance role $it" }
        val tokens = store.listTokens().map { "token ${it.id} ${it.name} principal=${it.principalId} hash=${it.secretHash}" }
        return principals + teams + instanceRoles + tokens
    }

    private fun withLegacyDatabase(kind: String, block: (Database) -> Unit) {
        val url = when (kind) {
            "sqlite" -> "jdbc:sqlite:${workingDirectory.resolve("reposilite.db").toAbsolutePath()}"
            "h2" -> "jdbc:h2:${workingDirectory.resolve("reposilite").toAbsolutePath()};MODE=MYSQL"
            else -> error("Unknown database kind: $kind")
        }
        DatabaseConnectionFactory.createConnection(workingDirectory, url, 1).use { connection ->
            block(connection.database)
        }
    }

}
