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
package net.onelitefeather.ingot.identity.storage

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

internal class AuthorizationRefreshSettingTest {

    @Test
    fun `the refresh interval is two seconds when neither variable is set`() {
        assertThat(AuthorizationRevision.refreshInterval(emptyMap())).isEqualTo(Duration.ofSeconds(2))
    }

    @Test
    fun `the refresh interval is read from the INGOT variable`() {
        assertThat(AuthorizationRevision.refreshInterval(mapOf("INGOT_AUTHORIZATION_REFRESH" to "5")))
            .isEqualTo(Duration.ofSeconds(5))
    }

    @Test
    fun `the refresh interval falls back to the REPOSILITE variable`() {
        assertThat(AuthorizationRevision.refreshInterval(mapOf("REPOSILITE_AUTHORIZATION_REFRESH" to "7")))
            .isEqualTo(Duration.ofSeconds(7))
    }

    @Test
    fun `the INGOT variable takes precedence over the REPOSILITE variable`() {
        val environment = mapOf("INGOT_AUTHORIZATION_REFRESH" to "3", "REPOSILITE_AUTHORIZATION_REFRESH" to "9")

        assertThat(AuthorizationRevision.refreshInterval(environment)).isEqualTo(Duration.ofSeconds(3))
    }

    @Test
    fun `a refresh interval that is not a positive number of seconds is refused`() {
        assertThatThrownBy { AuthorizationRevision.refreshInterval(mapOf("INGOT_AUTHORIZATION_REFRESH" to "0")) }
            .describedAs("zero would make the refresh spin")
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
