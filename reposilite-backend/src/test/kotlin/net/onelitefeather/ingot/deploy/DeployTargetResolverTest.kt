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
 * The resolver is a pure function: every source is passed in as a value, so no test reads the environment, the JVM
 * properties or the clock.
 */
internal class DeployTargetResolverTest {

    @Test
    fun `the default target is all when no source is set`() {
        // when: no target source is set and the legacy switch is on
        val resolution = resolve()

        // then: the server serves the dashboard as it always did
        assertThat(resolution.target).describedAs("default target").isEqualTo(DeployTarget.ALL)
    }

    @Test
    fun `the command line accepts all`() {
        assertThat(resolve(commandLine = "all").target).isEqualTo(DeployTarget.ALL)
    }

    @Test
    fun `the command line accepts server`() {
        assertThat(resolve(commandLine = "server").target).isEqualTo(DeployTarget.SERVER)
    }

    @Test
    fun `the system property accepts server`() {
        assertThat(resolve(properties = mapOf("ingot.target" to "server")).target).isEqualTo(DeployTarget.SERVER)
    }

    @Test
    fun `the environment variable accepts all`() {
        assertThat(resolve(environment = mapOf("INGOT_TARGET" to "all")).target).isEqualTo(DeployTarget.ALL)
    }

    @Test
    fun `the environment variable accepts server`() {
        assertThat(resolve(environment = mapOf("INGOT_TARGET" to "server")).target).isEqualTo(DeployTarget.SERVER)
    }

    @Test
    fun `the command line beats the system property and the environment variable`() {
        // when: all three sources are set and they disagree
        val resolution = resolve(
            commandLine = "all",
            properties = mapOf("ingot.target" to "server"),
            environment = mapOf("INGOT_TARGET" to "server"),
        )

        // then: the command line wins
        assertThat(resolution.target).describedAs("effective target").isEqualTo(DeployTarget.ALL)
    }

    @Test
    fun `the system property beats the environment variable`() {
        // when: the property and the environment variable disagree
        val resolution = resolve(
            properties = mapOf("ingot.target" to "all"),
            environment = mapOf("INGOT_TARGET" to "server"),
        )

        // then: the property wins
        assertThat(resolution.target).describedAs("effective target").isEqualTo(DeployTarget.ALL)
    }

    @Test
    fun `a blank environment variable counts as not set`() {
        // when: the variable is present but empty and nothing else is set, and the legacy switch is on
        val resolution = resolve(environment = mapOf("INGOT_TARGET" to ""))

        // then: the default applies
        assertThat(resolution.target).describedAs("effective target").isEqualTo(DeployTarget.ALL)
        assertThat(resolution.warnings).describedAs("warnings").isEmpty()
    }

    @Test
    fun `a blank environment variable does not hide a legacy server switch`() {
        // when: the variable is empty and the legacy switch asks for the server only
        val resolution = resolve(environment = mapOf("INGOT_TARGET" to ""), legacyDefaultFrontend = false)

        // then: the legacy switch decides, as if the variable were absent
        assertThat(resolution.target).describedAs("effective target").isEqualTo(DeployTarget.SERVER)
    }

    @Test
    fun `an unknown environment value is refused and the message names the variable and both values`() {
        // when / then: the misspelled value stops the resolution instead of falling back to a target
        assertThatIllegalArgumentException()
            .isThrownBy { resolve(environment = mapOf("INGOT_TARGET" to "sever")) }
            .withMessageContaining("INGOT_TARGET")
            .withMessageContaining("all")
            .withMessageContaining("server")
    }

    @Test
    fun `an unknown command line value is refused and the message names the option`() {
        assertThatIllegalArgumentException()
            .isThrownBy { resolve(commandLine = "sever") }
            .withMessageContaining("--target")
            .withMessageContaining("server")
    }

    @Test
    fun `an unknown value is refused even when the legacy switch would have chosen a target`() {
        // when / then: the refusal wins over the legacy alias, so a typo never publishes the dashboard by accident
        assertThatIllegalArgumentException()
            .isThrownBy { resolve(properties = mapOf("ingot.target" to "sever"), legacyDefaultFrontend = false) }
            .withMessageContaining("ingot.target")
    }

    @Test
    fun `the legacy switch alone means server`() {
        // when: no target source is set and the legacy switch is off
        val resolution = resolve(legacyDefaultFrontend = false)

        // then: the instance serves the API only, and nothing needs a warning
        assertThat(resolution.target).describedAs("effective target").isEqualTo(DeployTarget.SERVER)
        assertThat(resolution.warnings).describedAs("warnings").isEmpty()
    }

    @Test
    fun `a target of all wins over a legacy switch that is off, with exactly one warning`() {
        // when: the target asks for the dashboard and the legacy switch asks for none
        val resolution = resolve(commandLine = "all", legacyDefaultFrontend = false)

        // then: the target decides and the disagreement is reported once, naming both values
        assertThat(resolution.target).describedAs("effective target").isEqualTo(DeployTarget.ALL)
        assertThat(resolution.warnings).describedAs("warnings").hasSize(1)
        assertThat(resolution.warnings.single())
            .describedAs("warning text")
            .contains("all")
            .contains("defaultFrontend=false")
    }

    @Test
    fun `agreement between the target and the legacy switch logs nothing`() {
        // when: the target is server and the legacy switch is off
        val resolution = resolve(environment = mapOf("INGOT_TARGET" to "server"), legacyDefaultFrontend = false)

        // then: both say the same thing, so there is no warning
        assertThat(resolution.target).describedAs("effective target").isEqualTo(DeployTarget.SERVER)
        assertThat(resolution.warnings).describedAs("warnings").isEmpty()
    }

    @Test
    fun `a target of server with the legacy switch at its default logs nothing`() {
        // when: the legacy switch is at its default, which is not a statement about the target
        val resolution = resolve(commandLine = "server", legacyDefaultFrontend = true)

        // then: the target decides silently, so a plain upgrade does not print a warning on every start
        assertThat(resolution.target).describedAs("effective target").isEqualTo(DeployTarget.SERVER)
        assertThat(resolution.warnings).describedAs("warnings").isEmpty()
    }

    private fun resolve(
        commandLine: String? = null,
        properties: Map<String, String> = emptyMap(),
        environment: Map<String, String> = emptyMap(),
        legacyDefaultFrontend: Boolean = true,
    ): DeployTargetResolution = DeployTargetResolver.resolve(
        commandLine = commandLine,
        properties = properties,
        environment = environment,
        legacyDefaultFrontend = legacyDefaultFrontend,
    )

}
