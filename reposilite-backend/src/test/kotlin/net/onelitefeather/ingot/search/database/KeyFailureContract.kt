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

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.IndexAction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant

/** A database opened for one test, and what closes it. */
internal class OpenedDatabase(val database: Database, val close: () -> Unit)

/**
 * A row whose key the database refuses is skipped by the sweep, and the rows around it are keyed (full text change, D6). The
 * refusal is a CHECK constraint that names the artifact `poison`: it is added after the rows are written, and it fails the
 * statement that sets the key of such a row. Each test opens a fresh database; the vendor subclasses pick it.
 */
internal abstract class KeyFailureContract {

    @TempDir
    lateinit var directory: Path

    /** Opens a fresh database in [directory]. */
    protected abstract fun open(directory: Path): OpenedDatabase

    private val clock: Clock = MutableClock(Instant.parse(NOW))

    /** Runs [block] with the database and a close that may be called again: a test can close it to simulate an outage. */
    private fun withDatabase(block: (Database, () -> Unit) -> Unit) {
        val opened = open(directory)
        var closed = false
        val close = {
            if (!closed) {
                closed = true
                opened.close()
            }
        }
        try {
            block(opened.database, close)
        } finally {
            close()
        }
    }

    /** Writes [paths] through the production backend, then clears their keys and adds the refusal. */
    private fun seed(database: Database, paths: List<String>): DatabaseSearchBackend {
        SearchSchema.ensure(database, clock)
        val backend = DatabaseSearchBackend(database, clock)
        backend.apply(paths.map { IndexAction.Upsert(row(it)) })
        transaction(database) {
            exec("UPDATE search_artifact SET gav_key = NULL")
            exec("ALTER TABLE search_artifact ADD CONSTRAINT ck_refuse_poison CHECK (NOT (artifact_id_lc = 'poison' AND gav_key IS NOT NULL))")
        }
        return backend
    }

    @Test
    fun `a row whose key the database refuses is skipped and the healthy row of its batch is keyed`() = withDatabase { database, _ ->
        val backend = seed(database, listOf(HEALTHY_JAR, POISON_JAR))

        val batch = backend.keyRows(0L, 10)

        assertThat(batch?.keyed).`as`("rows keyed in the batch").isEqualTo(1)
        assertThat(batch?.skipped?.map { it.id })
            .`as`("ids of the rows skipped in the batch")
            .containsExactly(idOf(database, POISON))
        assertThat(keyedCount(database)).`as`("rows with a key after the batch").isEqualTo(1L)
    }

    @Test
    fun `a batch whose every row is refused is skipped row by row while the database answers`() = withDatabase { database, _ ->
        val backend = seed(database, listOf(POISON_JAR, POISON_OTHER_VERSION_JAR))

        val batch = backend.keyRows(0L, 10)

        assertThat(batch?.skipped?.size)
            .`as`("rows skipped when every row of the batch is refused")
            .isEqualTo(2)
    }

    @Test
    fun `a batch that fails because the database is gone is not skipped`() = withDatabase { database, close ->
        val backend = seed(database, listOf(HEALTHY_JAR, POISON_JAR))
        close()

        assertThatThrownBy { backend.keyRows(0L, 10) }
            .`as`("failure of a batch while the database is unreachable")
            .isInstanceOf(Exception::class.java)
    }

    private fun idOf(database: Database, artifactId: String): Long = transaction(database) {
        exec("SELECT id FROM search_artifact WHERE artifact_id_lc = '$artifactId'") { rs -> if (rs.next()) rs.getLong(1) else -1L } ?: -1L
    }

    private fun keyedCount(database: Database): Long = transaction(database) {
        exec("SELECT COUNT(*) FROM search_artifact WHERE gav_key IS NOT NULL") { rs -> rs.next(); rs.getLong(1) } ?: 0L
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
        const val HEALTHY_JAR = "com/example/healthy/1.0/healthy-1.0.jar"
        const val POISON = "poison"
        const val POISON_JAR = "com/example/poison/1.0/poison-1.0.jar"
        const val POISON_OTHER_VERSION_JAR = "com/example/poison/2.0/poison-2.0.jar"
    }

}
