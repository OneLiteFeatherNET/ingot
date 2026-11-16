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
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Instant
import net.onelitefeather.ingot.identity.login.LoginAttemptLimiter
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.storage.FactorRow
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference

/**
 * The re-sealing of authenticator seeds when the settings key rotates. The configured keys are a mutable list, so a test can
 * rotate them the way an operator does: set the new key, keep the previous one for a while, then remove it.
 */
internal class SecondFactorSeedResealerTest {

    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"))
    private val oldKey = ByteArray(SettingsEnvelopeCodec.KEY_LENGTH) { (it + 1).toByte() }
    private val newKey = ByteArray(SettingsEnvelopeCodec.KEY_LENGTH) { (it * 3 + 7).toByte() }
    private val unknownKey = ByteArray(SettingsEnvelopeCodec.KEY_LENGTH) { (it * 5 + 9).toByte() }
    private val seed = ByteArray(TotpAlgorithm.SEED_BYTES) { (it * 7 + 1).toByte() }

    @TempDir
    lateinit var workingDirectory: Path

    private var configured: List<SettingsEnvelopeCodec> = emptyList()

    private fun codec(key: ByteArray) = SettingsEnvelopeCodec(key = key)

    private fun seeds() = SettingsSeedCipher(codecs = { configured })

    private class Stores(val database: Database, val identities: IdentityStore, val factors: SecondFactorStore)

    private fun withStores(block: (Stores) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            block(Stores(connection.database, IdentityStore(connection.database, clock), SecondFactorStore(connection.database, clock)))
        }
    }

    private fun Stores.principal(name: String): Int =
        identities.createPrincipal(PrincipalKind.USER, name, name, false, clock.instant())!!.id

    /** Stores a confirmed authenticator whose seed is sealed with [key], as an instance that had [key] active would have. */
    private fun SecondFactorStore.storeConfirmedSeed(principalId: Int, key: ByteArray, factorKey: String): FactorRow {
        val envelope = SettingsSeedCipher(codecs = { listOf(codec(key)) }).seal(factorKey, seed)
        insertPendingTotp(principalId, factorKey, "Authenticator", envelope, clock.instant())
        val pending = checkNotNull(findPendingTotp(principalId))
        confirm(pending.id, clock.instant(), 0)
        return checkNotNull(findFactors(principalId).single { it.id == pending.id })
    }

    private fun resealer(factors: SecondFactorStore, batchSize: Int = 100) =
        SecondFactorSeedResealer(store = factors, seeds = seeds(), batchSize = batchSize)

    private fun secondFactorService(stores: Stores): SecondFactorService {
        val credentials = LoginStore(stores.database, clock)
        return SecondFactorService(
            store = stores.factors,
            identities = stores.identities,
            limiter = LoginAttemptLimiter(credentials, Reference.reference(LoginSettings()), clock),
            seeds = seeds(),
            clock = clock,
            audit = {},
            random = SecureRandom(),
        )
    }

    @Test
    fun `a seed sealed under the previous key opens under the active key after the reseal`() {
        withStores { stores ->
            // given: a seed sealed while the old key was active, and both keys configured after the rotation
            val principalId = stores.principal("alice")
            stores.factors.storeConfirmedSeed(principalId, oldKey, "factor-alice")
            configured = listOf(codec(newKey), codec(oldKey))

            // when
            val report = resealer(stores.factors).reseal()

            // then: the seed is sealed under the new key and still opens with it
            assertThat(report.resealed).describedAs("resealed seeds").isEqualTo(1)
            val row = stores.factors.findFactors(principalId).single()
            assertThat(row.secret).describedAs("sealed seed").startsWith("enc:v1:${codec(newKey).keyId}:")
            assertThat(seeds().open(row.factorKey, checkNotNull(row.secret))).describedAs("opened seed").isEqualTo(seed)
        }
    }

    @Test
    fun `a seed of the previous key still verifies after the previous key is removed`() {
        withStores { stores ->
            // given: an enrolled authenticator sealed with the old key, rotated to the new key
            val principalId = stores.principal("alice")
            stores.factors.storeConfirmedSeed(principalId, oldKey, "factor-alice")
            configured = listOf(codec(newKey), codec(oldKey))
            resealer(stores.factors).reseal()

            // when: the operator removes the previous key and signs in with a current code
            configured = listOf(codec(newKey))
            val service = secondFactorService(stores)
            val challenge = service.startChallenge(principalId)
            val code = TotpAlgorithm.code(seed, clock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS)
            val outcome = service.completeChallenge(challenge, code, IP)

            // then
            assertThat(outcome).describedAs("sign-in outcome").isInstanceOf(ChallengeCompletion.Verified::class.java)
        }
    }

    @Test
    fun `a seed that the active key already seals is left alone`() {
        withStores { stores ->
            // given: a seed sealed with the active key, and the previous key still configured
            val principalId = stores.principal("alice")
            val before = stores.factors.storeConfirmedSeed(principalId, newKey, "factor-alice").secret
            configured = listOf(codec(newKey), codec(oldKey))

            // when
            val report = resealer(stores.factors).reseal()

            // then: nothing is written, and the envelope keeps its bytes
            assertThat(report.resealed).describedAs("resealed seeds").isZero()
            assertThat(report.current).describedAs("seeds already under the active key").isEqualTo(1)
            assertThat(stores.factors.findFactors(principalId).single().secret).describedAs("stored seed").isEqualTo(before)
        }
    }

    @Test
    fun `a seed that no configured key opens is reported and left unchanged`() {
        withStores { stores ->
            // given: a seed sealed with a key that is no longer configured
            val principalId = stores.principal("alice")
            val row = stores.factors.storeConfirmedSeed(principalId, unknownKey, "factor-alice")
            configured = listOf(codec(newKey), codec(oldKey))

            // when
            val report = resealer(stores.factors).reseal()

            // then: the seed is reported by its factor id, its envelope is unchanged and the factor is still there
            assertThat(report.resealed).describedAs("resealed seeds").isZero()
            assertThat(report.unreadable).describedAs("unreadable seeds").containsExactly("second factor ${row.id}")
            val stored = stores.factors.findFactors(principalId)
            assertThat(stored).describedAs("stored factors").hasSize(1)
            assertThat(stored.single().secret).describedAs("stored seed").isEqualTo(row.secret)
        }
    }

    @Test
    fun `a pending enrolment is re-sealed as well`() {
        withStores { stores ->
            // given: an enrolment that waits for its first code, sealed with the old key
            val principalId = stores.principal("alice")
            val envelope = SettingsSeedCipher(codecs = { listOf(codec(oldKey)) }).seal("pending-factor", seed)
            stores.factors.insertPendingTotp(principalId, "pending-factor", "Authenticator", envelope, clock.instant())
            configured = listOf(codec(newKey), codec(oldKey))

            // when
            val report = resealer(stores.factors).reseal()

            // then
            assertThat(report.resealed).describedAs("resealed seeds").isEqualTo(1)
            val pending = checkNotNull(stores.factors.findPendingTotp(principalId))
            assertThat(seeds().open("pending-factor", checkNotNull(pending.secret))).describedAs("opened seed").isEqualTo(seed)
        }
    }

    @Test
    fun `every seed is re-sealed across batches, a batch of one included`() {
        withStores { stores ->
            // given: three seeds of the old key, two of them for one principal
            val alice = stores.principal("alice")
            val bob = stores.principal("bob")
            stores.factors.storeConfirmedSeed(alice, oldKey, "first")
            stores.factors.storeConfirmedSeed(alice, oldKey, "second")
            stores.factors.storeConfirmedSeed(bob, oldKey, "third")
            configured = listOf(codec(newKey), codec(oldKey))

            // when
            val report = resealer(stores.factors, batchSize = 1).reseal()

            // then
            assertThat(report.resealed).describedAs("resealed seeds").isEqualTo(3)
            assertThat(report.current).describedAs("seeds already under the active key").isZero()
            configured = listOf(codec(newKey))
            val rows = stores.factors.findFactors(alice) + stores.factors.findFactors(bob)
            assertThat(rows).describedAs("stored factors").hasSize(3)
            rows.forEach { row ->
                assertThat(seeds().open(row.factorKey, checkNotNull(row.secret))).describedAs("opened seed of ${row.factorKey}").isEqualTo(seed)
            }
        }
    }

    private companion object {
        const val IP = "192.0.2.10"
    }

}
