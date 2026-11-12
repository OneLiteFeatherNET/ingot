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

package net.onelitefeather.ingot.search.backend

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.database.SearchSchema
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.database.SqliteFileDatabase
import net.onelitefeather.ingot.search.runs.RunStore
import net.onelitefeather.ingot.search.runs.RunTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * The cluster agreement against a shared database, with two replicas that write heartbeats and one clock that the test
 * advances explicitly. Every decision reads the stored id and the heartbeats, as a replica does.
 */
internal class ClusterBackendAgreementTest {

    private val start = Instant.parse("2026-10-16T10:00:00Z")
    private val clock = MutableClock(start)

    @TempDir
    lateinit var directory: Path

    private fun withDatabase(block: (SearchStateStore, RunStore) -> Unit) {
        SqliteFileDatabase(directory.resolve("agreement.db")).use { sqlite ->
            SearchSchema.ensure(sqlite.database, clock)
            block(SearchStateStore(sqlite.database, clock), RunStore(sqlite.database, clock))
        }
    }

    private fun replica(state: SearchStateStore, runs: RunStore, instance: String, backend: String) =
        ClusterBackendAgreement(state = state, runs = runs, clock = clock, instanceId = instance, backendId = backend)

    @Test
    fun `a replica of the stored backend drains`() = withDatabase { state, runs ->
        val database = replica(state, runs, "instance-a", "database")
        database.agree()

        val decision = database.agree()

        assertThat(decision).`as`("decision of the replica whose id is stored").isEqualTo(AgreementDecision.DRAIN)
    }

    @Test
    fun `an absent stored id is written by the first available replica`() = withDatabase { state, runs ->
        val decision = replica(state, runs, "instance-a", "opensearch").agree()

        assertThat(decision).`as`("decision while no id is stored").isEqualTo(AgreementDecision.CLAIM)
        assertThat(state.get(SearchStateKey.BACKEND_ID)).`as`("stored backend id after the claim").isEqualTo("opensearch")
    }

    @Test
    fun `a heartbeat younger than three minutes blocks the switch`() = withDatabase { state, runs ->
        replica(state, runs, "instance-a", "database").apply { heartbeat() }
        replica(state, runs, "instance-a", "database").agree()
        clock.advance(Duration.ofMinutes(2))

        val decision = replica(state, runs, "instance-b", "opensearch").agree()

        assertThat(decision).`as`("decision of the other backend while the stored one is seen").isEqualTo(AgreementDecision.MISMATCH)
        assertThat(state.get(SearchStateKey.BACKEND_ID)).`as`("stored backend id after the blocked switch").isEqualTo("database")
        assertThat(runs.recent(5)).`as`("runs queued by the blocked switch").isEmpty()
    }

    @Test
    fun `a heartbeat exactly three minutes old allows the switch`() = withDatabase { state, runs ->
        replica(state, runs, "instance-a", "database").apply { heartbeat() }
        replica(state, runs, "instance-a", "database").agree()
        clock.advance(Duration.ofMinutes(3))

        val decision = replica(state, runs, "instance-b", "opensearch").agree()

        assertThat(decision).`as`("decision of the other backend after exactly three minutes").isEqualTo(AgreementDecision.SWITCH)
    }

    @Test
    fun `a switch clears the last full rebuild and queues a backend run`() = withDatabase { state, runs ->
        replica(state, runs, "instance-a", "database").apply { heartbeat() }
        replica(state, runs, "instance-a", "database").agree()
        state.put(SearchStateKey.LAST_FULL_REBUILD, start.toEpochMilli().toString())
        clock.advance(Duration.ofMinutes(4))

        val decision = replica(state, runs, "instance-b", "opensearch").agree()

        assertThat(decision).`as`("decision of the switch").isEqualTo(AgreementDecision.SWITCH)
        assertThat(state.get(SearchStateKey.BACKEND_ID)).`as`("stored backend id after the switch").isEqualTo("opensearch")
        assertThat(state.get(SearchStateKey.LAST_FULL_REBUILD)).`as`("last full rebuild after the switch").isNull()
        assertThat(runs.recent(5).firstOrNull()?.trigger).`as`("trigger of the run queued by the switch").isEqualTo(RunTrigger.BACKEND)
    }

}
