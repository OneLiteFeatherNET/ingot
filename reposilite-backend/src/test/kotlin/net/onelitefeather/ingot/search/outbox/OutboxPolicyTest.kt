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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** The timing rules of the outbox, decided from a fixed instant (artifact search spec, 3.4 and 10.1). */
internal class OutboxPolicyTest {

    private val now = Instant.parse("2026-10-16T10:00:00Z")

    @Test
    fun `a claim holds its entry for sixty seconds`() {
        assertThat(OutboxPolicy.leaseUntil(now)).isEqualTo(now.plusSeconds(60))
    }

    @Test
    fun `the backoff after a failure doubles from ten seconds`() {
        assertThat(OutboxPolicy.retryAt(now, attempts = 1)).isEqualTo(now.plusSeconds(10))
        assertThat(OutboxPolicy.retryAt(now, attempts = 2)).isEqualTo(now.plusSeconds(20))
        assertThat(OutboxPolicy.retryAt(now, attempts = 3)).isEqualTo(now.plusSeconds(40))
    }

    @Test
    fun `the backoff never exceeds ten minutes`() {
        assertThat(OutboxPolicy.retryAt(now, attempts = 9)).isEqualTo(now.plus(Duration.ofMinutes(10)))
    }

    @Test
    fun `an entry whose lease ended before now is claimable`() {
        assertThat(OutboxPolicy.isClaimable(attempts = 1, leaseUntil = now.minusMillis(1), now = now)).isTrue()
    }

    @Test
    fun `an entry whose lease ends at now or later is not claimable`() {
        assertThat(OutboxPolicy.isClaimable(attempts = 1, leaseUntil = now, now = now))
            .`as`("lease ending exactly now")
            .isFalse()
        assertThat(OutboxPolicy.isClaimable(attempts = 1, leaseUntil = now.plusMillis(1), now = now))
            .`as`("lease ending later")
            .isFalse()
    }

    @Test
    fun `an entry without a lease is claimable until its tenth attempt`() {
        assertThat(OutboxPolicy.isClaimable(attempts = 9, leaseUntil = null, now = now)).isTrue()
        assertThat(OutboxPolicy.isClaimable(attempts = 10, leaseUntil = null, now = now))
            .`as`("entry at the attempt limit")
            .isFalse()
        assertThat(OutboxPolicy.isDeadLetter(attempts = 10)).isTrue()
    }

    @Test
    fun `the next poll follows work after two seconds and idleness after ten`() {
        assertThat(OutboxPolicy.nextPollDelay(claimed = 1)).isEqualTo(Duration.ofSeconds(2))
        assertThat(OutboxPolicy.nextPollDelay(claimed = 0)).isEqualTo(Duration.ofSeconds(10))
    }

    @Test
    fun `entries of the same repository, path and operation form one group in order of appearance`() {
        val entries = listOf(
            entry(id = 1, path = "a/app-1.0.jar"),
            entry(id = 2, path = "a/app-1.0.pom"),
            entry(id = 3, path = "a/app-1.0.jar"),
            entry(id = 4, path = "a/app-1.0.jar", op = OutboxOp.REMOVED_PREFIX),
        )

        val groups = OutboxWorker.collapse(entries).map { group -> group.map { it.id } }

        assertThat(groups).containsExactly(listOf(1L, 3L), listOf(2L), listOf(4L))
    }

    private fun entry(id: Long, path: String, op: OutboxOp = OutboxOp.CHANGED_PATH) = OutboxEntry(
        id = id,
        repository = "releases",
        path = path,
        op = op,
        createdAt = now,
        leaseOwner = null,
        leaseUntil = null,
        attempts = 0,
        lastError = null,
    )

}
