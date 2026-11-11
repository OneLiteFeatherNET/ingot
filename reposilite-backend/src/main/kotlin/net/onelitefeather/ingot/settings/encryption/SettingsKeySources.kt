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

package net.onelitefeather.ingot.settings.encryption

import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64

enum class SettingsEncryptionMode(val wireName: String) {
    /** Encryption starts when a key is configured. Without a key the instance behaves as it did before encryption existed. */
    OPTIONAL("optional"),

    /** The instance does not start without a key and refuses to store a secret while no key is active. */
    REQUIRED("required"),
}

/**
 * The keys and the mode of the settings encryption, as read from the environment at start.
 * [active] is null when no key source is set, [previous] is only used to decrypt during a rotation.
 */
class SettingsKeyConfiguration(
    val active: ByteArray?,
    val previous: ByteArray?,
    val mode: SettingsEncryptionMode,
)

/**
 * Reads the settings key from the environment. The key comes from the process environment or from a file that the environment
 * names, never from the shared settings or the local configuration. The `INGOT_` names are read first, and the `REPOSILITE_` spelling
 * only when the `INGOT_` name is absent.
 */
object SettingsKeySources {

    const val KEY = "INGOT_SETTINGS_KEY"
    const val KEY_FILE = "INGOT_SETTINGS_KEY_FILE"
    const val PREVIOUS_KEY = "INGOT_SETTINGS_KEY_PREVIOUS"
    const val PREVIOUS_KEY_FILE = "INGOT_SETTINGS_KEY_PREVIOUS_FILE"
    const val MODE = "INGOT_SETTINGS_ENCRYPTION"

    /**
     * @throws IllegalArgumentException when the configuration cannot be used; the message names the variables, never a part of a key
     */
    fun read(environment: Map<String, String>): SettingsKeyConfiguration =
        SettingsKeyConfiguration(
            active = readKey(environment, KEY, KEY_FILE),
            previous = readKey(environment, PREVIOUS_KEY, PREVIOUS_KEY_FILE),
            mode = readMode(environment),
        )

    private fun readKey(environment: Map<String, String>, inline: String, file: String): ByteArray? {
        val inlineSetting = setting(environment, inline)
        val fileSetting = setting(environment, file)
        if (inlineSetting != null && fileSetting != null) {
            throw IllegalArgumentException("Set either ${inlineSetting.first} or ${fileSetting.first}, not both")
        }

        return when {
            inlineSetting != null -> decode(inlineSetting.first, inlineSetting.second)
            fileSetting != null -> decode(fileSetting.first, readFile(fileSetting.first, fileSetting.second))
            else -> null
        }
    }

    private fun readFile(name: String, path: String): String {
        val content = try {
            Files.readString(Paths.get(path))
        } catch (exception: Exception) {
            throw IllegalArgumentException("$name names a file that cannot be read (${exception::class.simpleName})")
        }
        // One trailing line break is what editors and secret mounts add, it is not part of the key.
        return content.removeSuffix("\r\n").removeSuffix("\n")
    }

    private fun decode(name: String, encoded: String): ByteArray {
        val bytes = try {
            Base64.getDecoder().decode(encoded.trim())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("$name is not valid base64")
        }

        if (bytes.size != SettingsEnvelopeCodec.KEY_LENGTH) {
            throw IllegalArgumentException("$name must hold base64 of ${SettingsEnvelopeCodec.KEY_LENGTH} bytes, found ${bytes.size} bytes")
        }
        return bytes
    }

    private fun readMode(environment: Map<String, String>): SettingsEncryptionMode {
        val (name, value) = setting(environment, MODE) ?: return SettingsEncryptionMode.OPTIONAL
        return SettingsEncryptionMode.entries.firstOrNull { it.wireName == value.lowercase() }
            ?: throw IllegalArgumentException("Unknown value for $name, expected 'optional' or 'required'")
    }

    /**
     * The INGOT spelling of a setting, or the REPOSILITE spelling when the INGOT one is unset or empty. Returns the name that was used.
     */
    private fun setting(environment: Map<String, String>, name: String): Pair<String, String>? =
        sequenceOf(name, name.replaceFirst("INGOT_", "REPOSILITE_"))
            .mapNotNull { key -> environment[key]?.trim()?.takeIf { it.isNotEmpty() }?.let { key to it } }
            .firstOrNull()

}
