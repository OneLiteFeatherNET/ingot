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

package com.reposilite.audit.api

/**
 * Who performed an audited action. [name] is the access token name, or [ANONYMOUS] / [SYSTEM]
 * when no token is involved. [address] is the client address when it is known.
 */
data class AuditActor(
    val name: String,
    val address: String? = null
) {

    companion object {
        const val ANONYMOUS = "anonymous"
        const val SYSTEM = "system"

        val SYSTEM_ACTOR = AuditActor(name = SYSTEM)

        fun named(name: String, address: String?): AuditActor =
            AuditActor(name = name, address = address?.takeIf { it.isNotBlank() })

        fun anonymous(address: String?): AuditActor =
            AuditActor(name = ANONYMOUS, address = address?.takeIf { it.isNotBlank() })

        /**
         * Reads the session identifier that the repository events carry: "name@address" for an
         * authenticated caller, or only the address for an anonymous one.
         */
        fun fromSessionIdentifier(session: String): AuditActor {
            val separator = session.lastIndexOf('@')
            return if (separator < 0) {
                anonymous(session)
            } else {
                named(name = session.substring(0, separator), address = session.substring(separator + 1))
            }
        }
    }

}
