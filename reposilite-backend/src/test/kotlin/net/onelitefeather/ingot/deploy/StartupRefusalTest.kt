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

package net.onelitefeather.ingot.deploy

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test

internal class StartupRefusalTest {

    @Test
    fun `an unknown target is refused with exit code 1`() {
        // given: the resolver refuses a misspelled target
        val refusal = refusalOf(environment = mapOf("INGOT_TARGET" to "sever"))

        // then: the process ends with the exit code of a failed start
        assertThat(refusal.exitCode).describedAs("exit code").isEqualTo(1)
    }

    @Test
    fun `an unknown target is reported as one console line that names the source and the valid values`() {
        // given: the resolver refuses a misspelled target
        val refusal = refusalOf(environment = mapOf("INGOT_TARGET" to "sever"))

        // then: the line fits on one console line and says what is wrong and what is valid
        assertThat(refusal.line).describedAs("console line").doesNotContain("\n")
        assertThat(refusal.line)
            .describedAs("console line")
            .startsWith("Ingot did not start:")
            .contains("'sever'")
            .contains("INGOT_TARGET")
            .contains("all, server")
    }

    @Test
    fun `an unknown command line target is reported with the option that carried it`() {
        val refusal = refusalOf(commandLine = "sever")

        assertThat(refusal.line).describedAs("console line").contains("--target")
    }

    private fun refusalOf(
        commandLine: String? = null,
        environment: Map<String, String> = emptyMap(),
    ): StartupRefusal {
        val thrown = catchThrowable {
            DeployTargetResolver.resolve(
                commandLine = commandLine,
                properties = emptyMap(),
                environment = environment,
                legacyDefaultFrontend = true,
            )
        }
        assertThat(thrown).describedAs("resolver refusal").isInstanceOf(UnknownDeployTargetException::class.java)
        return (thrown as UnknownDeployTargetException).toStartupRefusal()
    }

}
