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
import com.reposilite.token.application.AccessTokenComponents
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The policy feature fence: a database that records a feature this build does not know stops startup, and the check runs
 * before the identity migrations. Each test opens its own temporary database and seeds the state it needs.
 */
internal class PolicyFeatureFenceTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC)
    private val runningVersion = "9.9.9-test"

    @TempDir
    lateinit var workingDirectory: Path

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `a fresh database gets the policy feature table with no rows`(databaseConfiguration: String) {
        withDatabase(databaseConfiguration) { database ->
            // when: the fence checks a database that has never seen a feature
            PolicyFeatureFence(database, journalist, knownFeatures = emptySet(), runningVersion = runningVersion).check()

            // then: the table exists and holds no rows
            val tables = transaction(database) { SchemaUtils.listTables() }
            assertThat(tables.map { it.substringAfterLast('.').lowercase() })
                .describedAs("tables on $databaseConfiguration")
                .contains("policy_feature")
            assertThat(transaction(database) { PolicyFeatureTable.selectAll().count() })
                .describedAs("policy feature rows on $databaseConfiguration")
                .isZero()
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `a database whose features are all known starts normally`(databaseConfiguration: String) {
        withDatabase(databaseConfiguration) { database ->
            // given: a feature this build knows
            enableFeature(database, "restrictions")

            // when: the fence checks the database
            PolicyFeatureFence(database, journalist, knownFeatures = setOf("restrictions"), runningVersion = runningVersion).check()

            // then: nothing is logged as an error
            assertThat(errorsLogged()).describedAs("error log").isEmpty()
        }
    }

    @Test
    fun `an unknown feature stops startup with a message that names the feature and the running version`() {
        withDatabase("sqlite --temporary") { database ->
            // given: a feature written by a newer build
            enableFeature(database, "restrictions")
            val fence = PolicyFeatureFence(database, journalist, knownFeatures = emptySet(), runningVersion = runningVersion)

            // when / then: startup is refused with both names in the message
            assertThatThrownBy { fence.check() }
                .describedAs("refusal of an unknown feature")
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("restrictions")
                .hasMessageContaining(runningVersion)
        }
    }

    @Test
    fun `the error log names the feature and the running version`() {
        withDatabase("sqlite --temporary") { database ->
            // given
            enableFeature(database, "restrictions")
            val fence = PolicyFeatureFence(database, journalist, knownFeatures = emptySet(), runningVersion = runningVersion)

            // when
            runCatching { fence.check() }

            // then
            assertThat(errorsLogged())
                .describedAs("error log")
                .hasSize(1)
            assertThat(errorsLogged().single())
                .describedAs("error message")
                .contains("restrictions")
                .contains(runningVersion)
        }
    }

    @Test
    fun `every unknown feature is named in the refusal`() {
        withDatabase("sqlite --temporary") { database ->
            // given: two features this build does not know, and one it does
            enableFeature(database, "restrictions")
            enableFeature(database, "namespaces")
            enableFeature(database, "signing")
            val fence = PolicyFeatureFence(database, journalist, knownFeatures = setOf("signing"), runningVersion = runningVersion)

            // when / then
            assertThatThrownBy { fence.check() }
                .hasMessageContaining("restrictions")
                .hasMessageContaining("namespaces")
                .hasMessageNotContaining("signing")
        }
    }

    @Test
    fun `a refused database is not migrated`() {
        withDatabase("sqlite --temporary") { database ->
            // given: a database written by a newer build, with a feature this build does not know
            enableFeature(database, "restrictions")

            // when: the access token components start on it
            assertThatThrownBy { AccessTokenComponents(journalist, database).accessTokenFacade() }
                .describedAs("startup refusal")
                .isInstanceOf(IllegalStateException::class.java)

            // then: neither identity migration claimed its marker, so neither ran
            val markers = transaction(database) { IdentityMigrationTable.selectAll().map { it[IdentityMigrationTable.name] } }
            assertThat(markers).describedAs("identity migration markers").isEmpty()
        }
    }

    private fun enableFeature(database: Database, feature: String) {
        transaction(database) {
            SchemaUtils.create(PolicyFeatureTable)
            PolicyFeatureTable.insert {
                it[PolicyFeatureTable.name] = feature
                it[PolicyFeatureTable.enabledAt] = Instant.now(clock)
            }
        }
    }

    private fun errorsLogged(): List<String> =
        journalist.messages.filter { it.key == Channel.ERROR }.map { it.value }

    private fun withDatabase(databaseConfiguration: String, block: (Database) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, databaseConfiguration, 1).use { connection ->
            block(connection.database)
        }
    }
}
