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

import com.reposilite.IsolatedResources
import com.reposilite.SharedContainers
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.databaseToken
import com.reposilite.schemaToken
import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.outbox.IndexAction
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The index on MariaDB and PostgreSQL (artifact search spec, 10.3): the schema with its key limits and collations, the
 * upsert of each dialect, and one search query through the caller's row filter. Every test gets its own database or
 * schema from the shared containers and drops it afterwards.
 */
internal class SearchIndexDialectIntegrationTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC)
    private val path = "com/example/app/1.0.0/app-1.0.0.jar"

    @TempDir
    lateinit var workingDirectory: Path

    enum class Remote { MARIADB, POSTGRESQL }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Remote::class)
    fun `creates the search tables once when the schema is ensured twice`(remote: Remote) {
        withRemoteDatabase(remote) { database ->
            val first = SearchSchema.ensure(database, clock)
            val second = SearchSchema.ensure(database, clock)

            assertThat(first).`as`("first start on $remote").isEqualTo(SearchSchema.Outcome.CREATED)
            assertThat(second).`as`("second start on $remote").isEqualTo(SearchSchema.Outcome.ALREADY_CREATED)
            assertThat(tableNames(database)).`as`("tables on $remote").containsAll(listOf("search_artifact", "search_outbox"))
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Remote::class)
    fun `keeps one row when the same upsert is replayed`(remote: Remote) {
        withRemoteDatabase(remote) { database ->
            SearchSchema.ensure(database, clock)
            val writer = DatabaseArtifactIndex(database)

            writer.apply(listOf(IndexAction.Upsert(row(repository = "releases"))))
            writer.apply(listOf(IndexAction.Upsert(row(repository = "releases"))))

            assertThat(rowCount(database)).`as`("rows after a replay on $remote").isEqualTo(1)
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Remote::class)
    fun `returns only the indexed rows the caller may see for one search query`(remote: Remote) {
        withRemoteDatabase(remote) { database ->
            SearchSchema.ensure(database, clock)
            DatabaseArtifactIndex(database).apply(
                listOf(
                    IndexAction.Upsert(row(repository = "releases")),
                    IndexAction.Upsert(row(repository = "hidden")),
                ),
            )
            val releasesOnly = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.All)))

            val page = DatabaseArtifactSearchEngine(database, clock).search(ArtifactSearchQuery(artifactId = "app"), releasesOnly)

            assertThat(page.items.map { it.artifactId to it.repositories.map { repo -> repo.name } })
                .`as`("search results on $remote")
                .containsExactly("app" to listOf("releases"))
        }
    }

    private fun row(repository: String) = IndexRow(
        file = ArtifactFileRecord(
            repository = repository,
            path = path,
            groupId = "com.example",
            artifactId = "app",
            version = "1.0.0",
            classifier = null,
            extension = "jar",
            snapshot = false,
        ),
        provenance = Provenance.HOSTED,
        sizeBytes = 10L,
        lastModified = 1_000L,
        sha1 = null,
        sha256 = null,
        indexedAt = clock.millis(),
    )

    /** Connects through the production connection factory with a database or schema of this test only. */
    private fun <T> withRemoteDatabase(remote: Remote, block: (Database) -> T): T =
        when (remote) {
            Remote.MARIADB -> {
                val name = IsolatedResources.createMariaDbDatabase(SharedContainers.mariaDb)
                try {
                    DatabaseConnectionFactory.createConnection(workingDirectory, SharedContainers.mariaDb.databaseToken(name), 2)
                        .use { block(it.database) }
                } finally {
                    IsolatedResources.dropMariaDbDatabase(SharedContainers.mariaDb, name)
                }
            }
            Remote.POSTGRESQL -> {
                val schema = IsolatedResources.createPostgresSchema(SharedContainers.postgres)
                try {
                    DatabaseConnectionFactory.createConnection(workingDirectory, SharedContainers.postgres.schemaToken(schema), 2)
                        .use { block(it.database) }
                } finally {
                    IsolatedResources.dropPostgresSchema(SharedContainers.postgres, schema)
                }
            }
        }

    /** The tables of the database or schema of this test, without the schema prefix MariaDB and PostgreSQL add. */
    private fun tableNames(database: Database): List<String> =
        transaction(database) { SchemaUtils.listTables().map { it.substringAfterLast('.').lowercase() } }

    private fun rowCount(database: Database): Int =
        transaction(database) { SearchTables(SearchDialect.of(database.vendor)).artifact.selectAll().count().toInt() }

}
