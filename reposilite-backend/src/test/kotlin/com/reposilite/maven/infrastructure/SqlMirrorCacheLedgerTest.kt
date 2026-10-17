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

package com.reposilite.maven.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Two ledger instances on one database stand in for two instances of a cluster sharing a database.
 * Each test gets its own in-memory database, so no state leaks between tests.
 */
internal class SqlMirrorCacheLedgerTest {

    private val storedAt: Instant = Instant.parse("2026-10-15T12:00:00Z")
    private val database = freshInMemoryDatabase()
    private val firstInstance = SqlMirrorCacheLedger(database)
    private val secondInstance = SqlMirrorCacheLedger(database)

    @Test
    fun `should insert a key that a second instance already holds without an error`() {
        // given: the first instance has stored the version directory
        firstInstance.insertIfAbsent("central", "org/mirror/lib/2.0", storedAt)

        // when: the second instance stores the same version directory
        secondInstance.insertIfAbsent("central", "org/mirror/lib/2.0", storedAt.plusSeconds(1))

        // then: exactly one row exists for the key
        assertThat(firstInstance.find("central")).`as`("rows of the repository").hasSize(1)
    }

    @Test
    fun `should keep the first stored row when two instances insert the same key`() {
        // given: the first instance has stored the version directory
        firstInstance.insertIfAbsent("central", "org/mirror/lib/2.0", storedAt)

        // when: the second instance stores the same version directory a second later
        secondInstance.insertIfAbsent("central", "org/mirror/lib/2.0", storedAt.plusSeconds(1))

        // then: the row keeps the original last access
        assertThat(secondInstance.find("central")).containsEntry("org/mirror/lib/2.0", storedAt)
    }

    @Test
    fun `should keep the newest last access when an older touch arrives after a newer one`() {
        // given: a cached copy stored at the start
        firstInstance.insertIfAbsent("central", "org/mirror/lib/2.0", storedAt)

        // when: instance one records a serve at minute 30, then instance two records an earlier serve at minute 10
        firstInstance.markAccessed("central", "org/mirror/lib/2.0", storedAt.plusSeconds(30 * 60))
        secondInstance.markAccessed("central", "org/mirror/lib/2.0", storedAt.plusSeconds(10 * 60))

        // then: the last access is the newest serve, not the last write
        assertThat(firstInstance.find("central")).containsEntry("org/mirror/lib/2.0", storedAt.plusSeconds(30 * 60))
    }

    @Test
    fun `should move the last access forward when a newer touch arrives`() {
        // given: a cached copy stored at the start
        firstInstance.insertIfAbsent("central", "org/mirror/lib/2.0", storedAt)

        // when: instance two records a serve after instance one's serve
        firstInstance.markAccessed("central", "org/mirror/lib/2.0", storedAt.plusSeconds(10 * 60))
        secondInstance.markAccessed("central", "org/mirror/lib/2.0", storedAt.plusSeconds(20 * 60))

        // then: the last access is the newer serve
        assertThat(firstInstance.find("central")).containsEntry("org/mirror/lib/2.0", storedAt.plusSeconds(20 * 60))
    }

    @Test
    fun `should keep the first source host when a second host stores into the same directory`() {
        // given: the first host has stored a copy of the version directory
        firstInstance.insertIfAbsent("central", "org/mirror/lib/2.0", storedAt, "first.example.org")

        // when: a second host stores another file into the same directory
        secondInstance.insertIfAbsent("central", "org/mirror/lib/2.0", storedAt.plusSeconds(1), "second.example.org")

        // then: the directory keeps the host of the first fetch
        assertThat(secondInstance.sourceHost("central", "org/mirror/lib/2.0")).isEqualTo("first.example.org")
    }

    @Test
    fun `should return the source host of a stored directory`() {
        // given: a copy stored from a host
        firstInstance.insertIfAbsent("central", "org/mirror/lib/2.0", storedAt, "repo.example.org:8443")

        // when: the host is read back
        val host = secondInstance.sourceHost("central", "org/mirror/lib/2.0")

        // then: the host is returned as stored
        assertThat(host).isEqualTo("repo.example.org:8443")
    }

    @Test
    fun `should return no source host for a directory without a row`() {
        // when: the host of a directory that was never stored is read
        val host = firstInstance.sourceHost("central", "org/mirror/lib/2.0")

        // then: there is no host
        assertThat(host).`as`("source host of a missing row").isNull()
    }

    @Test
    fun `should return the source hosts of every directory of one repository`() {
        // given: copies from two hosts and one copy stored without a host
        firstInstance.insertIfAbsent("central", "org/mirror/lib/1.0", storedAt, "one.example.org")
        firstInstance.insertIfAbsent("central", "org/mirror/lib/2.0", storedAt, "two.example.org")
        firstInstance.insertIfAbsent("central", "org/mirror/lib/3.0", storedAt, null)
        firstInstance.insertIfAbsent("releases", "org/mirror/lib/1.0", storedAt, "other.example.org")

        // when: the hosts of the repository are read
        val hosts = secondInstance.sourceHosts("central")

        // then: each directory of that repository maps to its host, the missing host to null, and other repositories are absent
        assertThat(hosts).`as`("hosts by directory")
            .containsOnlyKeys("org/mirror/lib/1.0", "org/mirror/lib/2.0", "org/mirror/lib/3.0")
            .containsEntry("org/mirror/lib/1.0", "one.example.org")
            .containsEntry("org/mirror/lib/2.0", "two.example.org")
            .containsEntry("org/mirror/lib/3.0", null)
    }

    @Test
    fun `should read rows of the previous table shape with no source host`() {
        // given: a database that holds the table of the previous version with one row
        val legacy = freshInMemoryDatabase()
        transaction(legacy) {
            SchemaUtils.create(LegacyMirrorCacheEntryTable)
            LegacyMirrorCacheEntryTable.insert {
                it[repositoryId] = "central"
                it[entryPath] = "org/mirror/lib/2.0"
                it[cachedAt] = storedAt.toEpochMilli()
                it[lastAccessedAt] = storedAt.toEpochMilli()
            }
        }

        // when: the ledger of this version opens the same database
        val upgraded = SqlMirrorCacheLedger(legacy)

        // then: the existing row is still found and has no source host
        assertThat(upgraded.find("central")).containsEntry("org/mirror/lib/2.0", storedAt)
        assertThat(upgraded.sourceHost("central", "org/mirror/lib/2.0")).`as`("source host of an old row").isNull()
    }

    @Test
    fun `should accept rows of a previous version that do not name the source host`() {
        // given: this version has created the table with the source host column
        val database = freshInMemoryDatabase()
        SqlMirrorCacheLedger(database)

        // when: the previous version, which knows four columns, stores a copy
        transaction(database) {
            LegacyMirrorCacheEntryTable.insert {
                it[repositoryId] = "central"
                it[entryPath] = "org/mirror/lib/2.0"
                it[cachedAt] = storedAt.toEpochMilli()
                it[lastAccessedAt] = storedAt.toEpochMilli()
            }
        }

        // then: this version reads the row and finds no source host in it
        val ledger = SqlMirrorCacheLedger(database)
        assertThat(ledger.find("central")).containsEntry("org/mirror/lib/2.0", storedAt)
        assertThat(ledger.sourceHost("central", "org/mirror/lib/2.0")).`as`("source host of a row written without it").isNull()
    }

    /** The table as the previous version created it, before the source host column existed. */
    private object LegacyMirrorCacheEntryTable : Table("mirror_cache_entry") {
        val repositoryId = varchar("repository", 128)
        val entryPath = varchar("path", 512)
        val cachedAt = long("cached_at")
        val lastAccessedAt = long("last_accessed_at")

        override val primaryKey = PrimaryKey(repositoryId, entryPath)
    }

    private fun freshInMemoryDatabase(): Database {
        val name = UUID.randomUUID()
        return Database.connect(
            "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;MODE=MYSQL",
            driver = "org.h2.Driver",
        )
    }

}
