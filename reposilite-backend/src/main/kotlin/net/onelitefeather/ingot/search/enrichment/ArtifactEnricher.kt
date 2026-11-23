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

package net.onelitefeather.ingot.search.enrichment

import com.reposilite.plugin.api.Event
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import java.io.InputStream

/** The version of the enricher API this server speaks. An enricher declares the version it was built for. */
const val ARTIFACT_ENRICHER_API_VERSION: Int = 1

/**
 * A plugin's hook on the indexing of an artifact file (artifact enrichment, D1). The core calls [enrich] for every stored file
 * whose extension is in [extensions]. The registry accepts an enricher only when [apiVersion] equals
 * [ARTIFACT_ENRICHER_API_VERSION] and [id] is unique and matches the pattern .
 */
@ExperimentalIngotSearchApi
interface ArtifactEnricher {

    /** The namespace of this enricher. Unique on the server. */
    val id: String

    /** The API version this enricher was built for. Must equal [ARTIFACT_ENRICHER_API_VERSION]. */
    val apiVersion: Int

    /** The version of this enricher's output. It changes when the output changes. */
    val version: String

    /** The lower case extensions this enricher is called for, for example . */
    val extensions: Set<String>

    /** Enriches one file. Must not hold state that outlives one call. */
    fun enrich(artifact: ArtifactEnrichmentRequest): EnrichmentResult

    /**
     * The attribute keys a search may filter, facet or sort on (search attribute facets, D1). Each key is in this enricher's
     * namespace, `<id>.<name>`. A key that is not declared, or declared without the matching flag, is refused by the search.
     * Empty by default, which keeps every attribute out of filters, facets and sorts.
     */
    @ExperimentalIngotPluginApi
    val declaredAttributes: List<AttributeDeclaration> get() = emptyList()

}

/**
 * One attribute key an [ArtifactEnricher] declares, with what a search may do with it: [filterable] in a filter, [facetable] as a
 * facet with counts per value, [sortable] as the sort of the results. [label] is the human readable name that the facetable key
 * list shows (search attribute facets, D9); it defaults to the key.
 */
@ExperimentalIngotSearchApi
@ExperimentalIngotPluginApi
data class AttributeDeclaration(
    val key: String,
    val filterable: Boolean,
    val facetable: Boolean,
    val sortable: Boolean,
    val label: String = key,
)

/** The stored file an enricher is called for. Built by the core only. */
@ExperimentalIngotSearchApi
class ArtifactEnrichmentRequest internal constructor(
    val repository: String,
    val path: String,
    val file: ArtifactFileRecord,
    val origin: Origin,
    val sourceHost: String?,
    val sizeBytes: Long,
    val lastModified: Long,
    val sha1: String?,
    val sha256: String?,
    val trigger: EnrichmentTrigger,
    val content: ArtifactContent,
)

/** Why an enricher is called: the file changed, or it is indexed by a rebuild. */
@ExperimentalIngotSearchApi
enum class EnrichmentTrigger { CHANGED, REBUILD }

/** A read-only view of the stored bytes of one file, limited to that file. */
@ExperimentalIngotSearchApi
interface ArtifactContent {

    val sizeBytes: Long

    /** The bytes from [offset] for at most [length] bytes. Throws when the byte budget of the call is exhausted. */
    fun range(offset: Long, length: Long): InputStream

}

/** The outcome of one call. The reasons are short and carry no content. */
@ExperimentalIngotSearchApi
sealed interface EnrichmentResult

/** The enricher finished and its output is indexed. */
@ExperimentalIngotSearchApi
data object EnrichmentCompleted : EnrichmentResult

/** The enricher chose not to index this file. [reason] is short and carries no content. */
@ExperimentalIngotSearchApi
data class EnrichmentSkipped(val reason: String) : EnrichmentResult

/** The enricher failed on this file. [reason] is short and carries no content. */
@ExperimentalIngotSearchApi
data class EnrichmentFailed(val reason: String) : EnrichmentResult

/**
 * The enricher finished and writes [values] as attributes of the file. The keys are `<enricher-id>.<name>`, the values are strings,
 * and the set is bounded (artifact enrichment, D1 and D2). The core validates the set and refuses it as a whole when a bound or a
 * rule is broken; see [validateAttributes].
 */
@ExperimentalIngotSearchApi
@ExperimentalIngotPluginApi
data class EnrichmentAttributes(val values: Map<String, List<String>>) : EnrichmentResult

/**
 * Emitted by the search plugin after every plugin has initialised. A plugin registers its [ArtifactEnricher] here, from its own
 * `initialize`.
 */
@ExperimentalIngotSearchApi
class ArtifactEnricherRegistrationEvent internal constructor(
    private val registry: ArtifactEnricherRegistry,
) : Event {

    /** Registers [enricher]. A refusal is reported in a warning and never throws. */
    fun register(enricher: ArtifactEnricher) {
        registry.register(enricher)
    }

}
