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
package net.onelitefeather.ingot.identity.service

/**
 * Whether a team write changes its external reference. The comparison ignores surrounding whitespace and keeps the letter
 * case, because group names are case sensitive. An absent reference changes nothing, and a blank one counts as no reference.
 * Only a change is guarded and written, so a repeated reference cannot restore a link that was removed in the meantime.
 */
internal object ExternalReferenceChange {

    /** The reference without surrounding whitespace, or null for an absent or blank one. */
    fun normalised(reference: String?): String? = reference?.trim()?.takeIf { it.isNotEmpty() }

    /** Whether writing [requested] to a team that stores [current] changes the reference. */
    fun changes(current: String?, requested: String?): Boolean =
        requested != null && normalised(requested) != normalised(current)
}
