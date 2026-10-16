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
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.database.SqliteFileDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

/** The sink that the write paths call: it queues the right operation and never throws (artifact search spec, 3.5). */
internal class ArtifactIndexSinkTest {

    private val clock = MutableClock(Instant.parse("2026-10-16T10:00:00Z"))

    @TempDir
    lateinit var directory: Path

    @Test
    fun `each call of the sink queues its own operation for the worker`() {
        SqliteFileDatabase(directory.resolve("ops.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            val outbox = OutboxStore(shared.database, clock)
            val sink = OutboxArtifactIndexSink(outbox, SearchStateStore(shared.database, clock)) {}

            sink.changed("releases", "com/example/app/1.0/app-1.0.jar")
            sink.removed("releases", "com/example/app/0.9")
            sink.reorigin("releases", "com/example/app/1.0")

            val queued = outbox.claim(owner = "instance-a", limit = 10).map { it.op to it.path }
            assertThat(queued).`as`("queued operations in order").containsExactly(
                OutboxOp.CHANGED_PATH to "com/example/app/1.0/app-1.0.jar",
                OutboxOp.REMOVED_PREFIX to "com/example/app/0.9",
                OutboxOp.REORIGIN_DIRECTORY to "com/example/app/1.0",
            )
        }
    }

    @Test
    fun `a refused enqueue neither throws nor stops the write path, and is counted`() {
        SqliteFileDatabase(directory.resolve("refused.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            val state = SearchStateStore(shared.database, clock)
            val sink = OutboxArtifactIndexSink(OutboxStore(shared.database, clock), state) {}

            sink.changed("releases", "a/".repeat(300) + "app-1.0.jar")

            assertThat(state.get(SearchStateKey.DROPPED_ENQUEUES)).`as`("dropped enqueues").isEqualTo("1")
        }
    }

}
