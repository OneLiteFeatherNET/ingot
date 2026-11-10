package com.reposilite.status

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RemoteVersionCheckSettingTest {

    @Test
    fun `should keep the check on when nothing is configured`() {
        assertThat(RemoteVersionCheckSetting.read(emptyMap(), emptyMap()))
            .`as`("remote version check without any setting")
            .isTrue()
    }

    @Test
    fun `should turn the check off when the ingot variable is false`() {
        assertThat(RemoteVersionCheckSetting.read(emptyMap(), mapOf("INGOT_STATUS_REMOTE_VERSION_CHECK" to "false")))
            .`as`("remote version check with INGOT_STATUS_REMOTE_VERSION_CHECK=false")
            .isFalse()
    }

    @Test
    fun `should read the false value of the ingot variable regardless of case`() {
        assertThat(RemoteVersionCheckSetting.read(emptyMap(), mapOf("INGOT_STATUS_REMOTE_VERSION_CHECK" to "FALSE")))
            .`as`("remote version check with INGOT_STATUS_REMOTE_VERSION_CHECK=FALSE")
            .isFalse()
    }

    @Test
    fun `should keep the check on when the ingot variable is true`() {
        assertThat(RemoteVersionCheckSetting.read(emptyMap(), mapOf("INGOT_STATUS_REMOTE_VERSION_CHECK" to "true")))
            .`as`("remote version check with INGOT_STATUS_REMOTE_VERSION_CHECK=true")
            .isTrue()
    }

    @Test
    fun `should turn the check off when only the legacy variable is false`() {
        assertThat(RemoteVersionCheckSetting.read(emptyMap(), mapOf("REPOSILITE_STATUS_REMOTE_VERSION_CHECK" to "false")))
            .`as`("remote version check with REPOSILITE_STATUS_REMOTE_VERSION_CHECK=false")
            .isFalse()
    }

    @Test
    fun `should fall through an empty ingot variable to the legacy variable`() {
        assertThat(
            RemoteVersionCheckSetting.read(
                emptyMap(),
                mapOf("INGOT_STATUS_REMOTE_VERSION_CHECK" to "", "REPOSILITE_STATUS_REMOTE_VERSION_CHECK" to "false"),
            )
        ).`as`("remote version check with an empty INGOT_ variable and REPOSILITE_ false").isFalse()
    }

    @Test
    fun `should prefer the ingot property over the ingot variable`() {
        assertThat(
            RemoteVersionCheckSetting.read(
                properties = mapOf("ingot.status.remote-version-check" to "true"),
                environment = mapOf("INGOT_STATUS_REMOTE_VERSION_CHECK" to "false"),
            )
        ).`as`("remote version check with ingot. property true and INGOT_ variable false").isTrue()
    }

    @Test
    fun `should prefer the ingot variable over the legacy property`() {
        assertThat(
            RemoteVersionCheckSetting.read(
                properties = mapOf("reposilite.status.remote-version-check" to "false"),
                environment = mapOf("INGOT_STATUS_REMOTE_VERSION_CHECK" to "true"),
            )
        ).`as`("remote version check with reposilite. property false and INGOT_ variable true").isTrue()
    }

    @Test
    fun `should prefer the legacy property over the legacy variable`() {
        assertThat(
            RemoteVersionCheckSetting.read(
                properties = mapOf("reposilite.status.remote-version-check" to "true"),
                environment = mapOf("REPOSILITE_STATUS_REMOTE_VERSION_CHECK" to "false"),
            )
        ).`as`("remote version check with reposilite. property true and REPOSILITE_ variable false").isTrue()
    }

    @Test
    fun `should prefer the ingot property over every other source`() {
        assertThat(
            RemoteVersionCheckSetting.read(
                properties = mapOf("ingot.status.remote-version-check" to "false", "reposilite.status.remote-version-check" to "true"),
                environment = mapOf("INGOT_STATUS_REMOTE_VERSION_CHECK" to "true", "REPOSILITE_STATUS_REMOTE_VERSION_CHECK" to "true"),
            )
        ).`as`("remote version check with the ingot. property false").isFalse()
    }

    @Test
    fun `should treat any property value other than true as off`() {
        assertThat(RemoteVersionCheckSetting.read(mapOf("ingot.status.remote-version-check" to "no"), emptyMap()))
            .`as`("remote version check with ingot.status.remote-version-check=no")
            .isFalse()
    }

    @Test
    fun `should ignore an unknown variable value and warn once with the variable name`() {
        // given: a typo in the variable and a warning sink owned by this test
        val warnings = mutableListOf<String>()

        // when: the setting is read
        val enabled = RemoteVersionCheckSetting.read(
            properties = emptyMap(),
            environment = mapOf("INGOT_STATUS_REMOTE_VERSION_CHECK" to "flase"),
            warn = warnings::add,
        )

        // then: the default stays on and exactly one warning names the variable
        assertThat(enabled).`as`("remote version check with a misspelt value").isTrue()
        assertThat(warnings)
            .`as`("warnings for a misspelt INGOT_STATUS_REMOTE_VERSION_CHECK")
            .hasSize(1)
        assertThat(warnings.single())
            .`as`("warning text")
            .contains("INGOT_STATUS_REMOTE_VERSION_CHECK")
            .contains("flase")
    }

    @Test
    fun `should let the legacy variable decide when the ingot variable is misspelt`() {
        assertThat(
            RemoteVersionCheckSetting.read(
                emptyMap(),
                mapOf("INGOT_STATUS_REMOTE_VERSION_CHECK" to "flase", "REPOSILITE_STATUS_REMOTE_VERSION_CHECK" to "false"),
            )
        ).`as`("remote version check with a misspelt INGOT_ variable and REPOSILITE_ false").isFalse()
    }

}
