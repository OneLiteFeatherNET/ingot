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
package com.reposilite.frontend.api

import java.security.MessageDigest

/**
 * The accepted stylesheet of one plugin, as it is served: the wrapped bytes, their SHA-256 and the path that names them.
 * The path carries the first [HASH_LENGTH] hexadecimal characters of the digest, so a changed sheet has a new address and
 * the year-long cache of the path cannot serve an old sheet under a new name.
 */
internal class PluginStylesheet(val plugin: String, val body: ByteArray) {

    val sha256: String = MessageDigest.getInstance("SHA-256").digest(body).joinToString(separator = "") { "%02x".format(it) }

    val hash: String = sha256.take(HASH_LENGTH)

    val path: String = "$PATH_PREFIX$plugin/$hash.css"

    companion object {
        /** The prefix of every stylesheet path. The part after it is `<plugin>/<hash>.css`. */
        const val PATH_PREFIX: String = "/api/frontend/plugin-styles/"

        const val HASH_LENGTH: Int = 16

        /** The file name of a stylesheet path: the hash and the css extension. */
        val FILE_NAME: Regex = Regex("([0-9a-f]{$HASH_LENGTH})\\.css")
    }
}
