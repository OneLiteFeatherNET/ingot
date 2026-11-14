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

package net.onelitefeather.ingot.meilisearch

import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.database.pathHash
import java.time.Clock
import java.time.Duration

/**
 * The writes of the backend (design D4, D5). Every write returns a task of the server; the writer waits for the tasks of its batch
 * within the task bound, so that a search after the batch sees its documents. A failed task fails the batch with the server's
 * error code. A task still running at the bound releases the batch as busy, without an attempt, and the replay is idempotent.
 */
internal class MeilisearchWriter(
    private val client: MeilisearchClient,
    private val index: String,
    private val batchSize: Int,
    private val taskWait: Duration,
    private val clock: Clock,
    private val waiter: MeilisearchWaiter,
) {

    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER

    /** Applies the actions in order. Upserts between two other actions go in one batch of documents, in requests of [batchSize]. */
    fun apply(actions: List<IndexAction>) {
        val tasks = mutableListOf<Long>()
        val pending = mutableListOf<ObjectNode>()
        fun flush() {
            pending.chunked(batchSize).forEach { chunk -> tasks += enqueue("POST", "${documents()}?primaryKey=id", chunk) }
            pending.clear()
        }
        actions.forEach { action ->
            when (action) {
                is IndexAction.Upsert -> pending += MeilisearchDocument.of(action.row)
                is IndexAction.RemovePath -> {
                    flush()
                    tasks += enqueue("DELETE", "${documents()}/${MeilisearchDocument.id(action.repository, action.path)}", null)
                }
                is IndexAction.RemovePrefix -> {
                    flush()
                    val scope = "repository = ${MeilisearchFilters.quote(action.repository)} AND " +
                        "${MeilisearchFilters.path(PathPredicate.Exact(action.prefix))} AND indexed_at <= ${action.notAfter.toEpochMilli()}"
                    tasks += enqueue("POST", "${documents()}/delete", mapOf("filter" to scope))
                }
                is IndexAction.Reorigin -> {
                    flush()
                    tasks += reorigin(action)
                }
            }
        }
        flush()
        await(tasks)
    }

    /** Partial updates of existing documents, in batches; awaited like [apply]. */
    fun update(updates: List<ObjectNode>) {
        await(updates.chunked(batchSize).map { chunk -> enqueue("PUT", documents(), chunk) })
    }

    /** Removes the documents that match [filter] (an expression of the filter language), awaited. */
    fun deleteWhere(filter: String) {
        await(listOf(enqueue("POST", "${documents()}/delete", mapOf("filter" to filter))))
    }

    /** The ids of at most [limit] documents that match [filter], read from the server. */
    fun idsWhere(filter: String, limit: Int): List<String> {
        val answer = client.json("POST", "/indexes/$index/documents/fetch", mapOf("filter" to filter, "fields" to listOf("id"), "limit" to limit))
        return answer.path("results").map { it.path("id").asText() }
    }

    /**
     * The origin of every document below the directory, set by partial updates. The documents are read by page with a filter the
     * update does not change, so a page never repeats and no document is missed.
     */
    private fun reorigin(action: IndexAction.Reorigin): List<Long> {
        val filter = "repository = ${MeilisearchFilters.quote(action.repository)} AND " +
            "directories = ${MeilisearchFilters.quote(action.directory)}"
        val tasks = mutableListOf<Long>()
        var offset = 0
        while (true) {
            val answer = client.json(
                "POST",
                "/indexes/$index/documents/fetch",
                mapOf("filter" to filter, "fields" to listOf("id"), "offset" to offset, "limit" to PAGE),
            )
            val ids = answer.path("results").map { it.path("id").asText() }
            if (ids.isEmpty()) break
            val updates = ids.map { id ->
                mapper.createObjectNode().apply {
                    put("id", id)
                    put("origin", action.provenance.origin.name)
                    if (action.provenance.sourceHost == null) putNull("source_host") else put("source_host", action.provenance.sourceHost)
                }
            }
            tasks += enqueue("PUT", documents(), updates)
            offset += ids.size
            if (ids.size < PAGE) break
        }
        return tasks
    }

    /** Sends one write and returns the uid of its task. */
    private fun enqueue(method: String, path: String, body: Any?): Long {
        val answer = client.json(method, path, body)
        return answer.path("taskUid").takeIf { it.isNumber }?.asLong()
            ?: throw MeilisearchRejectedException("the server did not return a task for $method ${path.substringBefore('?')}")
    }

    /**
     * Waits for every task in turn, within one bound that starts now. A task that fails fails the batch; a task still running at
     * the bound raises [BackendBusyException] with the retry delay of the design.
     */
    fun await(tasks: List<Long>) {
        val deadline = clock.instant().plus(taskWait)
        tasks.forEach { uid ->
            while (true) {
                val task = client.json("GET", "/tasks/$uid")
                when (task.path("status").asText()) {
                    "succeeded" -> break
                    "failed" -> {
                        val code = task.path("error").path("code").asText("unknown")
                        throw MeilisearchRejectedException(client.redact("the task $uid failed with $code"))
                    }
                    else -> {
                        if (!clock.instant().isBefore(deadline)) {
                            throw BackendBusyException("the tasks of this batch are still running after the bound", BUSY_RETRY_AFTER)
                        }
                        waiter.pause(POLL_INTERVAL)
                    }
                }
            }
        }
    }

    private fun documents(): String = "/indexes/$index/documents"

    private companion object {
        const val PAGE = 1000
        val POLL_INTERVAL: Duration = Duration.ofMillis(250)
        val BUSY_RETRY_AFTER: Duration = Duration.ofSeconds(10)
    }

}
