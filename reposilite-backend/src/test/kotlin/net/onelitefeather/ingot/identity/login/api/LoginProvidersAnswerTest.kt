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

package net.onelitefeather.ingot.identity.login.api

import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MfaSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The `passkeyFirstFactor` boolean of `GET /api/auth/providers`, design D10: false unless `login.mfa` enables it. */
internal class LoginProvidersAnswerTest {

    private fun answerWith(settings: LoginSettings): LoginProvidersResponse =
        loginProvidersAnswer(LoginFacade(service = null, sessions = null, ownTokens = null, settings = { settings }))

    @Test
    fun `announces the passkey first factor as false when the settings are at their defaults`() {
        val answer = answerWith(LoginSettings())

        assertThat(answer.passkeyFirstFactor).describedAs("passkeyFirstFactor with default settings").isFalse()
    }

    @Test
    fun `announces the passkey first factor as true when login mfa enables it`() {
        val answer = answerWith(LoginSettings(mfa = MfaSettings(passkeyFirstFactor = true)))

        assertThat(answer.passkeyFirstFactor).describedAs("passkeyFirstFactor with the switch on").isTrue()
    }

    @Test
    fun `announces the passkey first factor as false when only the second factor policy is set`() {
        val answer = answerWith(LoginSettings(mfa = MfaSettings(requiredForAdmins = true, graceDays = 0)))

        assertThat(answer.passkeyFirstFactor).describedAs("passkeyFirstFactor with a policy but no switch").isFalse()
    }
}
