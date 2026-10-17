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
import net.onelitefeather.ingot.search.database.SearchSchema
import net.onelitefeather.ingot.search.database.SqliteFileDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/** The state transitions of one outbox row against a database, with an advanceable clock (artifact search spec, 3.4). */
internal class OutboxStoreTest {

    private val clock = MutableClock(Instant.parse("2026-10-16T10:00:00Z"))

    @TempDir
    lateinit var directory: Path

    @Test
    fun `a claim takes an entry, counts an attempt and names its owner`() {
        SqliteFileDatabase(directory.resolve("claim.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            val store = OutboxStore(shared.database, clock)
            store.enqueue("releases", "com/example/app/1.0/app-1.0.jar", OutboxOp.CHANGED_PATH)

            val claimed = store.claim(owner = "instance-a", limit = 10).single()

            assertThat(claimed.attempts).`as`("attempts after the first claim").isEqualTo(1)
            assertThat(claimed.leaseOwner).`as`("owner of the lease").isEqualTo("instance-a")
            assertThat(claimed.leaseUntil).`as`("lease end").isEqualTo(clock.instant().plus(OutboxPolicy.LEASE))
        }
    }

    @Test
    fun `completing an entry removes it`() {
        SqliteFileDatabase(directory.resolve("complete.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            val store = OutboxStore(shared.database, clock)
            store.enqueue("releases", "com/example/app/1.0/app-1.0.jar", OutboxOp.CHANGED_PATH)
            val entry = store.claim(owner = "instance-a", limit = 10).single()

            assertThat(store.complete("instance-a", listOf(entry.id))).`as`("removed entries").isEqualTo(1)
            assertThat(store.pending()).`as`("entries left").isZero()
        }
    }

    @Test
    fun `completing an entry that another instance took over removes nothing`() {
        SqliteFileDatabase(directory.resolve("taken.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            val crashed = OutboxStore(shared.database, clock)
            crashed.enqueue("releases", "com/example/app/1.0/app-1.0.jar", OutboxOp.CHANGED_PATH)
            val entry = crashed.claim(owner = "instance-a", limit = 10).single()
            clock.advance(OutboxPolicy.LEASE.plusSeconds(1))
            OutboxStore(shared.database, clock).claim(owner = "instance-b", limit = 10)

            assertThat(crashed.complete("instance-a", listOf(entry.id)))
                .`as`("removed by the instance that lost the lease")
                .isZero()
            assertThat(crashed.pending()).`as`("entries left for instance-b").isEqualTo(1)
        }
    }

    @Test
    fun `a failed entry is not claimable again before its backoff ends`() {
        SqliteFileDatabase(directory.resolve("backoff.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            val store = OutboxStore(shared.database, clock)
            store.enqueue("releases", "com/example/app/1.0/app-1.0.jar", OutboxOp.CHANGED_PATH)
            val entry = store.claim(owner = "instance-a", limit = 10).single()
            store.fail("instance-a", entry.id, "storage unavailable")

            clock.advance(Duration.ofSeconds(9))
            assertThat(store.claim(owner = "instance-a", limit = 10))
                .`as`("claims before the 10 second backoff ends")
                .isEmpty()

            clock.advance(Duration.ofSeconds(2))
            val retried = store.claim(owner = "instance-a", limit = 10).single()
            assertThat(retried.lastError).`as`("error of the failed attempt").isEqualTo("storage unavailable")
            assertThat(retried.attempts).`as`("attempts after the retry").isEqualTo(2)
        }
    }

    @Test
    fun `an entry that used up its ten attempts becomes a dead letter`() {
        SqliteFileDatabase(directory.resolve("dead.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            val store = OutboxStore(shared.database, clock)
            store.enqueue("releases", "com/example/app/1.0/app-1.0.jar", OutboxOp.CHANGED_PATH)
            repeat(OutboxPolicy.MAX_ATTEMPTS) {
                clock.advance(OutboxPolicy.MAX_BACKOFF.plusSeconds(1))
                val entry = store.claim(owner = "instance-a", limit = 10).single()
                store.fail("instance-a", entry.id, "still failing")
            }

            clock.advance(OutboxPolicy.MAX_BACKOFF.plusSeconds(1))
            assertThat(store.claim(owner = "instance-a", limit = 10)).`as`("claims of a dead letter").isEmpty()
            assertThat(store.deadLetters()).`as`("dead letters").isEqualTo(1)
            assertThat(store.pending()).`as`("pending entries").isZero()
        }
    }

    @Test
    fun `an extension keeps only the entries the instance still holds`() {
        SqliteFileDatabase(directory.resolve("extend.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            val store = OutboxStore(shared.database, clock)
            repeat(2) { store.enqueue("releases", "com/example/app/1.0/app-1.0-$it.jar", OutboxOp.CHANGED_PATH) }
            val (first, second) = store.claim(owner = "instance-a", limit = 2)
            clock.advance(OutboxPolicy.LEASE.plusSeconds(1))
            OutboxStore(shared.database, clock).claim(owner = "instance-b", limit = 1)

            val stillHeld = store.extend("instance-a", listOf(first.id, second.id))

            assertThat(stillHeld).`as`("entries still held after the extension").containsExactly(second.id)
        }
    }

    @Test
    fun `an entry whose path is longer than the column can take is refused`() {
        SqliteFileDatabase(directory.resolve("long.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            val store = OutboxStore(shared.database, clock)

            val longPath = "a/".repeat(300) + "app-1.0.jar"

            org.assertj.core.api.Assertions.assertThatThrownBy {
                store.enqueue("releases", longPath, OutboxOp.CHANGED_PATH)
            }.`as`("enqueue of an over-long path").isInstanceOf(IllegalArgumentException::class.java)
        }
    }

}
