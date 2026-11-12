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

package net.onelitefeather.ingot.search.access

import net.onelitefeather.ingot.search.ArtifactFilePage
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchEngine
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery

/**
 * Checks the rows of an engine against the caller's [SearchAccess] once more (search backend registration, D6). A file the
 * caller may not see is dropped, and a repository of a group the caller may not see is removed. Every drop is passed to
 * [onMismatch] with the repository and the file path, or the group coordinates, so the caller can log it and count it.
 * Facets and status are counts and state, not rows, and pass through unchanged.
 */
internal class AccessCheckedSearchEngine(
    private val delegate: ArtifactSearchEngine,
    private val onMismatch: (repository: String, location: String) -> Unit,
) : ArtifactSearchEngine by delegate {

    override fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage {
        val page = delegate.search(query, access)
        val items = page.items.mapNotNull { item ->
            val visible = item.repositories.filter { presence ->
                val allowed = access.repositories.any { it.repository == presence.name }
                if (!allowed) onMismatch(presence.name, "${item.groupId}:${item.artifactId}:${item.version}")
                allowed
            }
            if (visible.isEmpty()) null else item.copy(repositories = visible)
        }
        return page.copy(items = items)
    }

    override fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage {
        val page = delegate.files(query, access)
        val items = page.items.filter { item ->
            val allowed = access.matches(item.repository, item.path)
            if (!allowed) onMismatch(item.repository, item.path)
            allowed
        }
        return page.copy(items = items)
    }

}
