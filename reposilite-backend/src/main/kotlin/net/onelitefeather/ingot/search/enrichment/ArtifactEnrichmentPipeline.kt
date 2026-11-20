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
import net.onelitefeather.ingot.search.outbox.StoredFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** The outcomes of the calls of one enricher since the pipeline started (artifact enrichment, 6.2). */
data class EnrichmentTally(val completed: Long, val skipped: Long, val failed: Long)

/**
 * Calls the enrichers whose extensions match a file (artifact enrichment, D3). Every call goes through [call], which is the
 * executor of D5 in production. A failed call is reported in one warning and counted. The outcome never reaches the index
 * write that follows, so a failing enricher cannot change what a client sees.
 */
internal class ArtifactEnrichmentPipeline(
    private val enrichers: List<ArtifactEnricher>,
    private val call: (ArtifactEnricher, ArtifactEnrichmentRequest) -> EnrichmentResult,
    private val warn: (String) -> Unit,
) {

    private val tallies = ConcurrentHashMap<String, Tally>()

    /**
     * Calls every enricher whose extensions contain the extension of [file]. [content] is asked for once per call, and only when
     * an enricher matches, so a path that no enricher matches is never read.
     */
    fun enrich(
        trigger: EnrichmentTrigger,
        file: ArtifactFileRecord,
        provenance: Provenance,
        stored: StoredFile,
        sha1: String?,
        sha256: String?,
        content: () -> ArtifactContent?,
    ) {
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
            when (val result = call(enricher, request)) {
                is EnrichmentCompleted, is EnrichmentAttributes -> tally.completed.incrementAndGet()
                is EnrichmentSkipped -> tally.skipped.incrementAndGet()
                is EnrichmentFailed -> {
                    tally.failed.incrementAndGet()
                    warn("SEARCH | Artifact enricher '${enricher.id}' failed on ${file.repository}/${file.path}: ${result.reason}")
                }
            }
        }
    }

    /** The outcomes so far, by enricher id. */
    fun counts(): Map<String, EnrichmentTally> = tallies.mapValues { (_, tally) -> tally.snapshot() }

    private class Tally {
        val completed = AtomicLong()
        val skipped = AtomicLong()
        val failed = AtomicLong()

        fun snapshot(): EnrichmentTally = EnrichmentTally(completed.get(), skipped.get(), failed.get())
    }

}
