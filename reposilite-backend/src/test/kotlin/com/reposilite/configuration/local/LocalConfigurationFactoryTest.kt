package com.reposilite.configuration.local

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test

internal class LocalConfigurationFactoryTest {

    @Test
    fun `should apply boolean external property`() {
        // given: a default local configuration
        val localConfiguration = LocalConfiguration()

        // when: a boolean external property is applied
        applyProperty(localConfiguration, "DEBUGENABLED", "true")

        // then: the property is updated
        assertThat(localConfiguration.debugEnabled.get()).isTrue
    }

    @Test
    fun `should apply int external property`() {
        // given: a default local configuration
        val localConfiguration = LocalConfiguration()

        // when: an int external property is applied
        applyProperty(localConfiguration, "PORT", "8181")

        // then: the property is updated
        assertThat(localConfiguration.port.get()).isEqualTo(8181)
    }

    @Test
    fun `should apply long external property`() {
        // given: a default local configuration
        val localConfiguration = LocalConfiguration()

        // when: a long external property above the int range is applied
        applyProperty(localConfiguration, "IDLETIMEOUT", "2147483648")

        // then: the property is updated without losing precision
        assertThat(localConfiguration.idleTimeout.get()).isEqualTo(2_147_483_648L)
    }

    @Test
    fun `should apply string external property`() {
        // given: a default local configuration
        val localConfiguration = LocalConfiguration()

        // when: a string external property is applied
        applyProperty(localConfiguration, "HOSTNAME", "reposilite.local")

        // then: the property is updated
        assertThat(localConfiguration.hostname.get()).isEqualTo("reposilite.local")
    }

    @Test
    fun `should read environment variables from the ingot prefix`() {
        // when: an environment variable uses the INGOT_LOCAL_ prefix
        val properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("INGOT_LOCAL_PORT" to "8181"))

        // then: it is exposed under the bare property name
        assertThat(properties).containsExactly(entry("PORT", "8181"))
    }

    @Test
    fun `should still read environment variables from the legacy reposilite prefix`() {
        // when: an environment variable uses the REPOSILITE_LOCAL_ prefix an existing deployment was set up with
        val properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("REPOSILITE_LOCAL_PORT" to "8181"))

        // then: it is still honoured
        assertThat(properties).containsExactly(entry("PORT", "8181"))
    }

    @Test
    fun `should prefer the ingot prefix over the legacy one`() {
        // when: the same property is set under both prefixes
        val properties = LocalConfigurationFactory.getEnvironmentVariables(
            mapOf(
                "INGOT_LOCAL_PORT" to "8181",
                "REPOSILITE_LOCAL_PORT" to "8080"
            )
        )

        // then: the ingot prefix wins
        assertThat(properties).containsExactly(entry("PORT", "8181"))
    }

    @Test
    fun `should ignore environment variables without a known prefix`() {
        // when: an unrelated environment variable is present
        val properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("PATH" to "/usr/bin"))

        // then: it is not treated as a configuration override
        assertThat(properties).isEmpty()
    }

    private fun applyProperty(localConfiguration: LocalConfiguration, key: String, value: String) {
        LocalConfigurationFactory.applyCustomProperties(
            journalist = null,
            localConfiguration = localConfiguration,
            properties = mapOf(key to value)
        )
    }

}
