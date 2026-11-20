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

@file:OptIn(ExperimentalIngotSearchApi::class, ExperimentalIngotPluginApi::class)

package net.onelitefeather.ingot.search.enrichment

import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SearchCapability

/** The reason an attribute set is dropped at a backend that does not declare [SearchCapability.ATTRIBUTES]. */
internal const val UNSUPPORTED_BACKEND = "unsupported-backend"

/** The key prefix under which the core stores the version of each enricher that completed on a row. */
internal const val ENRICHER_VERSION_PREFIX = "core.enricher."

/**
 * The result as the backend sees it (search document attributes, D3). An attribute set at a backend without
 * [SearchCapability.ATTRIBUTES] becomes a skip with [UNSUPPORTED_BACKEND], so it is counted and the row is written without it.
 * Every other result passes unchanged.
 */
internal fun EnrichmentResult.atBoundary(capabilities: Set<SearchCapability>): EnrichmentResult =
    if (this is EnrichmentAttributes && SearchCapability.ATTRIBUTES !in capabilities) EnrichmentSkipped(UNSUPPORTED_BACKEND) else this

/** One call of one enricher on one file: who ran, in which version, and what came back after the boundary. */
internal data class EnricherOutcome(val id: String, val version: String, val result: EnrichmentResult)

/**
 * The attributes a changed file holds after its enrichers ran (search document attributes, D4), or null at a backend without
 * [SearchCapability.ATTRIBUTES], which then gets the row exactly as before.
 */
internal fun attributesOfChangedFile(capabilities: Set<SearchCapability>, outcomes: List<EnricherOutcome>): Map<String, List<String>>? =
    if (SearchCapability.ATTRIBUTES in capabilities) attributesOfChangedFile(outcomes) else null

/**
 * The attributes a changed file holds after its enrichers ran (search document attributes, D4). The stored set is replaced by the
 * union of the attribute sets of this call plus `core.enricher.<id>` with the version of each enricher that completed. A failed or
 * skipped enricher contributes nothing, so its earlier keys and its version are gone and a stale result cannot survive.
 */
internal fun attributesOfChangedFile(outcomes: List<EnricherOutcome>): Map<String, List<String>> {
    val written = LinkedHashMap<String, List<String>>()
    for (outcome in outcomes) {
        when (val result = outcome.result) {
            is EnrichmentAttributes -> written.putAll(result.values)
            is EnrichmentCompleted -> Unit
            is EnrichmentSkipped, is EnrichmentFailed -> continue
        }
        written[ENRICHER_VERSION_PREFIX + outcome.id] = listOf(outcome.version)
    }
    return written
}
