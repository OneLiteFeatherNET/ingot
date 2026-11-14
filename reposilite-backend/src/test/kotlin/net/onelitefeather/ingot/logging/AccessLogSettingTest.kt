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

package net.onelitefeather.ingot.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AccessLogSettingTest {

    @Test
    fun `should be off when no access log variable is set`() {
        assertThat(AccessLogSetting.read(emptyMap()).enabled).describedAs("access log without a variable").isFalse()
    }

    @Test
    fun `should be on when INGOT_ACCESS_LOG is true`() {
        assertThat(AccessLogSetting.read(mapOf("INGOT_ACCESS_LOG" to "true")).enabled)
            .describedAs("access log with INGOT_ACCESS_LOG=true").isTrue()
    }

    @Test
    fun `should fall back to the legacy spelling when the ingot one is unset`() {
        assertThat(AccessLogSetting.read(mapOf("REPOSILITE_ACCESS_LOG" to "true")).enabled)
            .describedAs("access log with REPOSILITE_ACCESS_LOG=true").isTrue()
    }

    @Test
    fun `should let the ingot variable win over the legacy one`() {
        assertThat(AccessLogSetting.read(mapOf("INGOT_ACCESS_LOG" to "false", "REPOSILITE_ACCESS_LOG" to "true")).enabled)
            .describedAs("access log with INGOT_ACCESS_LOG=false and REPOSILITE_ACCESS_LOG=true").isFalse()
    }

    @Test
    fun `should stay off for any other value without refusing the start`() {
        assertThat(AccessLogSetting.read(mapOf("INGOT_ACCESS_LOG" to "yes")).enabled)
            .describedAs("access log with INGOT_ACCESS_LOG=yes").isFalse()
    }

}
