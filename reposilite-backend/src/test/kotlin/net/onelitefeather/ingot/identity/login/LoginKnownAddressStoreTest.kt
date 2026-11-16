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
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

internal class LoginKnownAddressStoreTest {

    private val start = Instant.parse("2026-10-15T10:00:00Z")
    private val clock = MutableClock(start)
    private val window = Duration.ofDays(30)

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `an address is known within the window and unknown after it`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val store = LoginStore(connection.database, clock)
            store.recordKnownAddress("alice", "hash-a", start)

            assertThat(store.isKnownAddress("alice", "hash-a", start.minus(window).plus(Duration.ofDays(1))))
                .describedAs("a sign-in one day inside the window makes the address known")
                .isTrue()
            assertThat(store.isKnownAddress("alice", "hash-a", start.plus(Duration.ofDays(1))))
                .describedAs("a window that starts after the sign-in does not know the address")
                .isFalse()
        }
    }

    @Test
    fun `knows an address only for the name it signed in with`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val store = LoginStore(connection.database, clock)
            store.recordKnownAddress("alice", "hash-a", start)

            assertThat(store.isKnownAddress("bob", "hash-a", start.minus(window))).isFalse()
            assertThat(store.isKnownAddress("alice", "hash-b", start.minus(window))).isFalse()
        }
    }

    @Test
    fun `a later sign-in moves the time of the last success`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val store = LoginStore(connection.database, clock)
            store.recordKnownAddress("alice", "hash-a", start)
            val later = start.plus(Duration.ofDays(40))
            store.recordKnownAddress("alice", "hash-a", later)

            assertThat(store.isKnownAddress("alice", "hash-a", later.minus(window)))
                .describedAs("the address is known again after a sign-in that came after the old window")
                .isTrue()
        }
    }

}
