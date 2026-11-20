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

package net.onelitefeather.ingot.search.enrichment

import java.nio.charset.StandardCharsets

private const val MAX_KEYS = 32
private const val MAX_VALUES_PER_KEY = 16
private const val MAX_VALUE_CHARACTERS = 2000
private const val MAX_SET_BYTES = 8 * 1024

private val NAME_PATTERN = Regex("[a-z][a-z0-9_]{0,31}")

// Bidirectional override and isolate characters: the spoofing vectors of the Minecraft portal design (D6).
private val BIDI_CHARACTERS = (0x202A..0x202E) + (0x2066..0x2069)

/** Why an attribute set is refused. [rule] is the fixed name the core logs and returns, and never contains a value. */
internal enum class AttributeRefusal(val rule: String) {
    TOO_MANY_KEYS("too-many-keys"),
    TOO_MANY_VALUES("too-many-values"),
    VALUE_TOO_LONG("value-too-long"),
    SET_TOO_LARGE("set-too-large"),
    CONTROL_CHARACTER("control-character"),
    FOREIGN_NAMESPACE("foreign-namespace"),
    INVALID_KEY("invalid-key");

    /** The failure reason of an enrichment that was refused. */
    val reason: String get() = "invalid attributes: $rule"
}

/**
 * Checks the attribute set of one enricher (artifact enrichment, D1 and D2). Returns the first refusal in a fixed order, or null
 * when the set is allowed. Keys must start with [enricherId] followed by a dot and a name matching the name pattern. The set is
 * never truncated; a refused set is refused as a whole.
 */
internal fun validateAttributes(enricherId: String, values: Map<String, List<String>>): AttributeRefusal? {
    if (values.size > MAX_KEYS) return AttributeRefusal.TOO_MANY_KEYS
    for (key in values.keys) {
        val dot = key.indexOf('.')
        if (dot < 0) return AttributeRefusal.INVALID_KEY
        if (key.substring(0, dot) != enricherId) return AttributeRefusal.FOREIGN_NAMESPACE
        if (!NAME_PATTERN.matches(key.substring(dot + 1))) return AttributeRefusal.INVALID_KEY
    }
    if (values.values.any { it.size > MAX_VALUES_PER_KEY }) return AttributeRefusal.TOO_MANY_VALUES
    val allValues = values.values.flatten()
    if (allValues.any { it.codePointCount(0, it.length) > MAX_VALUE_CHARACTERS }) return AttributeRefusal.VALUE_TOO_LONG
    if (allValues.any { containsControlOrBidi(it) }) return AttributeRefusal.CONTROL_CHARACTER
    val bytes = values.entries.sumOf { (key, list) ->
        key.toByteArray(StandardCharsets.UTF_8).size.toLong() + list.sumOf { it.toByteArray(StandardCharsets.UTF_8).size.toLong() }
    }
    if (bytes > MAX_SET_BYTES) return AttributeRefusal.SET_TOO_LARGE
    return null
}

private fun containsControlOrBidi(value: String): Boolean = value.codePoints().anyMatch { codePoint ->
    Character.getType(codePoint) == Character.CONTROL.toInt() || codePoint in BIDI_CHARACTERS
}
