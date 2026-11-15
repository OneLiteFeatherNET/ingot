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
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

/**
 * The setting is a pure function: every source is passed in as a value, so no test reads the environment, the JVM
 * properties or the clock, and every test builds its own inputs.
 */
internal class BackgroundJobsSettingTest {

    @Test
    fun `the background jobs run when no source is set`() {
        assertThat(resolve(target = DeployTarget.SERVER)).describedAs("background jobs").isTrue()
    }

    @Test
    fun `the command line option switches the jobs off on server`() {
        assertThat(resolve(target = DeployTarget.SERVER, commandLine = "false")).isFalse()
    }

    @Test
    fun `the system property switches the jobs off on server`() {
        assertThat(resolve(target = DeployTarget.SERVER, properties = mapOf("ingot.background-jobs" to "false"))).isFalse()
    }

    @Test
    fun `the environment variable switches the jobs off on server`() {
        assertThat(resolve(target = DeployTarget.SERVER, environment = mapOf("INGOT_BACKGROUND_JOBS" to "false"))).isFalse()
    }

    @Test
    fun `the command line beats the system property and the environment variable`() {
        // when: the option says true while the other sources say false
        val enabled = resolve(
            target = DeployTarget.SERVER,
            commandLine = "true",
            properties = mapOf("ingot.background-jobs" to "false"),
            environment = mapOf("INGOT_BACKGROUND_JOBS" to "false"),
        )

        // then: the command line wins
        assertThat(enabled).describedAs("background jobs").isTrue()
    }

    @Test
    fun `the system property beats the environment variable`() {
        // when: the property says true while the variable says false
        val enabled = resolve(
            target = DeployTarget.SERVER,
            properties = mapOf("ingot.background-jobs" to "true"),
            environment = mapOf("INGOT_BACKGROUND_JOBS" to "false"),
        )

        // then: the property wins
        assertThat(enabled).describedAs("background jobs").isTrue()
    }

    @Test
    fun `a blank environment variable counts as not set`() {
        assertThat(resolve(target = DeployTarget.SERVER, environment = mapOf("INGOT_BACKGROUND_JOBS" to ""))).isTrue()
    }

    @Test
    fun `the server accepts true from the environment`() {
        assertThat(resolve(target = DeployTarget.SERVER, environment = mapOf("INGOT_BACKGROUND_JOBS" to "true"))).isTrue()
    }

    @Test
    fun `a value that is neither true nor false is refused and the message names the variable and the valid values`() {
        // when / then: a misspelled value stops the start instead of silently keeping the jobs on
        assertThatIllegalArgumentException()
            .isThrownBy { resolve(target = DeployTarget.SERVER, environment = mapOf("INGOT_BACKGROUND_JOBS" to "yes")) }
            .withMessage("Invalid background jobs value 'yes' from INGOT_BACKGROUND_JOBS. Valid values: true, false.")
    }

    @Test
    fun `an invalid command line value is refused and the message names the option`() {
        assertThatIllegalArgumentException()
            .isThrownBy { resolve(target = DeployTarget.SERVER, commandLine = "no") }
            .withMessageContaining("'no' from --background-jobs")
    }

    @Test
    fun `an invalid value is refused on the worker target as well`() {
        assertThatIllegalArgumentException()
            .isThrownBy { resolve(target = DeployTarget.WORKER, properties = mapOf("ingot.background-jobs" to "0")) }
            .withMessageContaining("'0' from ingot.background-jobs")
    }

    @Test
    fun `false is refused on target all with one line naming the variable`() {
        assertThatIllegalArgumentException()
            .isThrownBy { resolve(target = DeployTarget.ALL, environment = mapOf("INGOT_BACKGROUND_JOBS" to "false")) }
            .withMessage("INGOT_BACKGROUND_JOBS=false is only valid on target server.")
    }

    @Test
    fun `false is refused on target worker with one line naming the variable`() {
        assertThatIllegalArgumentException()
            .isThrownBy { resolve(target = DeployTarget.WORKER, environment = mapOf("INGOT_BACKGROUND_JOBS" to "false")) }
            .withMessage("INGOT_BACKGROUND_JOBS=false is only valid on target server.")
    }

    @Test
    fun `true is accepted on target all and on target worker`() {
        assertThat(resolve(target = DeployTarget.ALL, commandLine = "true")).isTrue()
        assertThat(resolve(target = DeployTarget.WORKER, commandLine = "true")).isTrue()
    }

    @Test
    fun `a contradictory setting becomes a one line startup refusal with exit code 1`() {
        // when: false is set from the environment on the worker
        val refusal = runCatching { resolve(target = DeployTarget.WORKER, environment = mapOf("INGOT_BACKGROUND_JOBS" to "false")) }
            .exceptionOrNull() as BackgroundJobsSettingException

        // then: the launcher prints one line and exits with the failed start code
        assertThat(refusal.toStartupRefusal()).isEqualTo(
            StartupRefusal(
                line = "Ingot did not start: INGOT_BACKGROUND_JOBS=false is only valid on target server.",
                exitCode = 1,
            )
        )
    }

    private fun resolve(
        target: DeployTarget,
        commandLine: String? = null,
        properties: Map<String, String> = emptyMap(),
        environment: Map<String, String> = emptyMap(),
    ): Boolean = BackgroundJobsSetting.resolve(
        target = target,
        commandLine = commandLine,
        properties = properties,
        environment = environment,
    )

}
