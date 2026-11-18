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

package net.onelitefeather.ingot.plugin.version

/**
 * A plugin version as the rule of design D3 reads it: one to three numbers, an optional pre-release suffix after a hyphen
 * and an optional build suffix after a plus sign, which is ignored. A missing number counts as zero, a release is higher
 * than a pre-release of the same numbers, and two pre-releases of the same numbers are equal.
 */
class PluginVersion private constructor(
    private val numbers: List<Long>,
    private val preRelease: Boolean,
) : Comparable<PluginVersion> {

    override fun compareTo(other: PluginVersion): Int {
        for (index in 0 until maxOf(numbers.size, other.numbers.size)) {
            val left = numbers.getOrElse(index) { 0L }
            val right = other.numbers.getOrElse(index) { 0L }
            if (left != right) {
                return left.compareTo(right)
            }
        }
        return when {
            preRelease == other.preRelease -> 0
            preRelease -> -1
            else -> 1
        }
    }

    override fun equals(other: Any?): Boolean = other is PluginVersion && compareTo(other) == 0

    override fun hashCode(): Int = numbers.dropLastWhile { it == 0L }.hashCode() * 31 + preRelease.hashCode()

    companion object {

        private val GRAMMAR = Regex(
            """(\d+)(?:\.(\d+))?(?:\.(\d+))?(-[A-Za-z0-9.-]+)?(\+[A-Za-z0-9.-]+)?"""
        )

        /** Parses a version, or returns null for a missing, blank or invalid value. */
        fun parse(raw: String?): PluginVersion? {
            if (raw.isNullOrEmpty()) {
                return null
            }
            val match = GRAMMAR.matchEntire(raw) ?: return null
            val numbers = (1..3).map { match.groupValues[it] }.filter { it.isNotEmpty() }
                .map { it.toLongOrNull() ?: return null }
            return PluginVersion(numbers, preRelease = match.groupValues[4].isNotEmpty())
        }

        /** Compares two versions where a missing or invalid version (null) is lower than every valid version. */
        fun compareOrLowest(left: PluginVersion?, right: PluginVersion?): Int = when {
            left == null && right == null -> 0
            left == null -> -1
            right == null -> 1
            else -> left.compareTo(right)
        }
    }

}
