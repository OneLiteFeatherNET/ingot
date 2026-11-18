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

import com.reposilite.shared.stream.BoundedInputStream
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.Location
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import java.io.IOException
import java.io.InputStream

/** The byte budget of one enrichment call, counted over every range read of that call (artifact enrichment, D4). */
internal const val ARTIFACT_ENRICHMENT_BYTE_BUDGET: Long = 16L * 1024 * 1024

/** A range read would take a call past its byte budget. The message names the budget and nothing of the content. */
internal class ArtifactContentBudgetExceededException(budgetBytes: Long) :
    IOException("Artifact content read exceeded the $budgetBytes byte budget of the enrichment call")

/**
 * The content handle of one enrichment call. It is created by the core for one stored file and reads only that file, through
 * the ranged read of the storage. Every range is charged to the budget of the call before it is read.
 */
internal class StoredArtifactContent(
    private val storage: StorageProvider,
    private val location: Location,
    override val sizeBytes: Long,
    private val budgetBytes: Long = ARTIFACT_ENRICHMENT_BYTE_BUDGET,
) : ArtifactContent {

    private var chargedBytes = 0L

    override fun range(offset: Long, length: Long): InputStream {
        require(offset >= 0 && length >= 0) { "offset and length of a range must not be negative" }
        if (length > budgetBytes - chargedBytes) throw ArtifactContentBudgetExceededException(budgetBytes)
        chargedBytes += length
        val stored = storage.getFile(location, offset, length)
        if (stored.isErr) throw IOException("the stored file could not be read")
        return BoundedInputStream(stored.get(), length)
    }

}
