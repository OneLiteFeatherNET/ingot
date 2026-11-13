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

import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.database.pathHash

/** One request of a write plan: a `_bulk` body, or one query request that cannot be a bulk action. */
internal sealed interface WriteStep {

    /** A `_bulk` body of [actions] actions, in the order they were planned. */
    data class Bulk(val body: String, val actions: Int) : WriteStep

    /** A prefix removal or a reorigin, sent on its own so that the order of the actions is kept. */
    data class Single(val action: IndexAction) : WriteStep

}

/**
 * Plans the requests of a batch (design D4). Upserts and removals of single paths go into `_bulk` bodies of at most [bulkSize]
 * actions and [maxBytes] bytes. A prefix removal or a reorigin ends the bulk request before it and is a request of its own, so
 * the actions are applied in the order they were given. Every document is addressed by the database path hash, so a replay
 * writes the same document again.
 */
internal object OpenSearchBulk {

    /** The largest body of one bulk request (design D4). */
    const val MAX_BYTES: Int = 5 * 1024 * 1024

    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER

    fun plan(index: String, actions: List<IndexAction>, bulkSize: Int, maxBytes: Int = MAX_BYTES): List<WriteStep> {
        val steps = mutableListOf<WriteStep>()
        val body = StringBuilder()
        var count = 0
        var bytes = 0

        fun flush() {
            if (count > 0) steps.add(WriteStep.Bulk(body.toString(), count))
            body.setLength(0)
            count = 0
            bytes = 0
        }

        for (action in actions) {
            val lines = bulkLines(index, action)
            if (lines == null) {
                flush()
                steps.add(WriteStep.Single(action))
                continue
            }
            val size = lines.sumOf { line -> line.toByteArray(Charsets.UTF_8).size + 1 }
            if (count > 0 && (count == bulkSize || bytes + size > maxBytes)) {
                flush()
            }
            lines.forEach { line -> body.append(line).append('\n') }
            count += 1
            bytes += size
        }
        flush()
        return steps
    }

    /** The action line and, for an upsert, the document line of one bulk action. Null for an action that is not a bulk action. */
    private fun bulkLines(index: String, action: IndexAction): List<String>? = when (action) {
        is IndexAction.Upsert -> listOf(
            actionLine("index", index, OpenSearchDocument.id(action.row)),
            mapper.writeValueAsString(OpenSearchDocument.of(action.row)),
        )
        is IndexAction.RemovePath -> listOf(actionLine("delete", index, pathHash(action.repository, action.path)))
        is IndexAction.RemovePrefix, is IndexAction.Reorigin -> null
    }

    private fun actionLine(operation: String, index: String, id: String): String =
        mapper.writeValueAsString(mapOf(operation to mapOf("_index" to index, "_id" to id)))

}
