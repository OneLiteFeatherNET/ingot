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

package com.reposilite.scheduling

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

private const val BOUND_SECONDS = 10L

/**
 * A private in-memory SQLite database for one test. Every instance that a test creates against [database] shares it,
 * which stands in for the database that several Ingot instances share. A single pooled connection keeps the database
 * alive between transactions, and the data source is closed with [close].
 */
internal class SqliteTestDatabase : AutoCloseable {

    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite::memory:"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 1
            minimumIdle = 1
        }
    )

    val database: Database = Database.connect(dataSource)

    override fun close() = dataSource.close()

}

/**
 * Runs every task on its own thread and releases them together, so that they hit the shared state at the same
 * instant. Every wait is bounded, so a stuck task fails the test instead of hanging it.
 */
internal fun <T> runAtOnce(tasks: List<() -> T>): List<T> {
    val executor = Executors.newFixedThreadPool(tasks.size)
    try {
        val gate = CountDownLatch(1)
        val futures = tasks.map { task ->
            executor.submit(Callable {
                check(gate.await(BOUND_SECONDS, SECONDS)) { "the release gate was not opened in time" }
                task()
            })
        }
        gate.countDown()
        return futures.map { it.get(BOUND_SECONDS, SECONDS) }
    } finally {
        executor.shutdownNow()
    }
}
