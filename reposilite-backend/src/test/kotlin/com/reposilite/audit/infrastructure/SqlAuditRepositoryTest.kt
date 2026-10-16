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

package com.reposilite.audit.infrastructure

import com.reposilite.audit.AuditCursor
import com.reposilite.audit.AuditQuery
import com.reposilite.audit.AuditRecord
import com.reposilite.audit.api.AuditAction.ARTIFACT_DEPLOYED
import com.reposilite.audit.api.AuditAction.LOGIN_FAILED
import com.reposilite.audit.api.AuditAction.TOKEN_DELETED
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.token.AccessToken
import com.reposilite.token.infrastructure.SqlAccessTokenRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Every test gets its own in-memory SQLite database. A single pooled connection keeps that database
 * alive between the transactions of one test, and the data source is closed when the test ends.
 */
internal class SqlAuditRepositoryTest {

    private val journalist = InMemoryLogger()

    private fun inMemoryDatabase(): Pair<HikariDataSource, Database> {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite::memory:"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
                minimumIdle = 1
            }
        )
        return dataSource to Database.connect(dataSource)
    }

    @Test
    fun `stores a record and reads it back with every column`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            val repository = SqlAuditRepository(database)

            val id = repository.insert(record(actor = "ci", address = "10.0.0.1", action = ARTIFACT_DEPLOYED, repository = "releases", path = "a/b.jar", details = """{"x":1}"""))

            val stored = repository.find(AuditQuery()).single()
            assertThat(stored.id).isEqualTo(id)
            assertThat(stored.actor).isEqualTo("ci")
            assertThat(stored.actorAddress).isEqualTo("10.0.0.1")
            assertThat(stored.action).isEqualTo(ARTIFACT_DEPLOYED)
            assertThat(stored.repository).isEqualTo("releases")
            assertThat(stored.path).isEqualTo("a/b.jar")
            assertThat(stored.details).isEqualTo("""{"x":1}""")
            assertThat(stored.success).isTrue()
            assertThat(stored.occurrences).isEqualTo(1)
        }
    }

    @Test
    fun `creates the audit table in a fresh database`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            val repository = SqlAuditRepository(database)

            assertThat(repository.count()).isZero()
        }
    }

    @Test
    fun `adds the audit table to an existing database without touching its token data`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            // given: a database that was created before the audit log existed, holding a token
            val tokens = SqlAccessTokenRepository(database, journalist, emptyArray())
            tokens.saveAccessToken(AccessToken(name = "existing", encryptedSecret = "secret"))

            // when: the audit repository starts on that database
            val repository = SqlAuditRepository(database)
            repository.insert(record(actor = "ci"))

            // then: the token survives and the audit table is usable
            assertThat(tokens.findAccessTokenByName("existing")).isNotNull()
            assertThat(repository.count()).isEqualTo(1)
        }
    }

    @Test
    fun `opens an existing audit table again with its rows intact`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            SqlAuditRepository(database).insert(record(actor = "ci"))

            val reopened = SqlAuditRepository(database)

            assertThat(reopened.count()).isEqualTo(1)
        }
    }

    @Test
    fun `an action filter with several actions matches any of them`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            val repository = SqlAuditRepository(database)
            repository.insert(record(action = ARTIFACT_DEPLOYED))
            repository.insert(record(action = LOGIN_FAILED))
            repository.insert(record(action = TOKEN_DELETED))

            val actions = repository.find(AuditQuery(actions = setOf(ARTIFACT_DEPLOYED, TOKEN_DELETED))).map { it.action }

            assertThat(actions).containsExactlyInAnyOrder(ARTIFACT_DEPLOYED, TOKEN_DELETED)
        }
    }

    @Test
    fun `matches the wildcard characters of the text filter only as literal characters`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            val repository = SqlAuditRepository(database)
            repository.insert(record(path = "a%b.jar"))
            repository.insert(record(path = "axb.jar"))
            repository.insert(record(path = "a_b.jar"))

            assertThat(repository.find(AuditQuery(text = "%")).map { it.path }).containsExactly("a%b.jar")
            assertThat(repository.find(AuditQuery(text = "_")).map { it.path }).containsExactly("a_b.jar")
        }
    }

    @Test
    fun `keeps rows with the same timestamp in id order across pages`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            val repository = SqlAuditRepository(database)
            val ids = (1..4).map { repository.insert(record(timestamp = 1_000L)) }

            val firstPage = repository.find(AuditQuery(limit = 2))
            val secondPage = repository.find(AuditQuery(limit = 2, after = AuditCursor(firstPage.last().timestamp, firstPage.last().id)))

            assertThat((firstPage + secondPage).map { it.id }).containsExactlyElementsOf(ids.reversed())
        }
    }

    @Test
    fun `increments the occurrence counter of a stored row`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            val repository = SqlAuditRepository(database)
            val id = repository.insert(record(action = LOGIN_FAILED, success = false))

            repository.incrementOccurrences(id)
            repository.incrementOccurrences(id)

            assertThat(repository.find(AuditQuery()).single().occurrences).isEqualTo(3)
        }
    }

    @Test
    fun `deletes only the rows older than the cutoff`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            val repository = SqlAuditRepository(database)
            repository.insert(record(timestamp = 1_000L, action = TOKEN_DELETED))
            repository.insert(record(timestamp = 2_000L, action = TOKEN_DELETED))
            repository.insert(record(timestamp = 3_000L, action = TOKEN_DELETED))

            assertThat(repository.deleteBefore(2_000L)).isEqualTo(1)
            assertThat(repository.find(AuditQuery()).map { it.timestamp }).containsExactly(3_000L, 2_000L)
        }
    }

    @Test
    fun `keeps the timestamp in epoch milliseconds`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            val repository = SqlAuditRepository(database)
            val instant = Instant.parse("2026-10-15T10:00:00.123Z")
            repository.insert(record(timestamp = instant.toEpochMilli()))

            assertThat(repository.find(AuditQuery()).single().timestamp).isEqualTo(instant.toEpochMilli())
        }
    }

    private fun record(
        timestamp: Long = 1_000L,
        actor: String = "ci",
        address: String? = null,
        action: com.reposilite.audit.api.AuditAction = TOKEN_DELETED,
        repository: String? = null,
        path: String? = null,
        details: String? = null,
        success: Boolean = true,
    ) = AuditRecord(
        id = 0,
        timestamp = timestamp,
        actor = actor,
        actorAddress = address,
        action = action,
        repository = repository,
        path = path,
        details = details,
        success = success,
        occurrences = 1,
    )

}
