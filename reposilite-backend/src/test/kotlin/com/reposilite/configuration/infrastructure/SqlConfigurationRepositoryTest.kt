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

package com.reposilite.configuration.infrastructure

import com.reposilite.configuration.VersionedConfiguration
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Every test gets its own in-memory SQLite database, kept alive by a single pooled connection and closed with the test.
 */
internal class SqlConfigurationRepositoryTest {

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

    /**
     * The settings table as it was before revisions existed.
     */
    private object LegacySettingsTable : Table("settings") {
        val name = varchar("name", 32).uniqueIndex()
        val updateDate = datetime("updateDate")
        val content = text("content")
    }

    @Test
    fun `should count every write of a document in its revision`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            // given: a repository with a document written twice
            val repository = SqlConfigurationRepository(database)
            repository.saveConfiguration("doc", "one")
            repository.saveConfiguration("doc", "two")

            // when: the document is read with its revision
            val stored = repository.findVersionedConfiguration("doc")

            // then: the content is the latest write and the revision counts both writes
            assertThat(stored).isEqualTo(VersionedConfiguration(content = "two", revision = 2))
        }
    }

    @Test
    fun `should return no versioned document when nothing is stored under the name`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            assertThat(SqlConfigurationRepository(database).findVersionedConfiguration("doc")).isNull()
        }
    }

    @Test
    fun `should store a compare-and-set write while the revision is still the stored one`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            // given: a document at revision 1
            val repository = SqlConfigurationRepository(database)
            repository.saveConfiguration("doc", "one")

            // when: a write is made against revision 1
            val stored = repository.compareAndSaveConfiguration("doc", expectedRevision = 1, configuration = "two")

            // then: it is stored and advances the revision
            assertThat(stored).isTrue
            assertThat(repository.findVersionedConfiguration("doc")).isEqualTo(VersionedConfiguration(content = "two", revision = 2))
        }
    }

    @Test
    fun `should refuse a compare-and-set against a revision that another writer has advanced`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            // given: a reader that saw revision 1, and another writer that stored revision 2 since
            val repository = SqlConfigurationRepository(database)
            repository.saveConfiguration("doc", "one")
            repository.saveConfiguration("doc", "from-other-writer")

            // when: the reader writes against the revision it saw
            val stored = repository.compareAndSaveConfiguration("doc", expectedRevision = 1, configuration = "stale")

            // then: nothing is written and the other writer's document stays
            assertThat(stored).isFalse
            assertThat(repository.findConfiguration("doc")).isEqualTo("from-other-writer")
        }
    }

    @Test
    fun `should store a compare-and-set without revision only when nothing is stored yet`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            val repository = SqlConfigurationRepository(database)

            // when: two writers both expect the name to be free
            val first = repository.compareAndSaveConfiguration("doc", expectedRevision = null, configuration = "first")
            val second = repository.compareAndSaveConfiguration("doc", expectedRevision = null, configuration = "second")

            // then: only the first one is stored
            assertThat(first).isTrue
            assertThat(second).isFalse
            assertThat(repository.findConfiguration("doc")).isEqualTo("first")
        }
    }

    @Test
    fun `should store a document under a name only once when saved if absent`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            val repository = SqlConfigurationRepository(database)

            assertThat(repository.saveConfigurationIfAbsent("key", "first")).isTrue
            assertThat(repository.saveConfigurationIfAbsent("key", "second")).isFalse
            assertThat(repository.findConfiguration("key")).isEqualTo("first")
        }
    }

    @Test
    fun `should accept a compare-and-set at revision zero for a document written before revisions existed`() {
        val (dataSource, database) = inMemoryDatabase()

        dataSource.use {
            // given: a settings table without the revision column, holding a document from an earlier release
            transaction(database) {
                SchemaUtils.create(LegacySettingsTable)
                LegacySettingsTable.insert {
                    it[name] = "doc"
                    it[updateDate] = LocalDateTime.ofInstant(java.time.Instant.now(), ZoneOffset.UTC)
                    it[content] = "legacy"
                }
            }

            // when: the repository starts on that database and the document is read back
            val repository = SqlConfigurationRepository(database)
            val legacy = repository.findVersionedConfiguration("doc")

            // then: the column was added without losing the document, and it starts at revision zero
            assertThat(legacy).isEqualTo(VersionedConfiguration(content = "legacy", revision = 0))
            assertThat(repository.compareAndSaveConfiguration("doc", expectedRevision = 0, configuration = "migrated")).isTrue
        }
    }

}
