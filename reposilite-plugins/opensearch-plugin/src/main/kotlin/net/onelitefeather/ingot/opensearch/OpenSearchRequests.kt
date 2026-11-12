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

package net.onelitefeather.ingot.opensearch

import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction

/**
 * The bodies of the two requests that act on many documents at once (design D4): the prefix removal, which is
 * `_delete_by_query`, and the reorigin, which is `_update_by_query` with a stored script. Every value of a request goes into the
 * query or the script parameters, never into the text of the script, and the script is one constant of the plugin.
 */
internal object OpenSearchRequests {

    /** The id of the stored Painless script that sets the origin and the source host of matching documents. */
    const val REORIGIN_SCRIPT_ID: String = "ingot-reorigin-v1"

    /** The source of the stored script. The parameters are `origin` and `source_host` and nothing else. */
    const val REORIGIN_SCRIPT_SOURCE: String =
        "ctx._source.origin = params.origin; ctx._source.source_host = params.source_host;"

    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER

    /**
     * Removes the rows of the repository at the prefix path and below it, indexed at or before the cut-off. Rows indexed later by
     * a concurrent deployment are kept, as in the database engine.
     */
    fun deleteByPrefix(action: IndexAction.RemovePrefix): ObjectNode {
        val body = mapper.createObjectNode()
        val filter = body.putObject("query").putObject("bool").putArray("filter")
        filter.addObject().putObject("term").put("repository", action.repository)
        filter.addObject().putObject("range").putObject("indexed_at").put("lte", action.notAfter.toEpochMilli())
        val pathCondition = filter.addObject().putObject("bool")
        pathCondition.putArray("should").apply {
            addObject().putObject("term").put("path", action.prefix)
            addObject().putObject("prefix").put("path", "${action.prefix}/")
        }
        pathCondition.put("minimum_should_match", 1)
        return body
    }

    /**
     * Sets the origin and the source host of the rows of the repository below the directory, by the stored script. The range is
     * the half-open one of the database: the paths from `directory/` up to `directory0`.
     */
    fun reorigin(action: IndexAction.Reorigin): ObjectNode {
        val body = mapper.createObjectNode()
        val filter = body.putObject("query").putObject("bool").putArray("filter")
        filter.addObject().putObject("term").put("repository", action.repository)
        filter.addObject().putObject("range").putObject("path")
            .put("gte", "${action.directory}/")
            .put("lt", "${action.directory}0")
        val script = body.putObject("script")
        script.put("id", REORIGIN_SCRIPT_ID)
        val parameters = script.putObject("params")
        parameters.put("origin", action.provenance.origin.name)
        parameters.put("source_host", action.provenance.sourceHost)
        return body
    }

}
