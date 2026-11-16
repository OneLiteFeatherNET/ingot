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

internal class AccountThrottleTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private val settings = LoginSettings(accountThrottle = AccountThrottleSettings(threshold = 3))

    @TempDir
    lateinit var workingDirectory: Path

    /** Two throttles on one database, as two instances of Ingot would be on a shared database. */
    private class Instances(val first: AccountThrottle, val second: AccountThrottle, val store: LoginStore)

    private fun withInstances(enabled: Boolean = true, block: (Instances) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val reference = Reference.reference(settings)
            val first = LoginStore(connection.database, clock)
            val second = LoginStore(connection.database, clock)
            block(
                Instances(
                    first = AccountThrottle(first, reference, clock, enabled),
                    second = AccountThrottle(second, reference, clock, enabled),
                    store = first,
                )
            )
        }
    }

    @Test
    fun `does nothing while the switch is off`() {
        withInstances(enabled = false) { instances ->
            repeat(10) { instances.first.recordFailure("alice") }

            assertThat(instances.second.retryAfter("alice", "10.0.0.2")).isNull()
            assertThat(instances.store.findAttempt(LoginAttemptLimiter.accountKey("alice")))
                .describedAs("no account row is written while the switch is off")
                .isNull()
        }
    }

    @Test
    fun `delays a name from an address it has never signed in from`() {
        withInstances { instances ->
            repeat(3) { instances.first.recordFailure("alice") }

            assertThat(instances.second.retryAfter("alice", "10.0.0.9")).isEqualTo(Duration.ofSeconds(1))
        }
    }

    @Test
    fun `does not delay a name from an address it has signed in from within the window`() {
        withInstances { instances ->
            instances.first.recordSuccess("alice", "10.0.0.1")
            repeat(3) { instances.first.recordFailure("alice") }

            assertThat(instances.second.retryAfter("alice", "10.0.0.1"))
                .describedAs("the owner at a known address is not delayed by the account key")
                .isNull()
            assertThat(instances.second.retryAfter("alice", "10.0.0.9")).isNotNull()
        }
    }

    @Test
    fun `counts a failure on one instance for the other`() {
        withInstances { instances ->
            instances.first.recordFailure("alice")
            instances.first.recordFailure("alice")
            instances.second.recordFailure("alice")

            assertThat(instances.first.retryAfter("alice", "10.0.0.9")).isNotNull()
        }
    }

    @Test
    fun `a success clears the delay of the name`() {
        withInstances { instances ->
            repeat(3) { instances.first.recordFailure("alice") }

            instances.first.recordSuccess("alice", "10.0.0.1")

            assertThat(instances.second.retryAfter("alice", "10.0.0.9")).isNull()
        }
    }

    @Test
    fun `keeps the delay of one name apart from another`() {
        withInstances { instances ->
            repeat(3) { instances.first.recordFailure("alice") }

            assertThat(instances.second.retryAfter("bob", "10.0.0.9")).isNull()
        }
    }

    @Test
    fun `the delay ends by itself on the injected clock`() {
        withInstances { instances ->
            repeat(3) { instances.first.recordFailure("alice") }

            clock.advance(Duration.ofSeconds(1))

            assertThat(instances.second.retryAfter("alice", "10.0.0.9")).isNull()
        }
    }

    @Test
    fun `second factor failures do not delay the password account of the same name`() {
        withInstances { instances ->
            val limiter = LoginAttemptLimiter(instances.store, Reference.reference(settings), clock)
            repeat(3) { limiter.recordFailure(listOf(LoginAttemptLimiter.mfaAccountKey("alice"))) }

            assertThat(instances.store.findAttempt(LoginAttemptLimiter.mfaAccountKey("alice")))
                .describedAs("the second factor failures are recorded under their own key")
                .isNotNull()
            assertThat(instances.second.retryAfter("alice", "10.0.0.9"))
                .describedAs("a second factor failure does not delay the password of the same name")
                .isNull()
            assertThat(instances.store.findAttempt(LoginAttemptLimiter.accountKey("alice")))
                .describedAs("the password account row is not written by second factor failures")
                .isNull()
        }
    }

}
