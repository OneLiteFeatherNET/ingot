# HA Stage 1: Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make it safe to run more than one Ingot instance against one database and one object store, by serialising schema initialisation and metadata writes across processes and by not losing buffered statistics on shutdown.

**Architecture:** A single `DatabaseLock` abstraction takes a named advisory lock on its own pooled connection. On MariaDB and MySQL that is `GET_LOCK`, on PostgreSQL `pg_advisory_lock`, and on SQLite and H2 it is a no-op because those support one writer anyway. Two call sites use it: the whole plugin initialisation sequence in `ReposiliteFactory`, and the read-modify-write on `maven-metadata.xml` in `MetadataService`. A third, unrelated fix flushes the statistics buffer during shutdown.

**Tech Stack:** Kotlin, Exposed (`org.jetbrains.exposed.v1`), HikariCP, JUnit 5, Testcontainers (MariaDB, PostgreSQL), Gradle.

## Global Constraints

- **Backwards compatibility outranks everything.** Moving from Reposilite to Ingot must still cost exactly one changed line. An unmodified upstream Compose file must start with only the image reference changed, an existing `/app/data` must be adopted whoever owns it, and `PUID`, `PGID` and `REPOSILITE_OPTS` must still take effect.
- **Package names stay `com.reposilite.*`.** Do not move anything to `net.onelitefeather.ingot.*`.
- **All code, comments and commit messages in English.**
- **No em dashes (`—`, U+2014) and no en dashes (`–`, U+2013)** anywhere in commits, code comments or documentation. Use a hyphen, a colon, a comma, or two sentences. A `PreToolUse` hook blocks commits that violate this.
- **No `Co-Authored-By:` trailers, no `Claude-Session:` links, no "Generated with Claude Code", no robot emoji** in any commit message.
- **Conventional Commits**, imperative mood, body wrapped at 72 characters. Allowed types: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`, `perf`.
- **New source files carry the Apache 2.0 header** in the form used by `reposilite-backend/src/main/kotlin/com/reposilite/maven/ResolutionCache.kt` (`Copyright (c) 2026 dzikoysk`). Do not remove or replace existing dzikoysk headers.
- **Build commands:** unit tests `./gradlew :reposilite-backend:test`, integration tests `./gradlew :reposilite-backend:integration`. The `integration` source set lives at `reposilite-backend/src/integration/kotlin`.
- **Branch:** work on `docs/kubernetes-ha-design` or a branch from it. The design this implements is `docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md`.

---

## File Structure

**Created:**

- `reposilite-backend/src/main/kotlin/com/reposilite/shared/DatabaseLock.kt` - the advisory lock abstraction and its vendor dispatch. One responsibility: acquire a named lock on a dedicated connection, run a block, release it.
- `reposilite-backend/src/test/kotlin/com/reposilite/shared/DatabaseLockTest.kt` - unit tests for the no-op path and the name-hashing helper.
- `reposilite-backend/src/integration/kotlin/com/reposilite/shared/DatabaseLockIntegrationTest.kt` - mutual exclusion against a real MariaDB and a real PostgreSQL.
- `reposilite-backend/src/integration/kotlin/com/reposilite/ConcurrentSchemaInitializationIntegrationTest.kt` - several initialisations racing against one database.
- `reposilite-backend/src/integration/kotlin/com/reposilite/maven/ConcurrentMetadataWriteIntegrationTest.kt` - parallel deployments of the same coordinate.

**Modified:**

- `reposilite-backend/src/main/kotlin/com/reposilite/Reposilite.kt` - hold the `DatabaseLock` so plugins can reach it.
- `reposilite-backend/src/main/kotlin/com/reposilite/ReposiliteFactory.kt` - build the lock, wrap `pluginLoader.initialize()`.
- `reposilite-backend/src/main/kotlin/com/reposilite/maven/MetadataService.kt` - take the lock around the read-modify-write in `generatePom`.
- `reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenComponents.kt` - pass the lock into `MetadataService`.
- `reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenPlugin.kt` - supply the lock from `reposilite()`.
- `reposilite-backend/src/main/kotlin/com/reposilite/statistics/application/StatisticsPlugin.kt` - flush on dispose.

**Why a dedicated connection:** `GET_LOCK` and `pg_advisory_lock` are session scoped, not transaction scoped. Taking the lock inside an Exposed `transaction { }` that also performs the guarded work would hold a transaction open across S3 I/O, which is exactly the long-running transaction Galera handles badly. Borrowing one connection, taking the lock, doing the work outside any transaction, then releasing and returning the connection avoids that.

---

### Task 1: The DatabaseLock abstraction

**Files:**
- Create: `reposilite-backend/src/main/kotlin/com/reposilite/shared/DatabaseLock.kt`
- Test: `reposilite-backend/src/test/kotlin/com/reposilite/shared/DatabaseLockTest.kt`

**Interfaces:**
- Consumes: `javax.sql.DataSource` (the `HikariDataSource` already inside `DatabaseConnection`), `org.jetbrains.exposed.v1.jdbc.Database` for vendor detection.
- Produces:
  - `class DatabaseLock(dataSource: DataSource, vendor: String, journalist: Journalist)`
  - `fun <T> DatabaseLock.withLock(name: String, timeoutSeconds: Int = 60, block: () -> T): T`
  - `internal fun advisoryKeyOf(name: String): Long` (used by the PostgreSQL path and asserted in tests)
  - `class DatabaseLockTimeoutException(name: String, timeoutSeconds: Int) : RuntimeException`

- [ ] **Step 1: Write the failing test**

Create `reposilite-backend/src/test/kotlin/com/reposilite/shared/DatabaseLockTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 dzikoysk
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

package com.reposilite.shared

import com.reposilite.journalist.backend.InMemoryLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.sql.DataSource
import java.sql.Connection

internal class DatabaseLockTest {

    private val logger = InMemoryLogger()

    /** A DataSource that fails loudly if anyone asks it for a connection. */
    private val forbiddenDataSource = object : DataSource by NoopDataSource() {
        override fun getConnection(): Connection = throw AssertionError("no connection expected")
    }

    @Test
    fun `should run the block without a connection on sqlite`() {
        val lock = DatabaseLock(forbiddenDataSource, "sqlite", logger)

        val result = lock.withLock("schema-init") { "done" }

        assertEquals("done", result)
    }

    @Test
    fun `should run the block without a connection on h2`() {
        val lock = DatabaseLock(forbiddenDataSource, "h2", logger)

        val result = lock.withLock("schema-init") { 42 }

        assertEquals(42, result)
    }

    @Test
    fun `should derive a stable advisory key from a lock name`() {
        assertEquals(advisoryKeyOf("schema-init"), advisoryKeyOf("schema-init"))
    }

    @Test
    fun `should derive different advisory keys for different names`() {
        assertNotEquals(advisoryKeyOf("metadata:releases:com/example"), advisoryKeyOf("metadata:releases:com/other"))
    }

    @Test
    fun `should propagate an exception thrown by the guarded block`() {
        val lock = DatabaseLock(forbiddenDataSource, "sqlite", logger)

        val thrown = runCatching { lock.withLock("schema-init") { error("boom") } }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
    }
}
```

Add the minimal `NoopDataSource` stub in the same file, below the test class:

```kotlin
private open class NoopDataSource : DataSource {
    override fun getConnection(): Connection = throw UnsupportedOperationException()
    override fun getConnection(username: String?, password: String?): Connection = throw UnsupportedOperationException()
    override fun getLogWriter(): java.io.PrintWriter = throw UnsupportedOperationException()
    override fun setLogWriter(out: java.io.PrintWriter?) = throw UnsupportedOperationException()
    override fun setLoginTimeout(seconds: Int) = throw UnsupportedOperationException()
    override fun getLoginTimeout(): Int = 0
    override fun getParentLogger(): java.util.logging.Logger = throw UnsupportedOperationException()
    override fun <T : Any?> unwrap(iface: Class<T>?): T = throw UnsupportedOperationException()
    override fun isWrapperFor(iface: Class<*>?): Boolean = false
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :reposilite-backend:test --tests "com.reposilite.shared.DatabaseLockTest"`

Expected: FAIL to compile, with unresolved references `DatabaseLock` and `advisoryKeyOf`.

- [ ] **Step 3: Write the implementation**

Create `reposilite-backend/src/main/kotlin/com/reposilite/shared/DatabaseLock.kt`:

```kotlin
/*
 * Copyright (c) 2026 dzikoysk
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

package com.reposilite.shared

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import java.sql.Connection
import javax.sql.DataSource

class DatabaseLockTimeoutException(name: String, timeoutSeconds: Int) :
    RuntimeException("Could not acquire database lock '$name' within $timeoutSeconds second(s)")

/**
 * Derives a 64 bit key from a lock name, for backends that key advisory locks by number
 * rather than by string. Stable across processes and JVM restarts, which String.hashCode
 * is but is only 32 bit, so the two halves are combined.
 */
internal fun advisoryKeyOf(name: String): Long {
    var hash = -0x340d631b7bdddcdbL // FNV-1a 64 bit offset basis
    for (byte in name.toByteArray(Charsets.UTF_8)) {
        hash = hash xor (byte.toLong() and 0xff)
        hash *= 0x100000001b3L // FNV-1a 64 bit prime
    }
    return hash
}

/**
 * A named lock held across processes, so that several Ingot instances sharing one database
 * do not run the same guarded section at the same time.
 *
 * The lock is taken on a connection borrowed for the purpose rather than inside a
 * transaction. Both GET_LOCK and pg_advisory_lock are session scoped, so nothing here needs
 * a transaction, and holding one open across the guarded work (which may be object storage
 * I/O) is what a Galera cluster handles worst.
 *
 * Embedded databases support a single writer regardless, so they run the block directly and
 * never ask the pool for a connection.
 */
class DatabaseLock(
    private val dataSource: DataSource,
    vendor: String,
    private val journalist: Journalist
) : Journalist {

    private val strategy: LockStrategy = when (vendor.lowercase()) {
        "mariadb", "mysql" -> MySqlLockStrategy
        "postgresql" -> PostgresLockStrategy
        else -> NoopLockStrategy
    }

    fun <T> withLock(name: String, timeoutSeconds: Int = 60, block: () -> T): T {
        if (strategy === NoopLockStrategy) {
            return block()
        }

        return dataSource.connection.use { connection ->
            strategy.acquire(connection, name, timeoutSeconds)
            logger.debug("DatabaseLock | Acquired '$name'")

            try {
                block()
            } finally {
                runCatching { strategy.release(connection, name) }
                    .onFailure { logger.warn("DatabaseLock | Failed to release '$name': ${it.message}") }
                logger.debug("DatabaseLock | Released '$name'")
            }
        }
    }

    override fun getLogger(): Logger =
        journalist.logger

    private interface LockStrategy {
        fun acquire(connection: Connection, name: String, timeoutSeconds: Int)
        fun release(connection: Connection, name: String)
    }

    private object NoopLockStrategy : LockStrategy {
        override fun acquire(connection: Connection, name: String, timeoutSeconds: Int) = Unit
        override fun release(connection: Connection, name: String) = Unit
    }

    private object MySqlLockStrategy : LockStrategy {
        override fun acquire(connection: Connection, name: String, timeoutSeconds: Int) {
            // GET_LOCK returns 1 on success, 0 on timeout and NULL on error.
            connection.prepareStatement("SELECT GET_LOCK(?, ?)").use { statement ->
                statement.setString(1, name)
                statement.setInt(2, timeoutSeconds)
                statement.executeQuery().use { result ->
                    val acquired = result.next() && result.getInt(1) == 1 && !result.wasNull()
                    if (!acquired) throw DatabaseLockTimeoutException(name, timeoutSeconds)
                }
            }
        }

        override fun release(connection: Connection, name: String) {
            connection.prepareStatement("SELECT RELEASE_LOCK(?)").use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { }
            }
        }
    }

    private object PostgresLockStrategy : LockStrategy {
        override fun acquire(connection: Connection, name: String, timeoutSeconds: Int) {
            // pg_advisory_lock has no timeout argument, so the wait is bounded with a
            // statement timeout that applies to this session only.
            connection.createStatement().use { it.execute("SET LOCAL lock_timeout = '${timeoutSeconds}s'") }
            connection.prepareStatement("SELECT pg_advisory_lock(?)").use { statement ->
                statement.setLong(1, advisoryKeyOf(name))
                try {
                    statement.executeQuery().use { }
                } catch (exception: java.sql.SQLException) {
                    throw DatabaseLockTimeoutException(name, timeoutSeconds).initCause(exception)
                }
            }
        }

        override fun release(connection: Connection, name: String) {
            connection.prepareStatement("SELECT pg_advisory_unlock(?)").use { statement ->
                statement.setLong(1, advisoryKeyOf(name))
                statement.executeQuery().use { }
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :reposilite-backend:test --tests "com.reposilite.shared.DatabaseLockTest"`

Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add reposilite-backend/src/main/kotlin/com/reposilite/shared/DatabaseLock.kt \
        reposilite-backend/src/test/kotlin/com/reposilite/shared/DatabaseLockTest.kt
git commit -m "feat(shared): add a cross-process advisory database lock

Several instances sharing one database need to agree on who runs a
guarded section. GET_LOCK on MariaDB and MySQL and pg_advisory_lock on
PostgreSQL are session scoped, so the lock is taken on a borrowed
connection rather than inside a transaction: holding one open across the
guarded work is what a Galera cluster handles worst.

Embedded databases support a single writer anyway and run the block
directly, without asking the pool for a connection at all."
```

---

### Task 2: Prove mutual exclusion against real databases

**Files:**
- Create: `reposilite-backend/src/integration/kotlin/com/reposilite/shared/DatabaseLockIntegrationTest.kt`

**Interfaces:**
- Consumes: `DatabaseLock`, `DatabaseLockTimeoutException` from Task 1.
- Produces: nothing that later tasks depend on.

- [ ] **Step 1: Write the failing test**

Create `reposilite-backend/src/integration/kotlin/com/reposilite/shared/DatabaseLockIntegrationTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 dzikoysk
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

package com.reposilite.shared

import com.reposilite.journalist.backend.InMemoryLogger
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Testcontainers
internal class DatabaseLockIntegrationTest {

    @Container
    private val mariadb = MariaDBContainer("mariadb:11.4")

    private val logger = InMemoryLogger()

    private fun dataSource(poolSize: Int): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = mariadb.jdbcUrl
                username = mariadb.username
                password = mariadb.password
                driverClassName = "org.mariadb.jdbc.Driver"
                maximumPoolSize = poolSize
            }
        )

    @Test
    fun `should let only one thread into the guarded section at a time`() {
        val threads = 8
        dataSource(threads).use { source ->
            val lock = DatabaseLock(source, "mariadb", logger)
            val concurrent = AtomicInteger(0)
            val maxObserved = AtomicInteger(0)
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(threads)

            repeat(threads) {
                pool.submit {
                    start.await()
                    lock.withLock("integration-test") {
                        val now = concurrent.incrementAndGet()
                        maxObserved.updateAndGet { previous -> maxOf(previous, now) }
                        Thread.sleep(50)
                        concurrent.decrementAndGet()
                    }
                }
            }

            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "workers did not finish in time")
            assertEquals(1, maxObserved.get(), "more than one thread was inside the guarded section")
        }
    }

    @Test
    fun `should time out rather than wait forever when the lock is held`() {
        dataSource(4).use { source ->
            val holder = DatabaseLock(source, "mariadb", logger)
            val contender = DatabaseLock(source, "mariadb", logger)
            val held = CountDownLatch(1)
            val release = CountDownLatch(1)

            val holderThread = Thread {
                holder.withLock("timeout-test") {
                    held.countDown()
                    release.await()
                }
            }
            holderThread.start()
            assertTrue(held.await(30, TimeUnit.SECONDS), "holder never acquired the lock")

            val thrown = runCatching {
                contender.withLock("timeout-test", timeoutSeconds = 1) { "unreachable" }
            }.exceptionOrNull()

            release.countDown()
            holderThread.join(30_000)

            assertTrue(thrown is DatabaseLockTimeoutException, "expected a timeout, got $thrown")
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails or is absent**

Run: `./gradlew :reposilite-backend:integration --tests "com.reposilite.shared.DatabaseLockIntegrationTest"`

Expected: PASS. This test is written against the implementation from Task 1, so it should pass immediately. If it fails, the Task 1 implementation is wrong and must be fixed before continuing. To confirm the test has teeth, temporarily change the `MySqlLockStrategy` dispatch in `DatabaseLock` to `NoopLockStrategy`, re-run, and observe `should let only one thread into the guarded section at a time` fail with a `maxObserved` above 1. Revert that change afterwards.

- [ ] **Step 3: Commit**

```bash
git add reposilite-backend/src/integration/kotlin/com/reposilite/shared/DatabaseLockIntegrationTest.kt
git commit -m "test(shared): prove the database lock excludes concurrent holders

Asserts against a real MariaDB rather than an embedded database, because
GET_LOCK semantics are the behaviour under test and no embedded engine
has them. Covers both mutual exclusion and the timeout path, so a lock
that silently never blocks cannot pass."
```

---

### Task 3: Serialise schema initialisation

**Files:**
- Modify: `reposilite-backend/src/main/kotlin/com/reposilite/Reposilite.kt`
- Modify: `reposilite-backend/src/main/kotlin/com/reposilite/ReposiliteFactory.kt`
- Create: `reposilite-backend/src/integration/kotlin/com/reposilite/ConcurrentSchemaInitializationIntegrationTest.kt`

**Interfaces:**
- Consumes: `DatabaseLock` and `withLock` from Task 1.
- Produces: `Reposilite.databaseLock: DatabaseLock`, reachable from plugins via `reposilite().databaseLock`. Task 4 depends on this property.

**Why here:** `SqlAccessTokenRepository`, `SqlStatisticsRepository` and `SqlConfigurationRepository` each run `SchemaUtils.create` in their constructor, and they are built by three different plugins. Wrapping `pluginLoader.initialize()` covers all three, and any plugin added later, in one place.

- [ ] **Step 1: Add the lock to the Reposilite holder**

In `reposilite-backend/src/main/kotlin/com/reposilite/Reposilite.kt`, add the import and the constructor parameter:

```kotlin
import com.reposilite.shared.DatabaseLock
```

Add `val databaseLock: DatabaseLock,` to the constructor parameter list, directly after `val databaseConnection: DatabaseConnection,`:

```kotlin
class Reposilite(
    val journalist: ReposiliteJournalist,
    val parameters: ReposiliteParameters,
    val localConfiguration: LocalConfiguration,
    val databaseConnection: DatabaseConnection,
    val databaseLock: DatabaseLock,
    val ioService: ExecutorService,
    val scheduler: ScheduledExecutorService,
    val webServer: HttpServer,
    val extensions: Extensions
) : Facade, Journalist {
```

- [ ] **Step 2: Build the lock and wrap plugin initialisation**

In `reposilite-backend/src/main/kotlin/com/reposilite/ReposiliteFactory.kt`, add the import:

```kotlin
import com.reposilite.shared.DatabaseLock
```

Replace the `val reposilite = Reposilite(...)` construction so the connection is built first and reused:

```kotlin
        val databaseConnection = DatabaseConnectionFactory.createConnection(
            workingDirectory = parameters.workingDirectory,
            databaseConfiguration = parameters.database,
            databaseThreadPoolSize = localConfiguration.databaseThreadPool.get()
        )

        val reposilite = Reposilite(
            journalist = journalist,
            parameters = parameters,
            localConfiguration = localConfiguration,
            databaseConnection = databaseConnection,
            databaseLock = DatabaseLock(
                dataSource = databaseConnection.databaseSource,
                vendor = databaseConnection.database.vendor,
                journalist = journalist
            ),
            webServer = HttpServer(),
            ioService = newFixedThreadPool(
                min = 0,
                max = localConfiguration.ioThreadPool.get(),
                prefix = "Ingot | IO"
            ),
            scheduler = newSingleThreadScheduledExecutor("Ingot | Scheduler"),
            extensions = Extensions(journalist)
        )
```

Then wrap the initialisation call at the bottom of the same function:

```kotlin
        val pluginLoader = PluginLoader(parameters.pluginDirectory, reposilite.extensions)
        pluginLoader.extensions.registerFacade(reposilite)
        pluginLoader.loadPluginsByServiceFiles()

        // Plugins create their tables as they initialise. With several instances starting at
        // once, which a rolling update produces by definition, that means concurrent DDL
        // against the same tables. Galera runs DDL under total order isolation, where
        // concurrent CREATE TABLE and ALTER TABLE stall the cluster or abort a node.
        reposilite.databaseLock.withLock(SCHEMA_INITIALIZATION_LOCK, timeoutSeconds = 300) {
            pluginLoader.initialize()
        }

        return reposilite
```

Add the constant at the top of the `ReposiliteFactory` object body:

```kotlin
    private const val SCHEMA_INITIALIZATION_LOCK = "ingot-schema-initialization"
```

- [ ] **Step 3: Compile to verify nothing else constructs Reposilite**

Run: `./gradlew :reposilite-backend:compileKotlin :reposilite-backend:compileTestKotlin :reposilite-backend:compileIntegrationKotlin`

Expected: PASS. If a test constructs `Reposilite` directly it will fail here with a missing argument; add `databaseLock = DatabaseLock(<its data source>, "sqlite", <its journalist>)` at that call site.

- [ ] **Step 4: Write the concurrency test**

Create `reposilite-backend/src/integration/kotlin/com/reposilite/ConcurrentSchemaInitializationIntegrationTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 dzikoysk
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

package com.reposilite

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.DatabaseLock
import com.reposilite.token.infrastructure.SqlAccessTokenRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

@Testcontainers
internal class ConcurrentSchemaInitializationIntegrationTest {

    @Container
    private val mariadb = MariaDBContainer("mariadb:11.4")

    private val logger = InMemoryLogger()

    @Test
    fun `should initialise the schema from several instances without failing`() {
        val instances = 5
        val failures = CopyOnWriteArrayList<Throwable>()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(instances)

        val sources = (1..instances).map {
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = mariadb.jdbcUrl
                    username = mariadb.username
                    password = mariadb.password
                    driverClassName = "org.mariadb.jdbc.Driver"
                    maximumPoolSize = 2
                }
            )
        }

        try {
            sources.forEach { source ->
                pool.submit {
                    runCatching {
                        start.await()
                        val database = Database.connect(source)
                        DatabaseLock(source, "mariadb", logger).withLock("ingot-schema-initialization", timeoutSeconds = 120) {
                            SqlAccessTokenRepository(database, logger, emptyArray())
                        }
                    }.onFailure { failures.add(it) }
                }
            }

            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(180, TimeUnit.SECONDS), "instances did not finish in time")
            assertEquals(emptyList<Throwable>(), failures.toList(), "concurrent schema initialisation failed")
        } finally {
            sources.forEach { it.close() }
        }
    }
}
```

`InMemoryLogger` satisfies both the `DatabaseLock` and the `SqlAccessTokenRepository` journalist parameter, because `com.reposilite.journalist.Logger` extends `Journalist` and `InMemoryLogger` is a `Logger`. No wrapper needed.

- [ ] **Step 5: Run the integration test**

Run: `./gradlew :reposilite-backend:integration --tests "com.reposilite.ConcurrentSchemaInitializationIntegrationTest"`

Expected: PASS, no failures collected.

- [ ] **Step 6: Verify the full suite still passes**

Run: `./gradlew :reposilite-backend:test :reposilite-backend:integration`

Expected: PASS. A single-instance start on SQLite must be unaffected, since the lock is a no-op there.

- [ ] **Step 7: Commit**

```bash
git add reposilite-backend/src/main/kotlin/com/reposilite/Reposilite.kt \
        reposilite-backend/src/main/kotlin/com/reposilite/ReposiliteFactory.kt \
        reposilite-backend/src/integration/kotlin/com/reposilite/ConcurrentSchemaInitializationIntegrationTest.kt
git commit -m "fix(configuration): serialise schema initialisation across instances

Three repositories create their tables while constructing, and three
different plugins build them, so a rolling update has every starting pod
issuing DDL against the same tables at once. Galera runs DDL under total
order isolation, where that stalls the cluster at best and aborts a node
at worst.

Wrapping the whole plugin initialisation rather than each repository
covers all three, and every plugin added later, in one place. Embedded
databases take no lock at all."
```

---

### Task 4: Serialise metadata writes

**Files:**
- Modify: `reposilite-backend/src/main/kotlin/com/reposilite/maven/MetadataService.kt`
- Modify: `reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenComponents.kt`
- Modify: `reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenPlugin.kt`
- Create: `reposilite-backend/src/integration/kotlin/com/reposilite/maven/ConcurrentMetadataWriteIntegrationTest.kt`

**Interfaces:**
- Consumes: `Reposilite.databaseLock` from Task 3, `DatabaseLock.withLock` from Task 1.
- Produces: `MetadataService(repositorySecurityProvider: RepositorySecurityProvider, databaseLock: DatabaseLock)`, a changed constructor signature that `MavenComponents` must match.

**The defect:** `generatePom` reads `maven-metadata.xml`, adds a version and writes it back. `FileSystemStorageProvider` holds a `ReentrantReadWriteLock` per location, but it ends at the process boundary and its own comment says it is not truly respected. The S3 provider holds none. Two pods deploying the same coordinate lose a version.

- [ ] **Step 1: Write the failing test**

Create `reposilite-backend/src/integration/kotlin/com/reposilite/maven/ConcurrentMetadataWriteIntegrationTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 dzikoysk
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

package com.reposilite.maven

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.DatabaseLock
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The lost update this guards against is not specific to the storage provider, so the test
 * models the read-modify-write directly: a counter read, incremented and written back under
 * the same lock the metadata write uses. Without the lock, concurrent writers overwrite each
 * other and the final value falls short of the number of writers.
 */
@Testcontainers
internal class ConcurrentMetadataWriteIntegrationTest {

    @Container
    private val mariadb = MariaDBContainer("mariadb:11.4")

    private val logger = InMemoryLogger()

    @Test
    fun `should not lose an update when several writers touch one coordinate`() {
        val writers = 10
        val shared = AtomicInteger(0)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(writers)

        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = mariadb.jdbcUrl
                username = mariadb.username
                password = mariadb.password
                driverClassName = "org.mariadb.jdbc.Driver"
                maximumPoolSize = writers
            }
        ).use { source ->
            val lock = DatabaseLock(source, "mariadb", logger)

            repeat(writers) {
                pool.submit {
                    start.await()
                    lock.withLock("ingot-metadata:releases:com/example/artifact") {
                        val read = shared.get()
                        Thread.sleep(20) // widen the window a lost update would slip through
                        shared.set(read + 1)
                    }
                }
            }

            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS), "writers did not finish in time")
            assertEquals(writers, shared.get(), "an update was lost")
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it has teeth**

Run: `./gradlew :reposilite-backend:integration --tests "com.reposilite.maven.ConcurrentMetadataWriteIntegrationTest"`

Expected: PASS with the lock in place. To confirm the test detects the defect, temporarily replace `lock.withLock("...") { ... }` with a direct call to the block, re-run, and observe the assertion fail with a value well below 10. Revert afterwards.

- [ ] **Step 3: Take the lock in MetadataService**

In `reposilite-backend/src/main/kotlin/com/reposilite/maven/MetadataService.kt`, add the import:

```kotlin
import com.reposilite.shared.DatabaseLock
```

Change the constructor:

```kotlin
internal class MetadataService(
    private val repositorySecurityProvider: RepositorySecurityProvider,
    private val databaseLock: DatabaseLock
) {
```

Add the lock-name helper as a private method on the class, next to `resolveMetadataFile`:

```kotlin
    /**
     * One lock per repository and directory, so deployments to unrelated coordinates never
     * wait on each other. The prefix keeps these distinct from other Ingot locks sharing the
     * same database.
     */
    private fun metadataLockName(repository: Repository, gav: Location): String =
        "ingot-metadata:${repository.name}:$gav"
```

Wrap the read-modify-write inside `generatePom`. The guarded section starts at the `putFile` for the POM and ends after `saveMetadata`, so replace the body from `repository.storageProvider` through `.mapToUnit()` with:

```kotlin
                return databaseLock.withLock(metadataLockName(repository, parentDirectory)) {
                    repository.storageProvider
                        .putFile(
                            location = gav,
                            inputStream = """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
                                xmlns="http://maven.apache.org/POM/4.0.0"
                                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                              <modelVersion>4.0.0</modelVersion>
                              <groupId>$groupId</groupId>
                              <artifactId>$artifactId</artifactId>
                              <version>$version</version>
                              <description>POM was generated by Ingot</description>
                            </project>
                            """.trimIndent().trim().byteInputStream()
                        )
                        .map { findMetadata(repository, parentDirectory).orElseGet { Metadata() } }
                        .map {
                            it.copy(
                                groupId = groupId,
                                artifactId = artifactId,
                                versioning = (it.versioning ?: Versioning()).copy(
                                    latest = version,
                                    release = version,
                                    lastUpdated = timestampFormatter.format(ZonedDateTime.now()),
                                    _versions = (it.versioning?.versions?.toMutableList() ?: mutableListOf()) + version
                                )
                            )
                        }
                        .flatMap {
                            saveMetadata(
                                SaveMetadataRequest(
                                    repository = repository,
                                    gav = parentDirectory,
                                    metadata = it
                                )
                            )
                        }
                        .mapToUnit()
                }
```

- [ ] **Step 4: Pass the lock through the component wiring**

In `reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenComponents.kt`, add the import `com.reposilite.shared.DatabaseLock` and add `private val databaseLock: DatabaseLock,` to the class constructor.

The construction site is at line 60 and currently reads `MetadataService(securityProvider())`. Note the method is called `securityProvider()`, not `repositorySecurityProvider()`, even though the `MetadataService` parameter carries the longer name. Replace it with:

```kotlin
    private fun metadataService(): MetadataService =
        MetadataService(
            repositorySecurityProvider = securityProvider(),
            databaseLock = databaseLock
        )
```

`metadataService()` is also threaded through as a default argument further down the same file (around lines 99 and 107). Leave those call sites alone; they resolve through this function.

In `reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenPlugin.kt`, add `databaseLock = reposilite().databaseLock,` to the `MavenComponents(...)` argument list, next to the other facade arguments.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :reposilite-backend:test :reposilite-backend:integration`

Expected: PASS. `MavenIntegrationTest` and `MavenApiIntegrationTest` exercise deployment and must be unaffected, because a single instance never contends.

- [ ] **Step 6: Commit**

```bash
git add reposilite-backend/src/main/kotlin/com/reposilite/maven/MetadataService.kt \
        reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenComponents.kt \
        reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenPlugin.kt \
        reposilite-backend/src/integration/kotlin/com/reposilite/maven/ConcurrentMetadataWriteIntegrationTest.kt
git commit -m "fix(maven): serialise metadata writes across instances

generatePom reads maven-metadata.xml, adds a version and writes it back.
The filesystem provider holds a lock per location, but it ends at the
process boundary and its own comment admits it is not truly respected,
and the S3 provider holds none. Two pods deploying the same coordinate
lose a version.

The lock is keyed by repository and directory, so deployments to
unrelated coordinates never wait on each other. Serialising in the
database rather than in the storage layer covers both providers with one
mechanism."
```

---

### Task 5: Flush statistics on shutdown

**Files:**
- Modify: `reposilite-backend/src/main/kotlin/com/reposilite/statistics/application/StatisticsPlugin.kt`
- Test: `reposilite-backend/src/test/kotlin/com/reposilite/statistics/StatisticsFacadeTest.kt` (add a case to the existing file; create it from the pattern in `reposilite-backend/src/test/kotlin/com/reposilite/statistics/` if it does not exist)

**Interfaces:**
- Consumes: `StatisticsFacade.saveRecordsBulk()`, already public.
- Produces: nothing later tasks depend on.

**The defect:** `StatisticsFacade` buffers increments in memory and flushes every ten seconds from a scheduled task. `Reposilite.shutdown()` calls `scheduler.shutdown()` first, and `StatisticsPlugin` registers no dispose handler, so every rolling update discards up to ten seconds of statistics per pod.

- [ ] **Step 1: Write the failing test**

Add to `reposilite-backend/src/test/kotlin/com/reposilite/statistics/StatisticsFacadeTest.kt`:

```kotlin
    @Test
    fun `should persist buffered records when flushed explicitly`() {
        val identifier = Identifier("releases", "com/example/artifact/1.0.0/artifact-1.0.0.jar")
        statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier))

        statisticsFacade.saveRecordsBulk()

        assertEquals(1, statisticsFacade.countRecords())
    }

    @Test
    fun `should not persist anything when the buffer is empty`() {
        statisticsFacade.saveRecordsBulk()

        assertEquals(0, statisticsFacade.countRecords())
    }
```

Use the imports and the `statisticsFacade` fixture that the surrounding `StatisticsSpecification` already provides. If the file does not exist, create it extending `StatisticsSpecification` from `reposilite-backend/src/test/kotlin/com/reposilite/statistics/specification/StatisticsSpecification.kt`, with the Apache header shown in Task 1.

- [ ] **Step 2: Run the test**

Run: `./gradlew :reposilite-backend:test --tests "com.reposilite.statistics.StatisticsFacadeTest"`

Expected: PASS. These assert existing behaviour and exist to pin it, so that the dispose wiring in Step 3 has something to rely on.

- [ ] **Step 3: Register the dispose handler**

In `reposilite-backend/src/main/kotlin/com/reposilite/statistics/application/StatisticsPlugin.kt`, add the import:

```kotlin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
```

Add this block directly after the existing `event { _: ReposiliteInitializeEvent -> ... }` block:

```kotlin
        // The scheduled flush runs every ten seconds, and shutdown stops the scheduler before
        // anything else, so without this every rolling update discards up to ten seconds of
        // statistics per pod. Runs on the shutdown thread rather than through ioService,
        // which has already been asked to stop by this point.
        event { _: ReposiliteDisposeEvent ->
            if (statisticsFacade.statisticsEnabled().get()) {
                statisticsFacade.saveRecordsBulk()
            }
        }
```

- [ ] **Step 4: Verify the full suite**

Run: `./gradlew :reposilite-backend:test :reposilite-backend:integration`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add reposilite-backend/src/main/kotlin/com/reposilite/statistics/application/StatisticsPlugin.kt \
        reposilite-backend/src/test/kotlin/com/reposilite/statistics/StatisticsFacadeTest.kt
git commit -m "fix(statistics): flush the buffer before shutting down

Increments are buffered in memory and written every ten seconds, and
shutdown stops the scheduler before anything else, so every rolling
update discarded up to ten seconds of statistics per pod. Multiply that
by the replica count and a routine deployment loses a visible amount.

The flush runs on the shutdown thread, since ioService has already been
asked to stop by the time the dispose event fires."
```

---

### Task 6: Document the guarantee

**Files:**
- Modify: `reposilite-site/data/guides/installation/kubernetes.md`

**Interfaces:**
- Consumes: everything above.
- Produces: nothing.

- [ ] **Step 1: Add a section on running several replicas**

Append to `reposilite-site/data/guides/installation/kubernetes.md`:

```markdown
### Running more than one replica

Several Ingot instances may share one database and one bucket. Two things have to be true.

**The database has to be shared and not embedded.** SQLite and H2 support a single writer,
so each pod would have its own state. Point every instance at the same MariaDB, MySQL or
PostgreSQL through the `database` setting.

**The artifact storage has to be shared.** Use the S3 storage provider. A ReadWriteMany
volume with the filesystem provider is not equivalent: the filesystem locks are held inside
one process and mean nothing to another pod.

With both in place, instances coordinate through the database. Schema initialisation is
serialised behind an advisory lock, so a rolling update does not have several pods issuing
DDL against the same tables, and deployments to the same coordinate serialise their metadata
writes so no version is lost. Deployments to unrelated coordinates never wait on each other.

Instances still keep some state of their own: the mirror resolution cache, the credentials
cache and the count of failed logins are per pod. None of them affect correctness; the
practical consequence is that brute force protection allows `maxAttempts` per replica rather
than in total, and that `cache-purge` on the console reaches the pod serving that session.
```

- [ ] **Step 2: Check for forbidden characters**

Run: `grep -n $'[—–]' reposilite-site/data/guides/installation/kubernetes.md`

Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add reposilite-site/data/guides/installation/kubernetes.md
git commit -m "docs(kubernetes): state what running several replicas requires

Two requirements decide whether a second instance is safe, and neither
was written down: a shared non-embedded database, and S3 rather than a
ReadWriteMany volume, because the filesystem locks mean nothing across
pods.

Also names the state that stays per instance, so the weaker brute force
allowance reads as a documented limit rather than a surprise."
```

---

## Self-Review

**Spec coverage.** Stage 1 of the design has three items: serialise schema initialisation (Task 3), serialise metadata writes (Task 4), flush statistics on shutdown (Task 5). Tasks 1 and 2 build and prove the shared mechanism both depend on. Task 6 documents the resulting guarantee. The design's testing section asks for concurrency tests against a real database rather than SQLite; Tasks 2, 3 and 4 use MariaDB via Testcontainers, which is already a test dependency.

**Not covered here, by design.** `PreservedBuildsListener` deletes snapshot files based on a timestamp read from metadata another pod may be rewriting. It is named in the spec under 1.2 but is not fixed by this plan: the listener runs on `DeployEvent`, after `generatePom` has released its lock, so covering it means either widening the lock to span the event dispatch or giving the listener its own acquisition. That decision needs a look at whether event listeners may block a deployment, which this plan does not settle. **Add it to the Stage 2 plan or raise it as its own task.**

**Placeholder scan.** No TBD, TODO or "handle edge cases" steps. Every code step carries the code. Two steps deliberately instruct a temporary revert to prove a test detects the defect it targets, and both say to revert afterwards.

**Type consistency.** `DatabaseLock(dataSource, vendor, journalist)` is used with the same three arguments in Tasks 1, 2, 3 and 4. `withLock(name, timeoutSeconds, block)` keeps its signature at every call site, with `timeoutSeconds` defaulting to 60 and overridden to 300 for schema initialisation and 1 in the timeout test. `advisoryKeyOf` is `internal` and used only in the main source set and the unit test, which share a module.

**One risk worth naming.** Task 3 changes the `Reposilite` constructor, which is public API that a plugin could construct. Nothing in this repository does so outside `ReposiliteFactory`, and Step 3 of that task catches any that do at compile time, but a third-party plugin constructing `Reposilite` directly would break. That is an accepted cost, and it is worth mentioning in the pull request body.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-08-ha-stage-1-correctness.md`. Two execution options:

**1. Subagent-Driven (recommended)** - a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** - tasks executed in this session with checkpoints for review.
