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
import org.jetbrains.exposed.v1.jdbc.Database
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

    private fun freshInMemoryDatabase(): Database {
        val name = UUID.randomUUID()
        return Database.connect(
            "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;MODE=MYSQL",
            driver = "org.h2.Driver",
        )
    }

}
