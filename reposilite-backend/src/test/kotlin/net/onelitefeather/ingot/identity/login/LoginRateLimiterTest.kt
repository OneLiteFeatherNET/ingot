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

package net.onelitefeather.ingot.identity.login

import com.reposilite.auth.application.BruteForceProtectionSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference
import java.time.Duration
import java.time.Instant

internal class LoginRateLimiterTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))

    private fun limiter(enabled: Boolean = true, maxAttempts: Int = 3, banSeconds: Long = 300) =
        LoginRateLimiter(
            settings = Reference.reference(BruteForceProtectionSettings(enabled = enabled, maxAttempts = maxAttempts, banDurationSeconds = banSeconds)),
            clock = clock,
        )

    @Test
    fun `does not block before the maximum number of failures`() {
        val limiter = limiter(maxAttempts = 3)

        repeat(2) { limiter.recordFailure("alice|10.0.0.1") }

        assertThat(limiter.isBlocked("alice|10.0.0.1")).describedAs("two of three allowed failures must not block").isFalse()
    }

    @Test
    fun `blocks once the maximum number of failures is reached`() {
        val limiter = limiter(maxAttempts = 3)

        repeat(3) { limiter.recordFailure("alice|10.0.0.1") }

        assertThat(limiter.isBlocked("alice|10.0.0.1")).describedAs("the third failure must start the ban").isTrue()
    }

    @Test
    fun `unblocks after the ban duration has passed`() {
        val limiter = limiter(maxAttempts = 3, banSeconds = 300)
        repeat(3) { limiter.recordFailure("alice|10.0.0.1") }

        clock.advance(Duration.ofSeconds(299))
        assertThat(limiter.isBlocked("alice|10.0.0.1")).describedAs("the ban must hold until its duration is over").isTrue()

        clock.advance(Duration.ofSeconds(1))
        assertThat(limiter.isBlocked("alice|10.0.0.1")).describedAs("the ban must end once its duration is over").isFalse()
    }

    @Test
    fun `keeps the failures of different keys apart`() {
        val limiter = limiter(maxAttempts = 3)
        repeat(3) { limiter.recordFailure("alice|10.0.0.1") }

        assertThat(limiter.isBlocked("alice|10.0.0.2")).describedAs("the same user from another address is a different key").isFalse()
        assertThat(limiter.isBlocked("bob|10.0.0.1")).describedAs("another user from the same address is a different key").isFalse()
    }

    @Test
    fun `forgets the failures of a key after a successful login`() {
        val limiter = limiter(maxAttempts = 3)
        repeat(2) { limiter.recordFailure("alice|10.0.0.1") }

        limiter.forget("alice|10.0.0.1")
        repeat(2) { limiter.recordFailure("alice|10.0.0.1") }

        assertThat(limiter.isBlocked("alice|10.0.0.1")).describedAs("failures before the success must not count any more").isFalse()
    }

    @Test
    fun `never blocks while brute force protection is disabled`() {
        val limiter = limiter(enabled = false, maxAttempts = 1)

        repeat(10) { limiter.recordFailure("alice|10.0.0.1") }

        assertThat(limiter.isBlocked("alice|10.0.0.1")).describedAs("a disabled protection must not block anybody").isFalse()
    }

}
