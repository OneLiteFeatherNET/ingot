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
package net.onelitefeather.ingot.schema

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors

/**
 * SQLite and H2 have a single writer, so the schema lock is a no-op there and the lock table is never created. Every test
 * opens its own in-memory database, so no test sees the state of another.
 */
internal class EmbeddedSchemaLockTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["h2", "sqlite"])
    fun `takes no lock on an embedded database`(engine: String) {
        withEmbeddedRequest(engine) { request ->
            // when: the schema lock is requested
            val lock = SchemaLockFactory.DEFAULT.acquire(request)

            // then: the lock is the no-op lock
            assertThat(lock).`as`("lock on $engine").isSameAs(SchemaLock.NONE)
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["h2", "sqlite"])
    fun `creates no ingot_lock table on an embedded database`(engine: String) {
        withEmbeddedRequest(engine) { request ->
            // when: the schema lock is requested
            SchemaLockFactory.DEFAULT.acquire(request)

            // then: the lock table does not exist
            val tables = transaction(request.database) { SchemaUtils.listTables() }
            assertThat(tables.map { it.substringAfterLast('.').lowercase() })
                .`as`("tables on $engine")
                .doesNotContain("ingot_lock")
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["h2", "sqlite"])
    fun `releases the no-op lock any number of times on an embedded database`(engine: String) {
        withEmbeddedRequest(engine) { request ->
            // given: the no-op lock is held
            val lock = SchemaLockFactory.DEFAULT.acquire(request)

            // when / then: releasing it twice does not fail
            lock.release()
            lock.release()
        }
    }

    private fun withEmbeddedRequest(engine: String, block: (SchemaLockRequest) -> Unit) {
        val (url, driver) = when (engine) {
            "h2" -> "jdbc:h2:mem:${UUID.randomUUID()};MODE=MYSQL;DB_CLOSE_DELAY=-1" to "org.h2.Driver"
            "sqlite" -> "jdbc:sqlite::memory:" to "org.sqlite.JDBC"
            else -> error("unknown embedded engine: $engine")
        }
        val database = Database.connect(url = url, driver = driver)
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            block(
                SchemaLockRequest(
                    database = database,
                    jdbcUrl = url,
                    username = null,
                    password = null,
                    timeout = Duration.ofSeconds(1),
                    clock = Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC),
                    scheduler = scheduler,
                )
            )
        } finally {
            scheduler.shutdownNow()
        }
    }

}
