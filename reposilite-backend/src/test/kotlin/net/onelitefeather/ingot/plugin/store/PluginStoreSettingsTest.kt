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

package net.onelitefeather.ingot.plugin.store

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PluginStoreSettingsTest {

    @Test
    fun `should read the coordinates of INGOT_PLUGINS`() {
        // given: the variable with two coordinates
        val environment = mapOf("INGOT_PLUGINS" to "net.onelitefeather.ingot:opensearch-plugin:1.0.0,org.example:search:2.1.0")

        // when: the settings are read
        val settings = PluginStoreSettings.read(environment)

        // then: both coordinates are selected in the given order
        assertThat(settings.selection.map { it.toString() })
            .describedAs("INGOT_PLUGINS must select both coordinates in order")
            .containsExactly("net.onelitefeather.ingot:opensearch-plugin:1.0.0", "org.example:search:2.1.0")
    }

    @Test
    fun `should read REPOSILITE_PLUGINS when INGOT_PLUGINS is unset`() {
        // given: only the legacy variable
        val environment = mapOf("REPOSILITE_PLUGINS" to "org.example:search:2.1.0")

        // when: the settings are read
        val settings = PluginStoreSettings.read(environment)

        // then: the legacy list is used
        assertThat(settings.selection.map { it.toString() })
            .describedAs("REPOSILITE_PLUGINS must be the fallback")
            .containsExactly("org.example:search:2.1.0")
    }

    @Test
    fun `should prefer INGOT_PLUGINS over REPOSILITE_PLUGINS when both are set`() {
        // given: both variables with different lists
        val environment = mapOf(
            "INGOT_PLUGINS" to "org.example:new:1.0.0",
            "REPOSILITE_PLUGINS" to "org.example:old:1.0.0",
        )

        // when: the settings are read
        val settings = PluginStoreSettings.read(environment)

        // then: only the INGOT list is selected
        assertThat(settings.selection.map { it.toString() })
            .describedAs("INGOT_PLUGINS must win over REPOSILITE_PLUGINS")
            .containsExactly("org.example:new:1.0.0")
    }

    @Test
    fun `should ignore blank entries of the list`() {
        // given: a list with blank entries between the coordinates
        val environment = mapOf("INGOT_PLUGINS" to " org.example:search:2.1.0 ,, ,")

        // when: the settings are read
        val settings = PluginStoreSettings.read(environment)

        // then: only the coordinate is selected and no problem is reported
        assertThat(settings.selection.map { it.toString() }).containsExactly("org.example:search:2.1.0")
        assertThat(settings.problems).describedAs("blank entries are not a problem").isEmpty()
    }

    @Test
    fun `should refuse the whole list when an entry has no group and no version`() {
        // given: a list whose second entry lacks the group and the version
        val environment = mapOf("INGOT_PLUGINS" to "org.example:search:2.1.0,opensearch-plugin")

        // when: the settings are read
        val settings = PluginStoreSettings.read(environment)

        // then: nothing is selected and the problem names the variable and the entry
        assertThat(settings.selection).describedAs("a malformed list selects nothing").isEmpty()
        assertThat(settings.problems.single())
            .describedAs("the problem must name the variable and the bad entry")
            .contains("INGOT_PLUGINS", "opensearch-plugin")
    }

    @Test
    fun `should read the local key plugins as the same list as the variable`() {
        // given: the same list as the variable
        val fromVariable = PluginStoreSettings.read(mapOf("INGOT_PLUGINS" to "org.example:search:2.1.0"))

        // when: the list is given as the local key
        val fromLocalKey = PluginStoreSettings.read(emptyMap(), localSelection = "org.example:search:2.1.0")

        // then: both produce the same selection
        assertThat(fromLocalKey.selection)
            .describedAs("the local key and the variable must select the same coordinates")
            .isEqualTo(fromVariable.selection)
    }

    @Test
    fun `should select a coordinate once when the variable and the local key both name it`() {
        // given: the same coordinate in both places
        val environment = mapOf("INGOT_PLUGINS" to "org.example:search:2.1.0")

        // when: the settings are read with the local key naming it too
        val settings = PluginStoreSettings.read(environment, localSelection = "org.example:search:2.1.0")

        // then: the union holds the coordinate once
        assertThat(settings.selection.map { it.toString() })
            .describedAs("the union of the two lists must not repeat a coordinate")
            .containsExactly("org.example:search:2.1.0")
    }

    @Test
    fun `should refuse a plain http source and name the variable`() {
        // given: a source that is not served over https
        val environment = mapOf("INGOT_PLUGIN_SOURCES" to "http://repo.example.org/maven")

        // when: the settings are read
        val settings = PluginStoreSettings.read(environment)

        // then: the source is not used and the problem names the variable
        assertThat(settings.sources).describedAs("a plain http source is not used").isEmpty()
        assertThat(settings.problems.single())
            .describedAs("the problem must name the variable")
            .contains("INGOT_PLUGIN_SOURCES")
    }

    @Test
    fun `should keep the https sources in the configured order`() {
        // given: two https sources
        val environment = mapOf("INGOT_PLUGIN_SOURCES" to "https://first.example.org/maven,https://second.example.org/maven")

        // when: the settings are read
        val settings = PluginStoreSettings.read(environment)

        // then: both are kept in order
        assertThat(settings.sources)
            .describedAs("the first source must stay first")
            .containsExactly("https://first.example.org/maven", "https://second.example.org/maven")
    }

    @Test
    fun `should load nothing when no store variable is set`() {
        // when: the settings are read from an empty environment
        val settings = PluginStoreSettings.read(emptyMap())

        // then: the store is inactive and reports no problem
        assertThat(settings.isActive).describedAs("an installation without settings must not activate the store").isFalse()
        assertThat(settings.problems).isEmpty()
        assertThat(settings.trustedKeysLocation).isNull()
        assertThat(settings.allowUnsigned).isFalse()
    }

    @Test
    fun `should disable the store when the recovery switch is true`() {
        // given: a selection and the recovery switch
        val environment = mapOf(
            "INGOT_PLUGINS" to "org.example:search:2.1.0",
            "INGOT_PLUGIN_SOURCES" to "https://repo.example.org/maven",
            "INGOT_PLUGIN_STORE_DISABLED" to "true",
        )

        // when: the settings are read
        val settings = PluginStoreSettings.read(environment)

        // then: the store is disabled for this start
        assertThat(settings.disabled).describedAs("INGOT_PLUGIN_STORE_DISABLED=true must disable the store").isTrue()
    }

    @Test
    fun `should read the unsigned switch from the legacy spelling when the INGOT name is unset`() {
        // when: only the legacy spelling is set
        val settings = PluginStoreSettings.read(mapOf("REPOSILITE_PLUGIN_ALLOW_UNSIGNED" to "true"))

        // then: unsigned content is allowed
        assertThat(settings.allowUnsigned).describedAs("REPOSILITE_PLUGIN_ALLOW_UNSIGNED must be the fallback").isTrue()
    }

    @Test
    fun `should read the trusted keys location as given`() {
        // when: the location of the trusted keys is set
        val settings = PluginStoreSettings.read(mapOf("INGOT_PLUGIN_TRUSTED_KEYS" to "/etc/ingot/keys.asc"))

        // then: the location is kept
        assertThat(settings.trustedKeysLocation.toString())
            .describedAs("the trusted keys location must be read from INGOT_PLUGIN_TRUSTED_KEYS")
            .isEqualTo("/etc/ingot/keys.asc")
    }

}
