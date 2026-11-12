package com.reposilite.configuration.local

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test

internal class PluginStylesSwitchTest {

    @Test
    fun `should read the plugin styles switch from the ingot prefix`() {
        // when: the switch is set with the INGOT_LOCAL_ prefix
        val properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("INGOT_LOCAL_PLUGINSTYLES" to "false"))

        // then: it is exposed under the property name of pluginStyles
        assertThat(properties).describedAs("properties").containsExactly(entry("PLUGINSTYLES", "false"))
    }

    @Test
    fun `should still read the plugin styles switch from the legacy reposilite prefix`() {
        // when: the switch is set with the REPOSILITE_LOCAL_ prefix an existing deployment was set up with
        val properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("REPOSILITE_LOCAL_PLUGINSTYLES" to "false"))

        // then: it is still honoured
        assertThat(properties).describedAs("properties").containsExactly(entry("PLUGINSTYLES", "false"))
    }
}
