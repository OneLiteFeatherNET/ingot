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

/**
 * The key record stored with the shared settings: the key identifier and the check value of the key that sealed them. It holds
 * no key bytes.
 */
data class StoredSettingsKey(val keyId: String, val check: String) {

    fun serialize(): String =
        "$keyId $check"

    companion object {

        fun of(codec: SettingsEnvelopeCodec): StoredSettingsKey =
            StoredSettingsKey(keyId = codec.keyId, check = codec.check)

        /** @return null for an empty or malformed record */
        fun parse(record: String): StoredSettingsKey? {
            val parts = record.trim().split(' ')
            return if (parts.size == 2 && parts[0].matches(Regex("[0-9a-f]{8}")) && parts[1].isNotEmpty()) {
                StoredSettingsKey(keyId = parts[0], check = parts[1])
            } else {
                null
            }
        }

    }

}

/** An envelope found in the stored document: where it sits and the key identifier it names. */
data class SealedSetting(val domain: String, val path: String, val keyId: String)

sealed interface KeyStartOutcome {

    /** No key is configured and the document holds no envelope. Nothing is checked or stored. */
    data object Unkeyed : KeyStartOutcome

    /** The configured key is to be stored as the key of this database. */
    data class Adopt(val record: StoredSettingsKey) : KeyStartOutcome

    /** The stored record names the configured key. */
    data object Accepted : KeyStartOutcome

    /** The stored record names the previous key, so a rotation is open and the instance reads both keys. */
    data class AcceptedPreviousKey(val stored: StoredSettingsKey) : KeyStartOutcome

}

/**
 * Decides at start whether the configured key may run against the stored settings. It is a pure function: the stored record and the
 * envelopes of the document are passed in, and the caller stores an [KeyStartOutcome.Adopt] record.
 */
object SettingsKeyGuard {

    /**
     * @throws IllegalStateException when the instance must not start; the message names the variables, a domain, a property or a key
     *   identifier, never a value
     */
    fun verify(configuration: SettingsKeyConfiguration, stored: StoredSettingsKey?, sealed: List<SealedSetting>): KeyStartOutcome {
        val active = configuration.active
        if (active == null) {
            if (configuration.mode == SettingsEncryptionMode.REQUIRED) {
                throw IllegalStateException(
                    "${SettingsKeySources.MODE} is 'required', but no settings key is configured. " +
                        "Set ${SettingsKeySources.KEY} or ${SettingsKeySources.KEY_FILE}."
                )
            }
            sealed.firstOrNull()?.let { first ->
                throw IllegalStateException(
                    "Settings domain '${first.domain}', property '${first.path}' is encrypted, but no settings key is configured. " +
                        "Set ${SettingsKeySources.KEY} or ${SettingsKeySources.KEY_FILE} to the key that wrote it."
                )
            }
            return KeyStartOutcome.Unkeyed
        }

        val configured = SettingsEnvelopeCodec(key = active)
        if (stored == null) {
            sealed.firstOrNull { it.keyId != configured.keyId }?.let { foreign ->
                throw IllegalStateException(
                    "The settings key does not match the key that sealed the stored settings (key id ${foreign.keyId}). " +
                        "Start the instance with the key the other instances use."
                )
            }
            return KeyStartOutcome.Adopt(StoredSettingsKey.of(configured))
        }

        if (stored.check == configured.check) {
            return KeyStartOutcome.Accepted
        }

        configuration.previous?.let { previous ->
            if (stored.check == SettingsEnvelopeCodec(key = previous).check) {
                return KeyStartOutcome.AcceptedPreviousKey(stored)
            }
        }

        throw IllegalStateException(
            "The settings key does not match the key of this database (key id ${stored.keyId}). " +
                "Start the instance with the key the other instances use."
        )
    }

}
