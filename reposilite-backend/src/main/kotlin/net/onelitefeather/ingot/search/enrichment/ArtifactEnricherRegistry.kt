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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.enrichment

import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi

private val ENRICHER_ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{1,31}")

/** The enrichers of this server, by id, in registration order (artifact enrichment, D2). */
internal class ArtifactEnricherRegistry(private val warn: (String) -> Unit) {

    private val enrichers = linkedMapOf<String, ArtifactEnricher>()

    /** Registers [enricher]. Returns false when it was refused, and says why in a warning. */
    fun register(enricher: ArtifactEnricher): Boolean {
        val name = enricher.javaClass.name
        if (enricher.apiVersion != ARTIFACT_ENRICHER_API_VERSION) {
            warn("SEARCH | Refused artifact enricher '${enricher.id}' from $name: apiVersion=${enricher.apiVersion}, expected $ARTIFACT_ENRICHER_API_VERSION")
            return false
        }
        if (!ENRICHER_ID_PATTERN.matches(enricher.id)) {
            warn("SEARCH | Refused artifact enricher from $name: id '${enricher.id}' does not match ${ENRICHER_ID_PATTERN.pattern}")
            return false
        }
        val existing = enrichers[enricher.id]
        if (existing != null) {
            warn("SEARCH | Refused artifact enricher '${enricher.id}' from $name: ${existing.javaClass.name} is already registered for this id")
            return false
        }
        enrichers[enricher.id] = enricher
        return true
    }

    /** The enrichers that were accepted, in registration order. */
    fun accepted(): List<ArtifactEnricher> = enrichers.values.toList()

}
