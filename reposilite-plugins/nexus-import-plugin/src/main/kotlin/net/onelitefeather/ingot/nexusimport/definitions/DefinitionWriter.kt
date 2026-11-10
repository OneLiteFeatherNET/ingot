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

package net.onelitefeather.ingot.nexusimport.definitions

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.maven.RepositoryType
import com.reposilite.maven.RepositoryVisibility
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import net.onelitefeather.ingot.nexusimport.plan.IngotType
import net.onelitefeather.ingot.nexusimport.plan.PlannedRepository

/** The `maven` settings domain as the writer needs it: its repositories and the entity tag they were read with. */
data class DomainState(val repositoryIds: Set<String>, val entityTag: String)

sealed interface UpdateResult {
    data object Stored : UpdateResult
    /** The entity tag no longer matches the stored domain. The caller reads it again and retries. */
    data object PreconditionFailed : UpdateResult
    /** The shared settings file owns the domain, so nothing can be stored through the API. */
    data object ManagedExternally : UpdateResult
}

/** The port to the `maven` settings domain. The production adapter wraps the shared configuration facade. */
interface MavenDomainPort {
    fun read(): DomainState
    fun update(ifMatch: String, transform: (List<RepositorySettings>) -> List<RepositorySettings>): UpdateResult
}

/** A definition that must be entered by hand, with the JSON to paste into the Maven settings. */
data class ManualDefinition(val target: String, val json: String)

data class WriteResult(
    val created: List<String>,
    /** Names the domain already held, which are never changed. */
    val skipped: List<String>,
    val manual: List<ManualDefinition>,
    /** Definitions not written because the domain kept changing during the two attempts. */
    val unwritten: List<String>,
)

/**
 * Creates the planned definitions in one update of the `maven` domain. A name the domain already holds is skipped, never
 * changed. A precondition failure re-reads the domain and tries once more; a managed domain turns every definition into a
 * manual row with its JSON.
 */
class DefinitionWriter(private val domain: MavenDomainPort) {

    fun write(planned: List<PlannedRepository>): WriteResult {
        var state = domain.read()
        repeat(MAX_ATTEMPTS) { attempt ->
            val skipped = planned.filter { it.targetName in state.repositoryIds }.map { it.targetName }
            val fresh = planned.filter { it.targetName !in state.repositoryIds }
            if (fresh.isEmpty()) {
                return WriteResult(emptyList(), skipped, emptyList(), emptyList())
            }
            val settings = fresh.map(::toSettings)
            when (domain.update(state.entityTag) { current -> current + settings.filter { s -> current.none { it.id == s.id } } }) {
                UpdateResult.Stored -> return WriteResult(fresh.map { it.targetName }, skipped, emptyList(), emptyList())
                UpdateResult.ManagedExternally -> return WriteResult(
                    emptyList(), skipped, settings.map { ManualDefinition(it.id, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(it)) }, emptyList(),
                )
                UpdateResult.PreconditionFailed -> {
                    if (attempt == MAX_ATTEMPTS - 1) {
                        return WriteResult(emptyList(), skipped, emptyList(), fresh.map { it.targetName })
                    }
                    state = domain.read()
                }
            }
        }
        return WriteResult(emptyList(), emptyList(), emptyList(), planned.map { it.targetName })
    }

    companion object {
        private const val MAX_ATTEMPTS = 2
        private val MAPPER = ObjectMapper()

        /** The definition of a planned repository. Visibility is always private, and snapshots are kept for snapshot repositories. */
        fun toSettings(planned: PlannedRepository): RepositorySettings = RepositorySettings(
            id = planned.targetName,
            visibility = RepositoryVisibility.PRIVATE,
            type = when (planned.type) {
                IngotType.HOSTED -> RepositoryType.HOSTED
                IngotType.PROXY -> RepositoryType.PROXY
                IngotType.GROUP -> RepositoryType.GROUP
            },
            redeployment = planned.redeployment,
            preserveSnapshots = planned.preserveSnapshots,
            proxied = when (planned.type) {
                IngotType.HOSTED -> emptyList()
                IngotType.PROXY -> listOfNotNull(planned.proxyRemoteUrl).map { MirroredRepositorySettings(reference = it, store = true) }
                IngotType.GROUP -> planned.members.map { MirroredRepositorySettings(reference = it) }
            },
        )
    }
}
