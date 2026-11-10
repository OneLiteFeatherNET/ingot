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

package com.reposilite.maven

import com.reposilite.storage.api.Location

/**
 * The group id prefixes a group repository protects, see `protectedGroupPrefixes`. A prefix is a dotted group id, and it
 * covers the group itself and every group below it, matched by whole path segments and case sensitively, as the Maven
 * layout is.
 */
internal object NamespaceProtection {

    private val segment = Regex("[A-Za-z0-9_-]+")

    /** A prefix is one or more segments of letters, digits, `_` and `-`, joined by single dots. */
    fun isValidPrefix(prefix: String): Boolean =
        prefix.split('.').all { segment.matches(it) }

    /** Whether any of [prefixes] covers the coordinate at [gav]. */
    fun isProtected(prefixes: Collection<String>, gav: Location): Boolean =
        prefixes.any { covers(it, gav) }

    /**
     * Whether [gav] lies below [prefix]: its leading path segments are the segments of the prefix. A location with fewer
     * segments than the prefix is an ancestor of it, and is not covered.
     */
    fun covers(prefix: String, gav: Location): Boolean {
        val prefixSegments = prefix.split('.')
        val locationSegments = gav.toString().split('/')
        return locationSegments.size >= prefixSegments.size &&
            prefixSegments.indices.all { locationSegments[it] == prefixSegments[it] }
    }

}
