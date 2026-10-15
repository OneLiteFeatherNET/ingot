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

import java.net.URI
import java.net.URISyntaxException

/**
 * The public address of the dashboard, `login.publicUrl`. Empty means that the address is taken from the request. A value
 * is an absolute https address, or an http address of a loopback host, and has neither a query, a fragment nor a user name.
 */
object PublicUrl {

    /** The rule a refused value breaks, in words an administrator can act on. */
    const val RULE = "Enter an absolute https:// address, or http:// for localhost, without a query, a fragment or a user name"

    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]")

    /** The reason [value] is not a public URL, or `null` when it is one, or when it is blank. */
    fun problemOf(value: String): String? {
        if (value.isBlank()) return null
        if (value != value.trim() || value.any { it.isWhitespace() }) return RULE
        val uri = try {
            URI(value)
        } catch (_: URISyntaxException) {
            return RULE
        }
        val host = uri.host.orEmpty()
        val secure = uri.scheme == "https"
        val loopbackHttp = uri.scheme == "http" && host in LOOPBACK_HOSTS
        return when {
            !(secure || loopbackHttp) || host.isEmpty() -> RULE
            uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null -> RULE
            else -> null
        }
    }

}
