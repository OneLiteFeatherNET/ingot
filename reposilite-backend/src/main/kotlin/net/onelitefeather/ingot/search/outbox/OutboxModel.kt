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

import java.time.Duration
import java.time.Instant

/**
 * What a claimed entry asks the worker to do (artifact search spec, 2.5 and 3.3). The entry names a path, never the state
 * of the file: the worker reads storage again when it processes it.
 */
enum class OutboxOp {
    /** Look at the path again and upsert or delete its row. */
    CHANGED_PATH,

    /** The path, a file or a directory, was removed: drop its rows and rows below it that storage no longer has. */
    REMOVED_PREFIX,

    /** The directory was deployed into: rewrite the origin of its rows from the mirror ledger. */
    REORIGIN_DIRECTORY,
}

/** One row of `search_outbox`, as a worker holds it after a claim. */
data class OutboxEntry(
    val id: Long,
    val repository: String,
    val path: String,
    val op: OutboxOp,
    val createdAt: Instant,
    val leaseOwner: String?,
    val leaseUntil: Instant?,
    val attempts: Int,
    val lastError: String?,
)

/**
 * The timing rules of the outbox (artifact search spec, 3.4), free of any database. Every decision takes the current
 * instant as a parameter, so the rules are tested with a fixed clock.
 */
object OutboxPolicy {

    /** How long a claim holds an entry before another instance may take it over. */
    val LEASE: Duration = Duration.ofSeconds(60)

    /** How often a busy worker extends the leases of the entries it still holds. */
    val LEASE_EXTENSION_INTERVAL: Duration = Duration.ofSeconds(20)

    /** Claims per entry before it counts as a dead letter. */
    const val MAX_ATTEMPTS: Int = 10

    /** Entries claimed in one batch. */
    const val BATCH_SIZE: Int = 200

    /** The longest pause after a failed entry. */
    val MAX_BACKOFF: Duration = Duration.ofMinutes(10)

    private val BACKOFF_STEP: Duration = Duration.ofSeconds(5)
    private val BUSY_POLL: Duration = Duration.ofSeconds(2)
    private val IDLE_POLL: Duration = Duration.ofSeconds(10)

    /** The lease an entry gets when a worker claims it at [now]. */
    fun leaseUntil(now: Instant): Instant = now.plus(LEASE)

    /** When a failed entry may be claimed again: `2^attempts * 5 s`, capped at [MAX_BACKOFF]. */
    fun retryAt(now: Instant, attempts: Int): Instant {
        val exponent = attempts.coerceIn(0, MAX_EXPONENT)
        val delay = BACKOFF_STEP.multipliedBy(1L shl exponent)
        return now.plus(if (delay > MAX_BACKOFF) MAX_BACKOFF else delay)
    }

    /** An entry is claimable when it is no dead letter and its lease ended strictly before [now]. */
    fun isClaimable(attempts: Int, leaseUntil: Instant?, now: Instant): Boolean =
        !isDeadLetter(attempts) && (leaseUntil == null || leaseUntil.isBefore(now))

    fun isDeadLetter(attempts: Int): Boolean = attempts >= MAX_ATTEMPTS

    /** The pause before the next poll: short while there is work, long while idle. */
    fun nextPollDelay(claimed: Int): Duration = if (claimed > 0) BUSY_POLL else IDLE_POLL

    private const val MAX_EXPONENT = 20

}
