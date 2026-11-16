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
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference

/**
 * A seed that no configured key opens is logged once per factor, with the factor id and the way out, and never with the seed.
 * The log lines are captured through the logger the service is given, not through the console.
 */
internal class SecondFactorSeedLogTest {

    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"))
    private val activeKey = ByteArray(SettingsEnvelopeCodec.KEY_LENGTH) { (it * 11 + 4).toByte() }
    private val unknownKey = ByteArray(SettingsEnvelopeCodec.KEY_LENGTH) { (it * 13 + 6).toByte() }

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `an unopenable seed is logged at error once per factor, with its id and the hint but without the seed`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            // given: an enrolled authenticator whose seed only a key that is no longer configured can open
            val identities = IdentityStore(connection.database, clock)
            val factors = SecondFactorStore(connection.database, clock)
            val principalId = identities.createPrincipal(PrincipalKind.USER, "alice", "alice", false, clock.instant())!!.id
            val envelope = SettingsSeedCipher(codecs = { listOf(SettingsEnvelopeCodec(key = unknownKey)) })
                .seal("factor-alice", ByteArray(32) { 9 })
            factors.insertPendingTotp(principalId, "factor-alice", "Authenticator", envelope, clock.instant())
            val pending = checkNotNull(factors.findPendingTotp(principalId))
            factors.confirm(pending.id, clock.instant(), 0)
            val logged = mutableListOf<String>()
            val service = SecondFactorService(
                store = factors,
                identities = identities,
                limiter = LoginAttemptLimiter(LoginStore(connection.database, clock), Reference.reference(LoginSettings()), clock),
                seeds = SettingsSeedCipher(codecs = { listOf(SettingsEnvelopeCodec(key = activeKey)) }),
                clock = clock,
                audit = {},
                random = SecureRandom(),
                logError = logged::add,
            )

            // when: two sign-ins with a code, which no seed can check
            val challenge = service.startChallenge(principalId)
            service.completeChallenge(challenge, "000000", IP)
            service.completeChallenge(challenge, "000000", IP)

            // then: one error line for the factor, naming the id and the previous key, and not the envelope
            assertThat(logged).describedAs("error lines").hasSize(1)
            assertThat(logged.single())
                .describedAs("error line")
                .contains("second factor ${pending.id}")
                .contains("settings-reencrypt")
                .contains("INGOT_SETTINGS_KEY_PREVIOUS")
                .doesNotContain(checkNotNull(pending.secret))
        }
    }

    private companion object {
        const val IP = "192.0.2.10"
    }

}
