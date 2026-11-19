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
package com.reposilite.configuration.local

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test

internal class PluginReadersSwitchTest {

    @Test
    fun `should leave the plugin readers switch on by default`() {
        // when: a local configuration is created without any setting
        val switch = LocalConfiguration().pluginReaders.get()

        // then: the readers are on, because installing a plugin that registers a reader page is the opt-in
        assertThat(switch).describedAs("plugin readers switch").isTrue()
    }

    @Test
    fun `should read the plugin readers switch from the ingot prefix`() {
        // when: the switch is set with the INGOT_LOCAL_ prefix
        val properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("INGOT_LOCAL_PLUGINREADERS" to "false"))

        // then: it is exposed under the property name of pluginReaders
        assertThat(properties).describedAs("properties").containsExactly(entry("PLUGINREADERS", "false"))
    }

    @Test
    fun `should still read the plugin readers switch from the legacy reposilite prefix`() {
        // when: the switch is set with the REPOSILITE_LOCAL_ prefix an existing deployment was set up with
        val properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("REPOSILITE_LOCAL_PLUGINREADERS" to "false"))

        // then: it is still honoured
        assertThat(properties).describedAs("properties").containsExactly(entry("PLUGINREADERS", "false"))
    }

    @Test
    fun `should prefer the ingot prefix over the legacy reposilite prefix`() {
        // when: both prefixes are set to different values
        val properties = LocalConfigurationFactory.getEnvironmentVariables(
            mapOf("INGOT_LOCAL_PLUGINREADERS" to "false", "REPOSILITE_LOCAL_PLUGINREADERS" to "true")
        )

        // then: the ingot value wins
        assertThat(properties).describedAs("properties").containsExactly(entry("PLUGINREADERS", "false"))
    }
}
