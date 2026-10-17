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

package net.onelitefeather.ingot.search.outbox

import com.reposilite.audit.MutableClock
import com.reposilite.scheduling.runAtOnce
import net.onelitefeather.ingot.search.database.SearchSchema
import net.onelitefeather.ingot.search.database.SqliteFileDatabase
import org.jetbrains.exposed.v1.jdbc.Database
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Two engine instances on one database claim work from the same outbox. A claimed entry belongs to one of them until
 * its lease runs out, so their batches never overlap and a crashed claimant's entries come back to the other one.
 */
internal class OutboxClaimExclusivityTest {

    private val clock = MutableClock(Instant.parse("2026-10-16T10:00:00Z"))

    @TempDir
    lateinit var directory: Path

    @Test
    fun `two workers on one database claim disjoint batches`() {
        SqliteFileDatabase(directory.resolve("disjoint.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            enqueue(shared.database, count = 10)

            val first = OutboxStore(shared.database, clock).claim(owner = "instance-a", limit = 5)
            val second = OutboxStore(shared.database, clock).claim(owner = "instance-b", limit = 5)

            assertThat(first.map { it.id }).`as`("first batch").hasSize(5)
            assertThat(second.map { it.id }).`as`("second batch").hasSize(5)
            assertThat(first.map { it.id }.intersect(second.map { it.id }.toSet()))
                .`as`("entries claimed by both workers")
                .isEmpty()
        }
    }

    @Test
    fun `concurrent claims never hand out the same entry twice`() {
        SqliteFileDatabase(directory.resolve("concurrent.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            enqueue(shared.database, count = 10)
            val workers = (1..4).map { OutboxStore(shared.database, clock) }

            val batches = runAtOnce(workers.mapIndexed { index, store -> { store.claim(owner = "instance-$index", limit = 10) } })

            val claimed = batches.flatten().map { it.id }
            assertThat(claimed).`as`("claimed entries, each once").doesNotHaveDuplicates()
            assertThat(claimed).`as`("entries claimed by the race").isNotEmpty().hasSizeLessThanOrEqualTo(10)
        }
    }

    @Test
    fun `an entry of an expired lease is taken over by another instance`() {
        SqliteFileDatabase(directory.resolve("takeover.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            enqueue(shared.database, count = 3)
            val crashed = OutboxStore(shared.database, clock).claim(owner = "instance-a", limit = 10)

            clock.advance(LEASE.plusSeconds(1))
            val takenOver = OutboxStore(shared.database, clock).claim(owner = "instance-b", limit = 10)

            assertThat(takenOver.map { it.id })
                .`as`("entries of the expired lease")
                .containsExactlyInAnyOrderElementsOf(crashed.map { it.id })
            assertThat(takenOver.map { it.leaseOwner }.toSet())
                .`as`("lease owner after the takeover")
                .containsExactly("instance-b")
        }
    }

    @Test
    fun `an entry of a live lease is not taken over before the lease expires`() {
        SqliteFileDatabase(directory.resolve("live.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            enqueue(shared.database, count = 3)
            OutboxStore(shared.database, clock).claim(owner = "instance-a", limit = 10)

            clock.advance(LEASE.minusSeconds(1))
            val early = OutboxStore(shared.database, clock).claim(owner = "instance-b", limit = 10)

            assertThat(early).`as`("claims while the lease of instance-a is live").isEmpty()
        }
    }

    private fun enqueue(database: Database, count: Int) {
        val store = OutboxStore(database, clock)
        repeat(count) { index ->
            store.enqueue(repository = "releases", path = "com/example/app/1.0/app-1.0-$index.jar", op = OutboxOp.CHANGED_PATH)
        }
    }

    private companion object {
        val LEASE: Duration = Duration.ofSeconds(60)
    }

}
