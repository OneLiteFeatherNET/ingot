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

import net.onelitefeather.ingot.identity.login.oidc.OidcProviderSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The `login.mfa` settings: every policy is off by default, and the values are checked on a write. */
internal class MfaSettingsTest {

    @Test
    fun `every policy is off by default`() {
        val mfa = LoginSettings().mfa

        assertThat(mfa.requiredForAdmins).describedAs("requiredForAdmins").isFalse()
        assertThat(mfa.requiredForTeams).describedAs("requiredForTeams").isEmpty()
        assertThat(mfa.passkeyFirstFactor).describedAs("passkeyFirstFactor").isFalse()
    }

    @Test
    fun `the default grace period is fourteen days and the default step-up window five minutes`() {
        val mfa = LoginSettings().mfa

        assertThat(mfa.graceDays).describedAs("graceDays").isEqualTo(14L)
        assertThat(mfa.stepUpMinutes).describedAs("stepUpMinutes").isEqualTo(5L)
    }

    @Test
    fun `the default settings are accepted and give no warning`() {
        assertThat(LoginSettings().violations()).isEmpty()
        assertThat(LoginSettings().warnings()).isEmpty()
    }

    @Test
    fun `a grace period of 365 days is accepted`() {
        assertThat(LoginSettings(mfa = MfaSettings(graceDays = 365)).violations()).isEmpty()
    }

    @Test
    fun `a grace period of 366 days is refused`() {
        assertThat(LoginSettings(mfa = MfaSettings(graceDays = 366)).violations()).anyMatch { it.startsWith("mfa.graceDays") }
    }

    @Test
    fun `a negative grace period is refused`() {
        assertThat(LoginSettings(mfa = MfaSettings(graceDays = -1)).violations()).anyMatch { it.startsWith("mfa.graceDays") }
    }

    @Test
    fun `a step-up window of sixty minutes is accepted`() {
        assertThat(LoginSettings(mfa = MfaSettings(stepUpMinutes = 60)).violations()).isEmpty()
    }

    @Test
    fun `a step-up window of zero minutes is refused`() {
        assertThat(LoginSettings(mfa = MfaSettings(stepUpMinutes = 0)).violations()).anyMatch { it.startsWith("mfa.stepUpMinutes") }
    }

    @Test
    fun `a step-up window of sixty one minutes is refused`() {
        assertThat(LoginSettings(mfa = MfaSettings(stepUpMinutes = 61)).violations()).anyMatch { it.startsWith("mfa.stepUpMinutes") }
    }

    @Test
    fun `a blank team name in the policy is refused`() {
        assertThat(LoginSettings(mfa = MfaSettings(requiredForTeams = listOf("ops", " "))).violations())
            .anyMatch { it.startsWith("mfa.requiredForTeams") }
    }

    @Test
    fun `a blank trusted acr value is refused`() {
        val settings = LoginSettings(oidcProviders = listOf(OidcProviderSettings(id = "corp", mfaAcrValues = listOf(""))))

        assertThat(settings.violations()).anyMatch { it.contains("mfaAcrValues") }
    }

    @Test
    fun `a blank trusted amr value is refused`() {
        val settings = LoginSettings(oidcProviders = listOf(OidcProviderSettings(id = "corp", mfaAmrValues = listOf("mfa", " "))))

        assertThat(settings.violations()).anyMatch { it.contains("mfaAmrValues") }
    }

    @Test
    fun `saving the policy for administrators is accepted with a warning about the break-glass token`() {
        val settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true))

        assertThat(settings.violations()).describedAs("the save is not refused").isEmpty()
        assertThat(settings.warnings()).describedAs("warnings of the save").anyMatch { it.contains("--token") }
    }
}
