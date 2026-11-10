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

package net.onelitefeather.ingot.nexusimport.plan

import com.fasterxml.jackson.databind.JsonNode
import net.onelitefeather.ingot.nexusimport.nexus.NexusClient
import net.onelitefeather.ingot.nexusimport.nexus.NexusHttpException

/**
 * Reads what the planner needs from Nexus: every repository, its Maven settings where the credential may read them, and the
 * asset count and bytes of hosted repositories. Only GET requests are sent.
 */
class NexusDiscovery(private val client: NexusClient) {

    fun discover(): List<RepositoryFacts> =
        client.repositories().map { repository ->
            val settings = if (repository.format == "maven2" && repository.type in MAVEN_TYPES) readSettings(repository.type, repository.name) else null
            val counts = if (repository.type == "hosted") count(repository.name) else Counts(0, 0)
            RepositoryFacts(
                name = repository.name,
                format = repository.format,
                type = repository.type,
                writePolicy = settings?.node?.path("storage")?.path("writePolicy")?.takeIf { it.isTextual }?.asText(),
                versionPolicy = settings?.node?.path("maven")?.path("versionPolicy")?.takeIf { it.isTextual }?.asText(),
                remoteUrl = settings?.node?.path("proxy")?.path("remoteUrl")?.takeIf { it.isTextual }?.asText(),
                memberNames = settings?.node?.path("group")?.path("memberNames")?.map { it.asText() }.orEmpty(),
                settingsReadable = settings?.readable ?: true,
                components = counts.components,
                bytes = counts.bytes,
            )
        }

    private class Settings(val node: JsonNode?, val readable: Boolean)

    private class Counts(val components: Long, val bytes: Long)

    private fun readSettings(type: String, name: String): Settings =
        try {
            Settings(client.mavenSettings(type, name), readable = true)
        } catch (exception: NexusHttpException) {
            if (exception.status != 403) throw exception
            Settings(null, readable = false)
        }

    private fun count(repository: String): Counts {
        var components = 0L
        var bytes = 0L
        var token: String? = null
        do {
            val page = client.assetPage(repository, token)
            components += page.items.size
            bytes += page.items.sumOf { it.fileSize.coerceAtLeast(0) }
            token = page.continuationToken
        } while (token != null)
        return Counts(components, bytes)
    }

    companion object {
        private val MAVEN_TYPES = setOf("hosted", "proxy", "group")
    }
}

/** The dry run: discovery and planning, nothing else. It has no port that writes, so it cannot change Nexus or Ingot. */
class NexusDryRun(private val discovery: NexusDiscovery) {

    fun run(selection: Set<String>?, existingTargets: Set<String>): ImportPlan =
        ImportPlanner(existingTargets).plan(discovery.discover(), selection)
}
