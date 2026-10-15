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

package com.reposilite.configuration.shared

import com.fasterxml.jackson.databind.JsonNode

/**
 * The dotted paths of the fields whose value differs between [before] and [after], such as
 * `ldap.host`. Only the paths are returned: the values of settings can be secrets.
 */
fun changedSettingPaths(before: JsonNode, after: JsonNode): List<String> {
    val paths = mutableListOf<String>()
    collectChangedPaths(before, after, path = "", into = paths)
    return paths
}

private fun collectChangedPaths(before: JsonNode?, after: JsonNode?, path: String, into: MutableList<String>) {
    if (before != null && after != null && before.isObject && after.isObject) {
        val names = (before.fieldNames().asSequence() + after.fieldNames().asSequence()).toSortedSet()
        names.forEach { name ->
            collectChangedPaths(before.get(name), after.get(name), path = if (path.isEmpty()) name else "$path.$name", into = into)
        }
        return
    }

    if (before != after) {
        into += path
    }
}
