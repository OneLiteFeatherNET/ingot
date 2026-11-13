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

@file:OptIn(net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.database

import com.reposilite.IsolatedResources
import com.reposilite.SharedContainers
import com.reposilite.audit.MutableClock
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.databaseToken
import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.IndexAction
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

/**
 * A coordinate at the longest length the search columns accept reaches MariaDB, and the sweep keys it. The key of a GAV is 16
 * bytes whatever the length of its coordinates, so the row fits `gav_key` (binary(16)).
 */
internal class MariaDbSearchKeyWidthTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `the sweep keys a row with the longest coordinates into a 16 byte gav_key`() {
        val name = IsolatedResources.createMariaDbDatabase(SharedContainers.mariaDb)
        val connection = DatabaseConnectionFactory.createConnection(directory, SharedContainers.mariaDb.databaseToken(name), 2)
        try {
            val database = connection.database
            val clock = MutableClock(Instant.parse(NOW))
            SearchSchema.ensure(database, clock)
            val backend = DatabaseSearchBackend(database, clock)
            backend.apply(listOf(IndexAction.Upsert(row(LONGEST_JAR))))
            transaction(database) { exec("UPDATE search_artifact SET gav_key = NULL") }

            val batch = backend.keyRows(0L, 10)

            assertThat(batch?.keyed).`as`("rows keyed by the sweep").isEqualTo(1)
            assertThat(keyLength(database))
                .`as`("length in bytes of the stored gav_key")
                .isEqualTo(16)
        } finally {
            connection.close()
            IsolatedResources.dropMariaDbDatabase(SharedContainers.mariaDb, name)
        }
    }

    private fun keyLength(database: Database): Int = transaction(database) {
        exec("SELECT LENGTH(gav_key) FROM search_artifact") { rs -> if (rs.next()) rs.getInt(1) else -1 } ?: -1
    }

    private fun row(path: String): IndexRow {
        val parsed = ArtifactPathParser.parse(REPOSITORY, path) as ArtifactPathResult.Row
        return IndexRow(
            file = parsed.file,
            provenance = Provenance(Origin.HOSTED, null),
            sizeBytes = 10L,
            lastModified = NOW_MILLIS,
            sha1 = null,
            sha256 = null,
            indexedAt = NOW_MILLIS,
        )
    }

    private companion object {
        const val NOW = "2026-11-11T10:00:00Z"
        val NOW_MILLIS: Long = Instant.parse(NOW).toEpochMilli()
        const val REPOSITORY = "releases"

        /** groupId of 201 characters, artifactId of 120, version of 24: the longest path the path column takes in practice. */
        val LONGEST_JAR = "${"a".repeat(100)}/${"b".repeat(100)}/${"c".repeat(120)}/1.0-${"d".repeat(20)}/" +
            "${"c".repeat(120)}-1.0-${"d".repeat(20)}.jar"
    }

}
