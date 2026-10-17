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

/**
 * The type a repository is declared with. A repository without a declared type keeps the behaviour it always had,
 * see [effectiveRepositoryType].
 *
 * - [HOSTED] stores what is uploaded and proxies nothing.
 * - [PROXY] proxies external URLs only, and caches what it fetches; it takes no uploads.
 * - [GROUP] proxies local repositories only and stores nothing; it takes no uploads.
 */
enum class RepositoryType {
    HOSTED,
    PROXY,
    GROUP
}

/**
 * What a repository is, as the API reports it. A declared [RepositoryType] maps onto the value of the same name; an
 * untyped repository is described by what it proxies.
 */
enum class EffectiveRepositoryType(val wireName: String) {
    HOSTED("hosted"),
    PROXY("proxy"),
    GROUP("group"),
    MIXED("mixed");
}

/**
 * The effective type of a repository.
 *
 * A declared type always wins. An untyped repository is derived from its proxied references: none is [HOSTED],
 * only local ones is [GROUP], only external ones is [PROXY] and a mix of both is [MIXED].
 *
 * @param declared the declared type, null for an untyped repository
 * @param loopbackReferences proxied references to local repositories, blank references excluded
 * @param externalReferences proxied references to remote URLs, blank references excluded
 */
fun effectiveRepositoryType(declared: RepositoryType?, loopbackReferences: Int, externalReferences: Int): EffectiveRepositoryType =
    when {
        declared != null -> EffectiveRepositoryType.valueOf(declared.name)
        loopbackReferences == 0 && externalReferences == 0 -> EffectiveRepositoryType.HOSTED
        externalReferences == 0 -> EffectiveRepositoryType.GROUP
        loopbackReferences == 0 -> EffectiveRepositoryType.PROXY
        else -> EffectiveRepositoryType.MIXED
    }

/**
 * Whether a deployment or a deletion request may change the content of a repository of this type. Untyped repositories
 * accept uploads as they always did. Mirror copies are written by the proxy itself and are not affected by this rule.
 */
fun RepositoryType?.acceptsUploads(): Boolean =
    this != RepositoryType.PROXY && this != RepositoryType.GROUP
