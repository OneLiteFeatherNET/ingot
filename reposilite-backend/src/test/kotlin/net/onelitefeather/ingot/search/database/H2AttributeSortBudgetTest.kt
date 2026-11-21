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

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.AttributeSort
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/**
 * The cost of a sort by attribute on the database backend (search attribute facets, D4 and Q2), measured on a fixture of the
 * expected size: 10 000 versions, each with one value except every seventh. The budget is a count of statements, not of
 * time: the sort is a subquery inside the page query, so it adds no statement per row and the page costs what an unsorted one
 * costs.
 */
internal class H2AttributeSortBudgetTest {

    @TempDir
    lateinit var directory: Path

    private val clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val statements = AtomicInteger()
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:file:${directory.resolve("search").toAbsolutePath()};MODE=MYSQL"
                driverClassName = "org.h2.Driver"
                maximumPoolSize = 1
                minimumIdle = 1
            }
        )
        database = Database.connect(counting(dataSource))
        SearchSchema.ensure(database, clock)
        IndexFixture(database, clock).insertGroups(count = ROWS, lastModified = NOW.toEpochMilli(), indexedAt = clock.millis())
        insertValues()
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `a sort by attribute over 10 000 rows costs no more statements than an unsorted page`() {
        val engine = DatabaseArtifactSearchEngine(database, clock)

        val unsorted = statementsOf { engine.search(ArtifactSearchQuery(), ACCESS) }
        val sorted = statementsOf { engine.search(ArtifactSearchQuery(attributeSort = AttributeSort(KEY, descending = false)), ACCESS) }

        assertThat(sorted).`as`("statements of a page sorted by attribute, unsorted page took $unsorted").isEqualTo(unsorted)
        assertThat(sorted).`as`("statements of a page sorted by attribute against the recorded budget").isLessThanOrEqualTo(STATEMENT_BUDGET)
    }

    @Test
    fun `a sort by attribute over 10 000 rows starts with the smallest value`() {
        val engine = DatabaseArtifactSearchEngine(database, clock)

        val page = engine.search(ArtifactSearchQuery(attributeSort = AttributeSort(KEY, descending = false)), ACCESS)

        assertThat(page.items.first().version).`as`("version with the smallest value").isEqualTo("1.0.$ROWS")
    }

    private fun statementsOf(read: () -> Unit): Int {
        statements.set(0)
        read()
        return statements.get()
    }

    /** One value per version, falling as the version rises, and none for every seventh version. */
    private fun insertValues() {
        val attributes = SearchTables(SearchDialect.of(database.vendor)).attribute
        transaction(database) {
            attributes.batchInsert((1..ROWS).filter { it % 7 != 0 }) { version ->
                val path = "bulk/lib/1.0.$version/lib-1.0.$version.jar"
                val value = "%05d".format(ROWS - version)
                this[attributes.repository] = "releases"
                this[attributes.path] = path
                this[attributes.pathHash] = pathHash("releases", path)
                this[attributes.key] = KEY
                this[attributes.valueIndex] = 0
                this[attributes.value] = value
                this[attributes.valueLc] = value
            }
        }
    }

    /** [delegate] with every statement a connection creates counted in [statements]. */
    private fun counting(delegate: DataSource): DataSource =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DataSource::class.java)) { _, method, arguments ->
            val result = invoke(delegate, method, arguments)
            if (result is Connection) countingConnection(result) else result
        } as DataSource

    private fun countingConnection(delegate: Connection): Connection =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Connection::class.java)) { _, method, arguments ->
            if (method.name in STATEMENT_FACTORIES) statements.incrementAndGet()
            invoke(delegate, method, arguments)
        } as Connection

    private fun invoke(target: Any, method: java.lang.reflect.Method, arguments: Array<Any?>?): Any? =
        try {
            method.invoke(target, *(arguments ?: emptyArray()))
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-21T10:00:00Z")
        const val ROWS = 10_000
        const val KEY = "portal.versions"
        val ACCESS = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.All)))
        val STATEMENT_FACTORIES = setOf("prepareStatement", "createStatement", "prepareCall")

        /** Measured on this fixture: the statements of one page, sorted or not, with the status reads of the page. */
        const val STATEMENT_BUDGET = 14
    }

}
