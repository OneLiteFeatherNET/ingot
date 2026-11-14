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

package net.onelitefeather.ingot.identity.login.mfa

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

internal class SecondFactorStoreTest {

    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `confirming a pending enrolment issues recovery codes only once`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val principal = IdentityStore(connection.database, clock)
                .createPrincipal(PrincipalKind.USER, "erin", "Erin", false, clock.instant())!!
            val store = SecondFactorStore(connection.database, clock)
            store.insertPendingTotp(principal.id, "factor-key-1", "Authenticator app", "sealed", clock.instant())
            val pending = checkNotNull(store.findPendingTotp(principal.id))

            val first = store.confirmWithRecoveryCodes(pending.id, clock.instant(), step = 1, recoveryHashes = listOf("first-set"))
            val second = store.confirmWithRecoveryCodes(pending.id, clock.instant(), step = 2, recoveryHashes = listOf("second-set"))

            assertThat(first).`as`("first confirmation").isTrue()
            assertThat(second).`as`("second confirmation of the same enrolment").isFalse()
            assertThat(store.claimRecoveryCode(principal.id, "first-set", clock.instant())).`as`("code of the first set still works").isTrue()
            assertThat(store.claimRecoveryCode(principal.id, "second-set", clock.instant())).`as`("code of the refused set is not issued").isFalse()
        }
    }

}
