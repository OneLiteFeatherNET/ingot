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

/**
 * The `ingot_mfa` cookie: the id of a sign-in challenge that waits for its second factor. It is HttpOnly, SameSite=Lax and
 * limited to the auth routes, and it lives as long as the challenge does.
 */
object SecondFactorCookie {

    const val NAME = "ingot_mfa"

    private const val MAX_AGE_SECONDS = 300L

    fun set(challenge: String, basePath: String, secure: Boolean): String =
        cookie(value = challenge, maxAgeSeconds = MAX_AGE_SECONDS, basePath = basePath, secure = secure)

    fun clear(basePath: String, secure: Boolean): String =
        cookie(value = "", maxAgeSeconds = 0, basePath = basePath, secure = secure)

    private fun cookie(value: String, maxAgeSeconds: Long, basePath: String, secure: Boolean): String {
        val base = basePath.trim().trimEnd('/').let { if (it.isEmpty() || it.startsWith('/')) it else "/$it" }
        val attributes = buildList {
            add("Path=$base/api/auth")
            add("Max-Age=$maxAgeSeconds")
            add("HttpOnly")
            add("SameSite=Lax")
            if (secure) {
                add("Secure")
            }
        }
        return "$NAME=$value; " + attributes.joinToString(separator = "; ")
    }

}
