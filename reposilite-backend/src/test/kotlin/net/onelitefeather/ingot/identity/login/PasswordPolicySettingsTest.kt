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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PasswordPolicySettingsTest {

    @Test
    fun `the default minimum length is 12`() {
        val settings = LoginSettings()

        assertThat(settings.passwordPolicy.minLength).isEqualTo(12)
        assertThat(settings.violations()).isEmpty()
    }

    @Test
    fun `refuses a minimum length below 8 with a violation on the key`() {
        val violations = LoginSettings(passwordPolicy = PasswordPolicySettings(minLength = 7)).violations()

        assertThat(violations).anySatisfy { assertThat(it).contains("passwordPolicy.minLength") }
    }

    @Test
    fun `accepts the minimum length of 8`() {
        assertThat(LoginSettings(passwordPolicy = PasswordPolicySettings(minLength = 8)).violations()).isEmpty()
    }

    @Test
    fun `accepts the maximum length of 256 and refuses 257 as a minimum`() {
        assertThat(LoginSettings(passwordPolicy = PasswordPolicySettings(minLength = 256)).violations()).isEmpty()

        val violations = LoginSettings(passwordPolicy = PasswordPolicySettings(minLength = 257)).violations()

        assertThat(violations).anySatisfy { assertThat(it).contains("passwordPolicy.minLength") }
    }

}
