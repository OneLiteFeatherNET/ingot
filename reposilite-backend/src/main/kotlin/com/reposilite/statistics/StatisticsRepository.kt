/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.statistics

import com.reposilite.maven.api.Identifier
import com.reposilite.statistics.api.ResolvedEntry
import java.time.LocalDate
import kotlin.Int.Companion.MAX_VALUE

interface StatisticsRepository {

    fun incrementResolvedRequests(requests: Map<Identifier, Long>, date: LocalDate)

    /**
     * The entries of [repository] whose GAV contains [phrase], highest count first, at most [limit] of them. When
     * [accessibleGavPrefixes] is set, only entries whose GAV starts with one of the prefixes are counted; an empty set matches nothing.
     */
    fun findResolvedRequestsByPhrase(
        repository: String,
        phrase: String,
        limit: Int = MAX_VALUE,
        accessibleGavPrefixes: Set<String>? = null
    ): List<ResolvedEntry>

    fun getAllResolvedRequestsPerRepositoryAsTimeSeries(): Map<String, Map<LocalDate, Long>>

    fun countUniqueResolvedRequests(): Long

    fun countResolvedRequests(): Long

}
