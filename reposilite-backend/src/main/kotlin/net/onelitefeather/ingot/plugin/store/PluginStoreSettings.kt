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

import java.nio.file.Path

/** A plugin coordinate `groupId:artifactId:version`, the form in which the store selects a plugin. */
data class PluginCoordinate(val groupId: String, val artifactId: String, val version: String) {

    override fun toString(): String = "$groupId:$artifactId:$version"

    companion object {

        private val SEGMENT = Regex("[^\\s:]+")

        /** Parses one entry, or returns null when it is not exactly three non-blank segments separated by colons. */
        fun parse(text: String): PluginCoordinate? {
            val parts = text.split(":")
            if (parts.size != 3 || parts.any { !SEGMENT.matches(it) }) {
                return null
            }
            return PluginCoordinate(parts[0], parts[1], parts[2])
        }

    }

}

/**
 * The local configuration of the plugin store, read once at start. Nothing here touches the network or the file system,
 * so an installation without any store variable gets an inactive store and no side effects.
 *
 * A malformed selection refuses the whole list it came from and is reported in [problems], so the start goes on without
 * guessing which entries were meant. The same applies to a source that is not an `https` address.
 */
class PluginStoreSettings internal constructor(
    /** The selected coordinates: the variable and the local key, as a union in first-seen order. */
    val selection: List<PluginCoordinate>,
    /** The `https` sources in configured order. */
    val sources: List<String>,
    /** The file or directory of ASCII-armoured OpenPGP public keys that the store trusts, or null when none is configured. */
    val trustedKeysLocation: Path?,
    /** True when unsigned catalogues and jars may load. Every such plugin is marked as unverified. */
    val allowUnsigned: Boolean,
    /** True when the recovery switch skips the store for this start. */
    val disabled: Boolean,
    /** Messages that name the variable or the local key and the refused value. */
    val problems: List<String>,
) {

    /** The store does something only when a plugin is selected and the recovery switch is off. */
    val isActive: Boolean
        get() = !disabled && selection.isNotEmpty()

    companion object {

        const val SELECTION_VARIABLE = "INGOT_PLUGINS"
        const val SOURCES_VARIABLE = "INGOT_PLUGIN_SOURCES"
        const val TRUSTED_KEYS_VARIABLE = "INGOT_PLUGIN_TRUSTED_KEYS"
        const val ALLOW_UNSIGNED_VARIABLE = "INGOT_PLUGIN_ALLOW_UNSIGNED"
        const val STORE_DISABLED_VARIABLE = "INGOT_PLUGIN_STORE_DISABLED"

        private const val LEGACY_PREFIX = "REPOSILITE_"

        /**
         * Reads the store settings. [localSelection] is the value of the local configuration key `plugins`, and an empty
         * string when the key is not set. Each `INGOT_` variable falls back to its `REPOSILITE_` spelling when it is unset or blank.
         */
        fun read(environment: Map<String, String>, localSelection: String = ""): PluginStoreSettings {
            val problems = mutableListOf<String>()

            val selection = LinkedHashSet<PluginCoordinate>()
            variable(environment, SELECTION_VARIABLE)?.let { (name, value) ->
                selection += parseSelection(name, value, problems)
            }
            if (localSelection.isNotBlank()) {
                selection += parseSelection("the local key plugins", localSelection, problems)
            }

            val sources = variable(environment, SOURCES_VARIABLE)?.let { (name, value) ->
                value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.filter { entry ->
                    val secure = entry.startsWith("https://", ignoreCase = true)
                    if (!secure) {
                        problems += "$name refused: '$entry' is not an https address, so it is not used"
                    }
                    secure
                }
            } ?: emptyList()

            val trustedKeys = variable(environment, TRUSTED_KEYS_VARIABLE)?.second?.let { Path.of(it) }

            return PluginStoreSettings(
                selection = selection.toList(),
                sources = sources,
                trustedKeysLocation = trustedKeys,
                allowUnsigned = switch(environment, ALLOW_UNSIGNED_VARIABLE),
                disabled = switch(environment, STORE_DISABLED_VARIABLE),
                problems = problems,
            )
        }

        private fun parseSelection(name: String, value: String, problems: MutableList<String>): List<PluginCoordinate> {
            val entries = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val parsed = entries.map { entry -> entry to PluginCoordinate.parse(entry) }
            val malformed = parsed.firstOrNull { (_, coordinate) -> coordinate == null }
            if (malformed != null) {
                problems += "$name refused: '${malformed.first}' is not groupId:artifactId:version, so none of its entries is selected"
                return emptyList()
            }
            return parsed.mapNotNull { (_, coordinate) -> coordinate }
        }

        /** The value of [ingotName], or of its legacy spelling when the INGOT name is unset or blank, with its name. */
        private fun variable(environment: Map<String, String>, ingotName: String): Pair<String, String>? {
            val legacyName = LEGACY_PREFIX + ingotName.removePrefix("INGOT_")
            environment[ingotName]?.trim()?.takeIf { it.isNotEmpty() }?.let { return ingotName to it }
            return environment[legacyName]?.trim()?.takeIf { it.isNotEmpty() }?.let { legacyName to it }
        }

        private fun switch(environment: Map<String, String>, ingotName: String): Boolean =
            variable(environment, ingotName)?.second.equals("true", ignoreCase = true)

    }

}
