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

import java.security.MessageDigest

/** The SHA-256 digest that the catalogue records for each jar. The catalogue is the authority, so the jar must equal it. */
object PluginDigest {

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** True when the digest of [bytes] equals [expected], which may be written in either case. */
    fun matches(bytes: ByteArray, expected: String): Boolean =
        sha256Hex(bytes).equals(expected.trim(), ignoreCase = true)

}
