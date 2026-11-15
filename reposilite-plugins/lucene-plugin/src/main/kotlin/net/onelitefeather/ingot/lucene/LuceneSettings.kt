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

package net.onelitefeather.ingot.lucene

import net.onelitefeather.ingot.search.config.SearchVariables
import java.nio.file.Path

/**
 * The `INGOT_LUCENE_*` variables (design D2 and D9). Each variable falls back to its `REPOSILITE_LUCENE_*` spelling, and the
 * `INGOT_` spelling wins when it is set. A blank value counts as unset. An invalid value is refused with an
 * [IllegalArgumentException] that names the variable and never its value.
 *
 * [defaultDirectory] is `search/lucene` below the data directory of the server; the plugin passes it in, so the rule can be tested
 * without a running server.
 */
internal class LuceneSettings private constructor(
    /** The directory that holds the Lucene index, and nothing else. */
    val directory: Path,
    /** The free space the index volume keeps for the rest of the server. Below it the plugin stops adding to the index. */
    val minFreeBytes: Long,
) {

    companion object {

        const val DIRECTORY_VARIABLE: String = "INGOT_LUCENE_DIRECTORY"
        const val DEFAULT_MIN_FREE_BYTES: Long = 1L shl 30

        fun read(environment: Map<String, String>, defaultDirectory: Path): LuceneSettings {
            val variables = SearchVariables("LUCENE", environment)
            val directory = variables.lookup("DIRECTORY")?.second?.trim()?.let { raw -> Path.of(raw) } ?: defaultDirectory
            return LuceneSettings(directory, minFreeBytes(variables))
        }

        private fun minFreeBytes(variables: SearchVariables): Long {
            val (variable, raw) = variables.lookup("MIN_FREE_BYTES") ?: return DEFAULT_MIN_FREE_BYTES
            val value = raw.trim().toLongOrNull()
            require(value != null && value >= 0) { "$variable must be a whole number of bytes, zero or more" }
            return value
        }
    }
}
