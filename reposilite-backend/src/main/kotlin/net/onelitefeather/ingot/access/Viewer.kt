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

package net.onelitefeather.ingot.access

import com.reposilite.maven.MavenFacade
import com.reposilite.maven.api.RepositoryListQuery
import com.reposilite.token.AccessTokenIdentifier
import net.onelitefeather.ingot.format.RepositoryPath
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi

/**
 * The access decisions of one viewer. Every answer is the answer of the route for the same token and input. A repository
 * that does not exist, or that the viewer may not read, answers false, so a viewer cannot discover private repositories.
 * The token is kept private: the value shows neither its secret nor its identifier.
 */
@ExperimentalIngotPluginApi
class Viewer internal constructor(
    private val token: AccessTokenIdentifier?,
    private val maven: MavenFacade,
) {

    /** Whether the viewer may read the repository, as the root listing decides. */
    fun canReadRepository(repository: String): Boolean =
        maven.getRepository(repository)?.let { maven.canAccessRepository(token, it) } ?: false

    /**
     * Whether the viewer may read the file or directory [path] of [repository], as the resource route decides. The path is
     * judged only in its canonical form: a path with an encoded slash, a dot segment, an empty segment or a leading slash
     * answers false.
     */
    fun canReadPath(repository: String, path: String): Boolean {
        val canonical = canonicalPathOf(repository, path) ?: return false
        val target = maven.getRepository(canonical.repository) ?: return false
        return maven.canAccessResource(token, target, canonical.location).isOk
    }

    /** Whether the repository appears in the repository list for this viewer. */
    fun canListRepository(repository: String): Boolean =
        maven.getRepository(repository)?.let { maven.canListRepository(token, it) } ?: false

    private fun canonicalPathOf(repository: String, path: String): RepositoryPath? {
        if (path.startsWith('/') || path.split('/').any { it.isEmpty() }) return null
        return RepositoryPath.canonicalize("/$repository/$path").orNull()?.takeIf { it.repository == repository }
    }

    /** The repositories the repository list shows to this viewer. Every name is one [canListRepository] answers true for. */
    fun listedRepositories(): Set<String> =
        maven.listRepositories(token, RepositoryListQuery()).repositories.map { it.id }.toSet()

}
