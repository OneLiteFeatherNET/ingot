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

import java.nio.file.Path
import java.time.Instant
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MfaSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore.Companion.KIND_WEBAUTHN_LOGIN
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The passkey as a first factor, design D14: off by default, and a challenge only when the switch allows it. */
internal class PasskeyFirstFactorTest {

    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `the first factor passkey options are refused while the switch is off and store no challenge`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val user = fixture.userWithPassword()
            fixture.addPasskey(user.id)
            fixture.settings = LoginSettings(mfa = MfaSettings(passkeyFirstFactor = false))

            val start = fixture.facade.startPasskeyFirstFactor("alice")

            assertThat(start).describedAs("options with the switch off").isEqualTo(FirstFactorPasskey.Disabled)
            assertThat(fixture.challengeCount()).describedAs("challenges stored with the switch off").isZero()
        }
    }

    @Test
    fun `the first factor passkey options store a passkey challenge while the switch is on`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val user = fixture.userWithPassword()
            fixture.addPasskey(user.id)
            fixture.settings = LoginSettings(mfa = MfaSettings(passkeyFirstFactor = true))

            val start = fixture.facade.startPasskeyFirstFactor("alice") as FirstFactorPasskey.Started

            assertThat(fixture.challengeKindOf(start.challenge.challengeId))
                .describedAs("kind of the stored challenge")
                .isEqualTo(KIND_WEBAUTHN_LOGIN)
        }
    }

    @Test
    fun `an unknown user gets no first factor passkey challenge`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.settings = LoginSettings(mfa = MfaSettings(passkeyFirstFactor = true))

            val start = fixture.facade.startPasskeyFirstFactor("nobody")

            assertThat(start).describedAs("options of an unknown user").isEqualTo(FirstFactorPasskey.Unavailable)
            assertThat(fixture.challengeCount()).describedAs("challenges stored for an unknown user").isZero()
        }
    }

    @Test
    fun `a user without a passkey gets no first factor passkey challenge`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.userWithPassword()
            fixture.settings = LoginSettings(mfa = MfaSettings(passkeyFirstFactor = true))

            val start = fixture.facade.startPasskeyFirstFactor("alice")

            assertThat(start).describedAs("options of a user without a passkey").isEqualTo(FirstFactorPasskey.Unavailable)
            assertThat(fixture.challengeCount()).describedAs("challenges stored for a user without a passkey").isZero()
        }
    }
}
