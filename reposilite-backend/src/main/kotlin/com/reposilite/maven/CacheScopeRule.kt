/*
 * Copyright (c) 2023 dzikoysk
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

import com.reposilite.shared.http.CacheScope

/** A local repository as the scope rule sees it: its visibility and the local repositories it references through loopback mirrors. */
internal data class CacheScopeNode(
    val visibility: RepositoryVisibility,
    val loopbackReferences: List<String>,
)

internal fun Repository.cacheScopeNode(): CacheScopeNode =
    CacheScopeNode(
        visibility = visibility,
        loopbackReferences = mirrorHosts.filter { it.client is RepositoryLoopbackClient }.map { it.host },
    )

/**
 * The scope of the `Cache-Control` value of a file answer from [repository]. The answer is [CacheScope.PRIVATE] when the
 * repository, or any local repository it reaches through loopback references, directly or through other groups, is not
 * PUBLIC. A walk over those references ends on a cycle, because every name is visited once. Remote references are not
 * part of the node, and a reference to a repository that does not exist is skipped. An unknown [repository] is private.
 *
 * [privateAsPublic] is the escape hatch: when it is set, every answer is [CacheScope.PUBLIC].
 */
internal fun resolveCacheScope(
    repository: String,
    nodeOf: (String) -> CacheScopeNode?,
    privateAsPublic: Boolean,
): CacheScope {
    if (privateAsPublic) {
        return CacheScope.PUBLIC
    }
    if (nodeOf(repository) == null) {
        return CacheScope.PRIVATE
    }

    val visited = mutableSetOf<String>()
    val pending = ArrayDeque(listOf(repository))
    while (pending.isNotEmpty()) {
        val name = pending.removeFirst()
        if (!visited.add(name)) {
            continue
        }
        val node = nodeOf(name) ?: continue
        if (node.visibility != RepositoryVisibility.PUBLIC) {
            return CacheScope.PRIVATE
        }
        pending.addAll(node.loopbackReferences)
    }
    return CacheScope.PUBLIC
}

/** The escape hatch of [resolveCacheScope], read once at start-up. */
internal object PrivateAsPublicSetting {

    const val SETTING = "INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC"
    private const val LEGACY_SETTING = "REPOSILITE_MAVEN_CACHE_PRIVATE_AS_PUBLIC"

    /**
     * Reads the setting from [environment]: `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC`, then the legacy `REPOSILITE_` spelling. Nothing
     * set, or an empty value, means `false`. Any value other than `true` or `false`, compared without regard to case, is refused,
     * because a loosened security default must not follow from a typo.
     */
    fun read(environment: Map<String, String> = System.getenv()): Boolean {
        val (name, value) = sequenceOf(SETTING, LEGACY_SETTING)
            .mapNotNull { name -> environment[name]?.trim()?.takeIf { it.isNotEmpty() }?.let { name to it } }
            .firstOrNull()
            ?: return false

        return when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Unknown value '$value' for $name, expected 'true' or 'false'")
        }
    }

}
