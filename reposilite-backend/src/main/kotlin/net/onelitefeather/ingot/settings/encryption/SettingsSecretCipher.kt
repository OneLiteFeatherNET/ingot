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

import java.util.concurrent.ConcurrentHashMap

/**
 * The keys of one instance as the settings store uses them: the active codec seals, and the active and the previous codec open.
 *
 * A value that was opened is remembered with its envelope. When the same plain value is written again, the remembered envelope is
 * written back, so an unchanged secret keeps its bytes in the stored document and the file of a repository diffs cleanly. A remembered
 * envelope is only reused while it was sealed with the active key.
 *
 * The cipher is one instance per running server. It holds no global state, and tests construct their own.
 *
 * @param active null when no key is configured, which leaves [seal] unavailable
 */
class SettingsSecretCipher(
    private val active: SettingsEnvelopeCodec?,
    private val previous: SettingsEnvelopeCodec?,
    val mode: SettingsEncryptionMode,
) {

    private val remembered = ConcurrentHashMap<String, Pair<String, String>>()

    /** True when a key is active, so new secret values are written as envelopes. */
    val sealing: Boolean
        get() = active != null

    /** True when secret values may not be stored without an active key. */
    val requiresKey: Boolean
        get() = mode == SettingsEncryptionMode.REQUIRED && active == null

    val activeKeyId: String?
        get() = active?.keyId

    val previousKeyId: String?
        get() = previous?.keyId

    /**
     * @throws IllegalStateException when no key is active
     */
    fun seal(domain: String, path: String, plain: String): String {
        val codec = checkNotNull(active) { "No settings key is active" }
        val key = "$domain|$path"
        remembered[key]?.let { (rememberedPlain, envelope) ->
            if (rememberedPlain == plain && envelope.startsWith("${SettingsEnvelopeCodec.PREFIX}${SettingsEnvelopeCodec.VERSION}:${codec.keyId}:")) {
                return envelope
            }
        }

        return codec.seal(domain, path, plain).also { remembered[key] = plain to it }
    }

    /**
     * @throws SettingsEnvelopeException when no configured key opens the envelope
     */
    fun open(domain: String, path: String, envelope: String): String {
        val keyId = envelope.split(':').getOrNull(2)
        val codec = listOfNotNull(active, previous).firstOrNull { it.keyId == keyId }
            ?: listOfNotNull(active, previous).firstOrNull()
            ?: throw SettingsEnvelopeException(
                "The value of '$path' is encrypted, but no settings key is configured"
            )

        return codec.open(domain, path, envelope).also { plain ->
            if (codec === active) {
                remembered["$domain|$path"] = plain to envelope
            }
        }
    }

    companion object {

        /** The cipher of an instance with the keys and the mode read from the environment. */
        fun of(configuration: SettingsKeyConfiguration): SettingsSecretCipher =
            SettingsSecretCipher(
                active = configuration.active?.let { SettingsEnvelopeCodec(key = it) },
                previous = configuration.previous?.let { SettingsEnvelopeCodec(key = it) },
                mode = configuration.mode,
            )

    }

}
