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

package net.onelitefeather.ingot.plugin.description

/** The stored text of a plugin description, and whether the limit cut it. */
data class NormalisedDescription(
    val text: String,
    val cut: Boolean,
)

/**
 * Turns the raw description of a plugin into the stored text. The description is plain text: control characters, including
 * line breaks, are removed, leading and trailing whitespace is removed, and the result is cut to [LIMIT] code points. An empty
 * result is the empty text, which means no description. The function never throws, so a description never refuses a plugin.
 */
object PluginDescriptionNormaliser {

    /** The most code points a stored description keeps. */
    const val LIMIT = 280

    fun normalise(raw: String): NormalisedDescription {
        val text = raw.filterNot { it.category == CharCategory.CONTROL }.trim()
        val codePoints = text.codePointCount(0, text.length)
        if (codePoints <= LIMIT) {
            return NormalisedDescription(text = text, cut = false)
        }
        return NormalisedDescription(text = text.substring(0, text.offsetByCodePoints(0, LIMIT)), cut = true)
    }

}
