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

package net.onelitefeather.ingot.lucene

import com.reposilite.audit.MutableClock
import com.reposilite.scheduling.ScheduledJobClaims
import net.onelitefeather.ingot.search.backend.BackendLogger
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

/** The rows of one shared lease store: every instance of a test reads and writes the same table. */
internal class LeaseTable {

    class Entry(var owner: String?, var epoch: Long, var until: Long)

    val entries = HashMap<String, Entry>()
}

/**
 * A lease store of one instance over [table], with the rules of the database store: a grant bumps the epoch, a live lease refuses
 * other owners, renewal needs the owner, the epoch and a live lease, and release frees the key. The clock is injected.
 */
internal class FakeLeases(
    private val table: LeaseTable,
    private val owner: String,
    private val clock: Clock,
) : ScheduledJobClaims {

    override fun claim(key: String): Boolean = false

    override fun acquire(key: String, ttl: Duration): ScheduledJobClaims.Lease? {
        val now = clock.millis()
        val entry = table.entries[key]
        if (entry != null && entry.owner != null && entry.until > now) return null
        val epoch = (entry?.epoch ?: 0L) + 1
        table.entries[key] = LeaseTable.Entry(owner, epoch, now + ttl.toMillis())
        return ScheduledJobClaims.Lease(key, epoch)
    }

    override fun renew(lease: ScheduledJobClaims.Lease, ttl: Duration): Boolean {
        val entry = table.entries[lease.key] ?: return false
        val now = clock.millis()
        if (entry.owner != owner || entry.epoch != lease.epoch || entry.until <= now) return false
        entry.until = now + ttl.toMillis()
        return true
    }

    override fun release(lease: ScheduledJobClaims.Lease, done: Boolean) {
        table.entries[lease.key]
            ?.takeIf { it.owner == owner && it.epoch == lease.epoch }
            ?.let { entry -> entry.owner = null; entry.until = 0L }
    }

}

/** A logger that keeps every line, so that a test can assert what the backend reported. */
internal class RecordingLogger : BackendLogger {

    val lines = mutableListOf<String>()

    override fun info(message: String) {
        lines += message
    }

    override fun warn(message: String) {
        lines += message
    }

    override fun error(message: String) {
        lines += message
    }

}

/** A backend with its own lease table and clock, for the tests that have one replica. */
internal fun testBackend(
    settings: LuceneSettings,
    openDirectory: (Path) -> Directory = { FSDirectory.open(it) },
    usableSpace: (Path) -> Long = { Long.MAX_VALUE },
): LuceneSearchBackend {
    val clock = MutableClock()
    return LuceneSearchBackend(settings, FakeLeases(LeaseTable(), "test", clock), clock, RecordingLogger(), openDirectory, usableSpace)
}
