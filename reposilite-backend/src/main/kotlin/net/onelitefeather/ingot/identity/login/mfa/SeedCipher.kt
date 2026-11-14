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

package net.onelitefeather.ingot.identity.login.mfa

import java.util.Base64
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec

/**
 * Keeps authenticator seeds reversible at rest. A TOTP check needs the seed itself, so a hash cannot be used. The seed is
 * sealed with the settings key, and the envelope is bound to the factor it belongs to, so it cannot be moved to another row.
 */
internal interface SeedCipher {

    /** False while no settings key is active. Then no authenticator can be enrolled. */
    val available: Boolean

    /** Seals [seed] for the factor [factorKey]. Only called while [available] is true. */
    fun seal(factorKey: String, seed: ByteArray): String

    /** Opens a seed sealed by [seal]. Throws when no configured key opens it. */
    fun open(factorKey: String, envelope: String): ByteArray

}

/**
 * The seed cipher on top of the settings envelope codec. [codecs] is read on every call, so a key that is added or removed
 * takes effect without a restart of the login. The first codec seals, every codec may open.
 */
internal class SettingsSeedCipher(
    private val codecs: () -> List<SettingsEnvelopeCodec>,
) : SeedCipher {

    override val available: Boolean
        get() = codecs().isNotEmpty()

    override fun seal(factorKey: String, seed: ByteArray): String {
        val codec = checkNotNull(codecs().firstOrNull()) { "No settings key is active" }
        return codec.seal(DOMAIN, pathOf(factorKey), Base64.getEncoder().encodeToString(seed))
    }

    override fun open(factorKey: String, envelope: String): ByteArray {
        val keyId = envelope.split(':').getOrNull(2)
        val codec = codecs().firstOrNull { it.keyId == keyId } ?: error("No configured settings key opens this seed")
        return Base64.getDecoder().decode(codec.open(DOMAIN, pathOf(factorKey), envelope))
    }

    private fun pathOf(factorKey: String): String = "mfa_factor.$factorKey.secret"

    private companion object {
        const val DOMAIN = "login"
    }

}
