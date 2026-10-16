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

/**
 * Where a login returns to. The value comes from the client, so it is kept only when it is a path on the same origin:
 * a single leading slash, no backslash, no control character, no colon before the query, and at most 1024 characters.
 * Anything else becomes the root, so the redirect can never leave the instance.
 */
object LoginRedirect {

    const val ROOT = "/"
    private const val MAX_LENGTH = 1024

    fun sanitize(path: String?): String {
        val candidate = path?.takeIf { it.isNotBlank() } ?: return ROOT
        val safe = candidate.length <= MAX_LENGTH &&
            candidate.startsWith('/') &&
            !candidate.startsWith("//") &&
            !candidate.contains('\\') &&
            candidate.none { it.code < 0x20 || it.code == 0x7f } &&
            !candidate.substringBefore('?').substringBefore('#').contains(':')
        return if (safe) candidate else ROOT
    }

}
