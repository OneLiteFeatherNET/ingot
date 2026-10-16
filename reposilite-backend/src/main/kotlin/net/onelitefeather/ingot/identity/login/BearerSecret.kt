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

package net.onelitefeather.ingot.identity.login

import java.security.SecureRandom
import java.util.Base64

/**
 * Secrets of tokens created through the self-service API: `ingot_<tokenId>_<random>`.
 *
 * The token id is part of the secret, so a Bearer request names its token without a name and without a second index:
 * the id leads to the row, and the row's hash verifies the whole secret. The id is not a secret; the random part is what
 * protects the token. The whole secret stays below 72 bytes, because bcrypt, which hashes it, ignores anything after that.
 *
 * Tokens created before this format, and tokens created through the legacy API, have no prefix and work over Basic only.
 */
internal object BearerSecret {

    const val PREFIX = "ingot_"

    private const val RANDOM_BYTES = 32
    private val random = SecureRandom()

    fun create(tokenId: Int): String {
        val randomPart = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(RANDOM_BYTES).also { random.nextBytes(it) })
        return "$PREFIX${tokenId}_$randomPart"
    }

    /** The token id a secret of this format names, or `null` for anything else. Does not verify the secret. */
    fun tokenId(secret: String): Int? {
        if (!secret.startsWith(PREFIX)) {
            return null
        }
        val rest = secret.removePrefix(PREFIX)
        val randomPart = rest.substringAfter('_', missingDelimiterValue = "")
        if (randomPart.isEmpty()) {
            return null
        }
        return rest.substringBefore('_').toIntOrNull()
    }

}
