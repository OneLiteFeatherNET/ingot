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

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

internal class LoginAttemptLimiterTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    /** Two limiters on one database, as two instances of Ingot would be on a shared database. */
    private class Instances(val first: LoginAttemptLimiter, val second: LoginAttemptLimiter, val store: LoginStore)

    private fun withInstances(
        settings: LoginSettings = LoginSettings(maxFailedAttempts = 3, lockoutMinutes = 15),
        block: (Instances) -> Unit,
    ) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val reference = Reference.reference(settings)
            val first = LoginStore(connection.database, clock)
            val second = LoginStore(connection.database, clock)
            block(
                Instances(
                    first = LoginAttemptLimiter(first, reference, clock),
                    second = LoginAttemptLimiter(second, reference, clock),
                    store = first,
                )
            )
        }
    }

    @Test
    fun `reports no lock for a key without failures`() {
        withInstances { instances ->
            assertThat(instances.first.retryAfter(listOf(LoginAttemptLimiter.addressKey("10.0.0.1")))).isNull()
        }
    }

    @Test
    fun `shares the failures of one key between instances of the database`() {
        withInstances { instances ->
            val key = LoginAttemptLimiter.addressKey("10.0.0.1")
            instances.first.recordFailure(listOf(key))
            instances.first.recordFailure(listOf(key))
            instances.second.recordFailure(listOf(key))

            assertThat(instances.first.retryAfter(listOf(key)))
                .describedAs("a lock set by the failures of both instances must hold on every instance")
                .isNotNull()
        }
    }

    @Test
    fun `reports the time left of the lock`() {
        withInstances { instances ->
            val key = LoginAttemptLimiter.addressKey("10.0.0.1")
            repeat(3) { instances.first.recordFailure(listOf(key)) }

            clock.advance(Duration.ofMinutes(4))

            assertThat(instances.second.retryAfter(listOf(key))).isEqualTo(Duration.ofMinutes(11))
        }
    }

    @Test
    fun `keeps the user name key apart from the address key of the same address`() {
        withInstances { instances ->
            val userKey = LoginAttemptLimiter.userKey("alice", "10.0.0.1")
            repeat(3) { instances.first.recordFailure(listOf(userKey)) }

            assertThat(instances.second.retryAfter(listOf(LoginAttemptLimiter.addressKey("10.0.0.1"))))
                .describedAs("a locked user name must not lock the whole address")
                .isNull()
        }
    }

    @Test
    fun `forgets a key only when asked to`() {
        withInstances { instances ->
            val key = LoginAttemptLimiter.userKey("alice", "10.0.0.1")
            repeat(3) { instances.first.recordFailure(listOf(key)) }

            instances.second.forget(key)

            assertThat(instances.first.retryAfter(listOf(key))).describedAs("a forgotten key must not be locked any more").isNull()
        }
    }

    @Test
    fun `user name keys ignore the letter case of the name`() {
        assertThat(LoginAttemptLimiter.userKey("Alice", "10.0.0.1"))
            .describedAs("user names are case-insensitive, so their lock must be too")
            .isEqualTo(LoginAttemptLimiter.userKey("ALICE", "10.0.0.1"))
    }

    @Test
    fun `stores no plain user name or address`() {
        withInstances { instances ->
            instances.first.recordFailure(listOf(LoginAttemptLimiter.userKey("alice", "10.0.0.1")))

            val stored = instances.store.findAttempt(LoginAttemptLimiter.userKey("alice", "10.0.0.1"))
            assertThat(stored).isNotNull()
            assertThat(LoginAttemptLimiter.userKey("alice", "10.0.0.1")).doesNotContain("alice").doesNotContain("10.0.0.1")
        }
    }

    @Test
    fun `drops rows that have been quiet for longer than a day`() {
        withInstances { instances ->
            val stale = LoginAttemptLimiter.addressKey("10.0.0.1")
            instances.first.recordFailure(listOf(stale))

            clock.advance(Duration.ofHours(25))
            instances.second.recordFailure(listOf(LoginAttemptLimiter.addressKey("10.0.0.2")))

            assertThat(instances.store.findAttempt(stale))
                .describedAs("a quiet key must not keep a row forever, which would let the table grow without bound")
                .isNull()
        }
    }

}
