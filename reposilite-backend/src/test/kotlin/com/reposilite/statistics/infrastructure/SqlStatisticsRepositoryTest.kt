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
package com.reposilite.statistics.infrastructure

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.api.Identifier
import net.onelitefeather.ingot.search.database.SqliteFileDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDate

internal class SqlStatisticsRepositoryTest {

    @TempDir
    lateinit var directory: Path

    private val date = LocalDate.of(2026, 11, 10)
    private val identifier = Identifier("releases", "com/example/a")

    @Test
    fun `should store the counts of a writer whose identifier lookup missed an existing row`() {
        SqliteFileDatabase(directory.resolve("statistics.db")).use { sqlite ->
            // given: a first writer has stored the identifier with one count
            val first = SqlStatisticsRepository(sqlite.database, InMemoryLogger(), emptyArray())
            first.incrementResolvedRequests(mapOf(identifier to 1L), date)

            // and a second writer whose lookup reports the identifier as not found, although the row exists
            val second = SqlStatisticsRepository(
                sqlite.database,
                InMemoryLogger(),
                emptyArray(),
                identifierLookup = { null }
            )

            // when: the second writer stores two counts for the same identifier
            second.incrementResolvedRequests(mapOf(identifier to 2L), date)

            // then: the identifier insert is ignored, the batch succeeds and all three counts are stored
            assertThat(first.countResolvedRequests())
                .describedAs("the second writer's counts must be stored next to the first writer's count")
                .isEqualTo(3L)
        }
    }

    @Test
    fun `should add the counts of a second writer to an identifier created by the first`() {
        SqliteFileDatabase(directory.resolve("statistics.db")).use { sqlite ->
            // given: a first writer has stored the identifier with two counts
            val first = SqlStatisticsRepository(sqlite.database, InMemoryLogger(), emptyArray())
            first.incrementResolvedRequests(mapOf(identifier to 2L), date)

            // when: a second writer stores three counts for the same identifier
            val second = SqlStatisticsRepository(sqlite.database, InMemoryLogger(), emptyArray())
            second.incrementResolvedRequests(mapOf(identifier to 3L), date)

            // then: both writers' counts are stored
            assertThat(first.countResolvedRequests())
                .describedAs("both writers must store their counts for the same identifier")
                .isEqualTo(5L)
        }
    }

}
