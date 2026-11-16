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
import net.onelitefeather.ingot.identity.login.LoginOutcome.Rejected
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

internal class LocalLoginProviderTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `computes the dummy hash once, when the provider is created`() {
        withProvider { _, dummyHashes ->
            assertThat(dummyHashes.get())
                .describedAs("the dummy hash exists before the first login, so no unknown user pays for it")
                .isEqualTo(1)
        }
    }

    @Test
    fun `does not compute a dummy hash while answering an unknown user`() {
        withProvider { provider, dummyHashes ->
            val outcome = provider.authenticate("nobody", "a password of some length")

            assertThat(outcome).isEqualTo(Rejected)
            assertThat(dummyHashes.get())
                .describedAs("the first unknown-user login must cost one verification, not a hash and a verification")
                .isEqualTo(1)
        }
    }

    private fun withProvider(block: (LocalLoginProvider, Counter) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val dummyHashes = Counter()
            val provider = LocalLoginProvider(
                identities = IdentityStore(connection.database, clock),
                credentials = LoginStore(connection.database, clock),
                hasher = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1)),
                dummyHash = { dummyHashes.increment(); "unused" },
            )
            block(provider, dummyHashes)
        }
    }

    private class Counter {
        private var value = 0

        fun increment() {
            value++
        }

        fun get(): Int = value
    }

}
