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

import com.reposilite.configuration.application.ConfigurationComponents
import com.reposilite.configuration.shared.application.SharedConfigurationComponents
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * With the database as the shared source the switch has no effect, and the instance says so once at start.
 */
internal class SharedConfigurationReloadWithoutFileTest {

    @TempDir
    lateinit var workingDirectory: Path

    private fun startWithoutFile(journalist: InMemoryLogger, sharedConfigurationReload: Boolean) {
        SharedConfigurationComponents(
            journalist = journalist,
            workingDirectory = workingDirectory,
            extensions = Extensions(journalist),
            sharedConfigurationPath = null,
            failureFacade = FailureFacade(journalist),
            configurationFacade = ConfigurationComponents().configurationFacade(),
            sharedConfigurationReload = sharedConfigurationReload,
        ).sharedConfigurationFacade(
            sharedSettingsProvider = SharedSettingsProvider(emptyMap()),
        )
    }

    private fun warningsNaming(journalist: InMemoryLogger): List<String> =
        journalist.messages
            .filter { it.key == Channel.WARN }
            .map { it.value }
            .filter { it.contains("INGOT_SHARED_CONFIGURATION_RELOAD") }

    @Test
    fun `the switch without a shared file logs one warning that it has no effect`() {
        // given: an instance that uses the database source and has the switch on
        val journalist = InMemoryLogger()

        // when: the instance starts
        startWithoutFile(journalist, sharedConfigurationReload = true)

        // then: exactly one warning names the switch and says it has no effect
        val warnings = warningsNaming(journalist)
        assertThat(warnings).describedAs("warnings naming the switch").hasSize(1)
        assertThat(warnings.single()).describedAs("warning text").contains("no effect")
    }

    @Test
    fun `the database source logs no warning about the switch when the switch is off`() {
        // given: an instance that uses the database source and has the switch off
        val journalist = InMemoryLogger()

        // when: the instance starts
        startWithoutFile(journalist, sharedConfigurationReload = false)

        // then: no warning names the switch
        assertThat(warningsNaming(journalist)).describedAs("warnings naming the switch").isEmpty()
    }
}
