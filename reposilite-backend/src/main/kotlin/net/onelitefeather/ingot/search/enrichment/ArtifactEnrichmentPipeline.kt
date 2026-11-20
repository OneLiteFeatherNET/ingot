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

import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SearchCapability
import net.onelitefeather.ingot.search.outbox.StoredFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The outcomes of the calls of one enricher since the pipeline started (artifact enrichment, D7). [skipped] includes the
 * [busy] skips of a full queue, and [failures] counts the failed calls per reason.
 */
data class EnrichmentTally(
    val completed: Long,
    val skipped: Long,
    val failed: Long,
    val busy: Long = 0,
    val failures: Map<String, Long> = emptyMap(),
) {

    /** Every call the pipeline made, whatever its outcome. */
    val calls: Long
        get() = completed + skipped + failed

}

/**
 * Calls the enrichers whose extensions match a file (artifact enrichment, D3). Every call goes through [call], which is the
 * executor of D5 in production. A failed call is reported in one warning and counted. An attribute set is validated and then
 * passed through the boundary of the backend before it is counted, so a refused set counts as failed and a set the backend
 * cannot store counts as skipped (search document attributes, D1 and D3).
 */
internal class ArtifactEnrichmentPipeline(
    private val enrichers: List<ArtifactEnricher>,
    private val call: (ArtifactEnricher, ArtifactEnrichmentRequest) -> EnrichmentResult,
    private val warn: (String) -> Unit,
) {

    private val tallies = ConcurrentHashMap<String, Tally>().apply {
        enrichers.forEach { put(it.id, Tally()) }
    }

    /**
     * Calls every enricher whose extensions contain the extension of [file]. [content] is asked for once per call, and only when
     * an enricher matches, so a path that no enricher matches is never read. Returns the outcome of each call, as the backend
     * with [capabilities] sees it.
     */
    fun enrich(
        trigger: EnrichmentTrigger,
        file: ArtifactFileRecord,
        provenance: Provenance,
        stored: StoredFile,
        sha1: String?,
        sha256: String?,
        capabilities: Set<SearchCapability> = emptySet(),
        content: () -> ArtifactContent?,
    ): List<EnricherOutcome> {
        val outcomes = mutableListOf<EnricherOutcome>()
        val extension = file.extension.lowercase()
        for (enricher in enrichers.filter { extension in it.extensions }) {
            val bytes = content() ?: continue
            val request = ArtifactEnrichmentRequest(
                repository = file.repository,
                path = file.path,
                file = file,
                origin = provenance.origin,
                sourceHost = provenance.sourceHost,
                sizeBytes = stored.sizeBytes,
                lastModified = stored.lastModified.toEpochMilli(),
                sha1 = sha1,
                sha256 = sha256,
                trigger = trigger,
                content = bytes,
            )
            val tally = tallies.getOrPut(enricher.id) { Tally() }
            val result = call(enricher, request).validated(enricher.id).atBoundary(capabilities)
            outcomes += EnricherOutcome(enricher.id, enricher.version, result)
            when (result) {
                is EnrichmentCompleted, is EnrichmentAttributes -> tally.completed.incrementAndGet()
                is EnrichmentSkipped -> {
                    tally.skipped.incrementAndGet()
                    if (result.reason == BUSY_REASON) tally.busy.incrementAndGet()
                }
                is EnrichmentFailed -> {
                    tally.failed.incrementAndGet()
                    tally.fail(result.reason)
                    warn("SEARCH | Artifact enricher '${enricher.id}' failed on ${file.repository}/${file.path}: ${result.reason}")
                }
            }
        }
        return outcomes
    }

    /** The id and version of every enricher whose extensions contain the extension of [file]. */
    fun matchingVersions(file: ArtifactFileRecord): Map<String, String> {
        val extension = file.extension.lowercase()
        return enrichers.filter { extension in it.extensions }.associate { it.id to it.version }
    }

    /** An attribute set that breaks a rule of D1 becomes a failure that names the rule and never a value. */
    private fun EnrichmentResult.validated(enricherId: String): EnrichmentResult =
        if (this is EnrichmentAttributes) validateAttributes(enricherId, values)?.let { EnrichmentFailed(it.reason) } ?: this else this

    /** The outcomes so far, by enricher id. Every registered enricher is present, with zeros until it is called. */
    fun counts(): Map<String, EnrichmentTally> = tallies.mapValues { (_, tally) -> tally.snapshot() }

    private class Tally {
        val completed = AtomicLong()
        val skipped = AtomicLong()
        val failed = AtomicLong()
        val busy = AtomicLong()
        private val failures = ConcurrentHashMap<String, AtomicLong>()

        /** Counts a failure under its reason. Reasons come from plugins, so only the first few distinct ones get their own count. */
        fun fail(reason: String) {
            val key = if (failures.containsKey(reason) || failures.size < MAX_FAILURE_REASONS) reason else OTHER_FAILURE_REASON
            failures.computeIfAbsent(key) { AtomicLong() }.incrementAndGet()
        }

        fun snapshot(): EnrichmentTally = EnrichmentTally(
            completed = completed.get(),
            skipped = skipped.get(),
            failed = failed.get(),
            busy = busy.get(),
            failures = failures.mapValues { (_, count) -> count.get() },
        )
    }

    companion object {
        /** The distinct failure reasons counted per enricher; later reasons are counted under [OTHER_FAILURE_REASON]. */
        const val MAX_FAILURE_REASONS = 16

        /** The reason under which failures beyond [MAX_FAILURE_REASONS] are counted. */
        const val OTHER_FAILURE_REASON = "other"

        /** The skip reason of a call refused because the queue of the executor was full (D5). */
        private const val BUSY_REASON = "busy"
    }

}
