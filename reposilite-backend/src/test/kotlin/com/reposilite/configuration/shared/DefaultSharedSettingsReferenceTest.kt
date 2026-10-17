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

package com.reposilite.configuration.shared

import com.reposilite.frontend.application.AppearanceSettings
import com.reposilite.frontend.application.FrontendSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.function.Supplier

internal class DefaultSharedSettingsReferenceTest {

    private fun reference(applied: MutableList<FrontendSettings>): DefaultSharedSettingsReference<FrontendSettings> =
        DefaultSharedSettingsReference(
            type = FrontendSettings::class.java,
            schema = Supplier { ByteArrayInputStream(ByteArray(0)) },
            getter = { FrontendSettings() },
            setter = { applied += it }
        )

    @Test
    fun `should refuse an update with a malformed colour and leave the settings untouched`() {
        // given: a reference that records every value it is asked to store
        val applied = mutableListOf<FrontendSettings>()

        // when: an update with a colour that is not six digit hex arrives
        val result = reference(applied).update(FrontendSettings(appearance = AppearanceSettings(seedColor = "red")))

        // then: the update is rejected with a message naming the field, and nothing is stored
        assertThat(result.isErr).isTrue
        assertThat(result.error.message).describedAs("error message").contains("appearance.seedColor")
        assertThat(applied).isEmpty()
    }

    @Test
    fun `should report a rejected update as its rules only, without an exception name or a prefix`() {
        // given: a reference that records every value it is asked to store
        val applied = mutableListOf<FrontendSettings>()

        // when: an update with a colour that is not six digit hex arrives
        val result = reference(applied).update(FrontendSettings(appearance = AppearanceSettings(seedColor = "red")))

        // then: the failure carries the rules as a list, and its message is the first rule as written
        val error = result.error
        assertThat(error).isInstanceOf(SettingsViolationException::class.java)
        assertThat((error as SettingsViolationException).violations).singleElement().asString().startsWith("appearance.seedColor")
        assertThat(error.message).describedAs("message of the rejection")
            .startsWith("appearance.seedColor")
            .doesNotContain("java.lang")
            .doesNotContain("Invalid")
    }

    @Test
    fun `should store an update with valid appearance settings`() {
        // given: a reference that records every value it is asked to store
        val applied = mutableListOf<FrontendSettings>()
        val update = FrontendSettings(appearance = AppearanceSettings(seedColor = "#1A2B3C"))

        // when: the update is applied
        val result = reference(applied).update(update)

        // then: the update succeeds and is stored unchanged
        assertThat(result.isOk).isTrue
        assertThat(applied).containsExactly(update)
    }

}
