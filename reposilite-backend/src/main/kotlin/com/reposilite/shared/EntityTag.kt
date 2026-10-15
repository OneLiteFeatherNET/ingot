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

package com.reposilite.shared

import java.security.MessageDigest

/**
 * Strong entity tags for optimistic concurrency. A client that reads a resource keeps its tag and sends it back in
 * `If-Match`; the write is applied only when the tag still describes the stored state.
 */
object EntityTag {

    /**
     * Derives a strong, quoted entity tag from the canonical representation of a resource.
     */
    fun of(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content)
        return "\"" + digest.joinToString(separator = "") { "%02x".format(it) } + "\""
    }

    /**
     * Evaluates an `If-Match` header against the current entity tag of a resource, `null` when the resource does not exist.
     * An absent or blank header imposes no precondition, which keeps clients that never send it working as before.
     */
    fun matches(ifMatch: String?, current: String?): Boolean {
        if (ifMatch.isNullOrBlank()) {
            return true
        }

        return ifMatch.split(',')
            .map { it.trim() }
            .any { candidate ->
                when {
                    candidate == "*" -> current != null
                    current == null -> false
                    else -> candidate == current
                }
            }
    }

}
