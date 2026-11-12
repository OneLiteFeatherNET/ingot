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

package net.onelitefeather.ingot.search

import net.onelitefeather.ingot.search.backend.FakeArtifactSearchBackend
import net.onelitefeather.ingot.search.database.H2FileDatabase
import net.onelitefeather.ingot.search.database.IndexFixture
import net.onelitefeather.ingot.search.database.SearchSchema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The outbox part of the index status (artifact search spec, 4.8) for a backend that keeps no outbox of its own. The core
 * tables hold the entries, so the lag and the dead letters come from them whatever the backend answers for its status.
 */
internal class IndexStatusReaderTest {

    @TempDir
    lateinit var directory: Path

    private val now: Instant = Instant.parse("2026-11-13T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var opened: H2FileDatabase
    private lateinit var fixture: IndexFixture

    @BeforeEach
    fun setUp() {
        opened = H2FileDatabase(directory.resolve("search"))
        SearchSchema.ensure(opened.database, clock)
        fixture = IndexFixture(opened.database, clock)
    }

    @AfterEach
    fun tearDown() {
        opened.close()
    }

    @Test
    fun `reports the lag, the dead letters and the pending entries of the outbox for a backend without an outbox`() {
        fixture.enqueue(createdAtMillis = now.minusSeconds(600).toEpochMilli(), attempts = 0)
        fixture.enqueue(createdAtMillis = now.minusSeconds(3600).toEpochMilli(), attempts = 10)

        val status = IndexStatusReader(opened.database, clock).statusOf(FakeArtifactSearchBackend(), rowsExist = true)

        assertThat(status.lagSeconds).`as`("lag of the oldest pending entry").isEqualTo(600)
        assertThat(status.deadLetters).`as`("dead letters").isEqualTo(1)
        assertThat(status.pendingEntries).`as`("pending entries").isEqualTo(1)
    }
}
