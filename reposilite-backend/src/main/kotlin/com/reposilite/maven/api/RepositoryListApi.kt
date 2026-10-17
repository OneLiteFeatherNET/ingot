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

package com.reposilite.maven.api

import com.reposilite.maven.RepositoryVisibility

data class RepositoryListResponse(
    val repositories: List<RepositorySummary>
)

data class RepositorySummary(
    val id: String,
    val visibility: RepositoryVisibility,
    val description: String,
    val tags: List<String>,
    /** The effective type in lowercase: hosted, proxy, group or mixed. */
    val type: String,
    /** True when the type is declared in the settings, false when it is derived from the proxied repositories. */
    val typeExplicit: Boolean,
)

/**
 * Search criteria of the repository list.
 *
 * [query] matches case-insensitively against the id, the description and every tag of a repository.
 * [tags] keeps only the repositories that carry every one of them. Stored tags are lowercase, so requested
 * tags are compared in lowercase as well. Blank values filter nothing.
 */
data class RepositoryListQuery(
    val query: String? = null,
    val tags: List<String> = emptyList(),
) {

    fun matches(repository: RepositorySummary): Boolean =
        matchesQuery(repository) && matchesTags(repository)

    private fun matchesQuery(repository: RepositorySummary): Boolean {
        val needle = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return true

        return repository.id.lowercase().contains(needle) ||
            repository.description.lowercase().contains(needle) ||
            repository.tags.any { it.lowercase().contains(needle) }
    }

    private fun matchesTags(repository: RepositorySummary): Boolean =
        tags
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .all { it in repository.tags }

}
