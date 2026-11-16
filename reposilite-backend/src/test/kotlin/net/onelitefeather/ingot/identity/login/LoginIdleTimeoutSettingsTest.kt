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

internal class LoginIdleTimeoutSettingsTest {

    @Test
    fun `the idle timeout is off by default and the default passes validation`() {
        val settings = LoginSettings()

        assertThat(settings.idleTimeoutMinutes).describedAs("the default keeps the refresh lifetime as the only idle limit").isEqualTo(0L)
        assertThat(settings.violations()).isEmpty()
    }

    @Test
    fun `a timeout of two minutes is refused with a violation on the key`() {
        val violations = LoginSettings(idleTimeoutMinutes = 2).violations()

        assertThat(violations).describedAs("a timeout below five minutes is a settings violation").singleElement()
            .asString().startsWith("idleTimeoutMinutes:")
    }

    @Test
    fun `a negative timeout is refused with a violation on the key`() {
        val violations = LoginSettings(idleTimeoutMinutes = -1).violations()

        assertThat(violations).singleElement().asString().startsWith("idleTimeoutMinutes:")
    }

    @Test
    fun `a timeout of five minutes is accepted`() {
        assertThat(LoginSettings(idleTimeoutMinutes = 5).violations()).isEmpty()
    }

    @Test
    fun `a timeout above the refresh lifetime is refused`() {
        val settings = LoginSettings(refreshLifetimeHours = 2, idleTimeoutMinutes = 121)

        assertThat(settings.violations()).describedAs("the timeout may not exceed the refresh lifetime of 120 minutes").singleElement()
            .asString().startsWith("idleTimeoutMinutes:")
    }

    @Test
    fun `a timeout equal to the refresh lifetime is accepted`() {
        assertThat(LoginSettings(refreshLifetimeHours = 2, idleTimeoutMinutes = 120).violations()).isEmpty()
    }

}
