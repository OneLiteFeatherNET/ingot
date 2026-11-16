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

import net.onelitefeather.ingot.identity.session.SessionScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The routes a restricted session (scope enrol, design D10) may call. Every other route answers 403 mfa_enrolment_required. */
internal class EnrolmentRoutesTest {

    private fun refused(method: String, path: String, scope: SessionScope = SessionScope.ENROL): Boolean =
        EnrolmentRoutes.refused(scope, method, path)

    @Test
    fun `a restricted session may list its factors`() {
        assertThat(refused("GET", "/api/identity/me/factors")).isFalse()
    }

    @Test
    fun `a restricted session may start and confirm a TOTP enrolment`() {
        assertThat(refused("POST", "/api/identity/me/factors/totp")).isFalse()
        assertThat(refused("POST", "/api/identity/me/factors/totp/confirm")).isFalse()
    }

    @Test
    fun `a restricted session may start and finish a passkey registration`() {
        assertThat(refused("POST", "/api/identity/me/factors/passkey/options")).isFalse()
        assertThat(refused("POST", "/api/identity/me/factors/passkey")).isFalse()
    }

    @Test
    fun `a restricted session may remove a factor and regenerate the recovery codes`() {
        assertThat(refused("DELETE", "/api/identity/me/factors/{id}")).isFalse()
        assertThat(refused("POST", "/api/identity/me/recovery-codes")).isFalse()
    }

    @Test
    fun `a restricted session may rename a factor`() {
        assertThat(refused("PATCH", "/api/identity/me/factors/{id}")).isFalse()
    }

    @Test
    fun `a restricted session may log out`() {
        assertThat(refused("POST", "/api/auth/logout")).isFalse()
    }

    @Test
    fun `a restricted session may renew itself, which is where the scope changes`() {
        assertThat(refused("POST", "/api/auth/refresh")).isFalse()
    }

    @Test
    fun `a restricted session may not create an access token`() {
        assertThat(refused("POST", "/api/identity/me/tokens")).isTrue()
    }

    @Test
    fun `a restricted session may read the security status`() {
        assertThat(refused("GET", "/api/identity/me/security")).isFalse()
    }

    @Test
    fun `a restricted session may not call the step-up route`() {
        assertThat(refused("POST", "/api/identity/me/step-up")).isTrue()
    }

    @Test
    fun `a full session is never refused by the enrolment routes`() {
        assertThat(refused("POST", "/api/identity/me/tokens", scope = SessionScope.FULL)).isFalse()
    }
}
