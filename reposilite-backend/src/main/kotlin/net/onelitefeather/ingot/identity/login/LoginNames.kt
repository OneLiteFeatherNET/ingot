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

/** The rules a user name must meet, whichever provider creates the user. */
object LoginNames {

    const val MAX_USERNAME_LENGTH = 255

    /** A user-facing reason why [name] is not a user name, or `null` when it is one. */
    fun usernameProblem(name: String): String? = when {
        name.isEmpty() -> "User name is required"
        name.length > MAX_USERNAME_LENGTH -> "User name must not have more than $MAX_USERNAME_LENGTH characters"
        name.any { it == ':' || it.isWhitespace() || it.isISOControl() } -> "User name must not contain colons, spaces or control characters"
        else -> null
    }

}
