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

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.maven.api.Identifier
import com.reposilite.plugin.api.Facade
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequestError
import com.reposilite.statistics.api.AllResolvedResponse
import com.reposilite.statistics.api.IncrementResolvedRequest
import com.reposilite.statistics.api.IntervalRecord
import com.reposilite.statistics.api.RepositoryStatistics
import com.reposilite.statistics.api.ResolvedCountResponse
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.Route
import com.reposilite.token.RoutePermission.READ
import net.onelitefeather.ingot.identity.model.Action
import panda.std.Result
import panda.std.asSuccess
import panda.std.reactive.Reference
import java.sql.SQLException
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

const val MAX_PAGE_SIZE = 100

/** Retries after the first attempt when a write fails with a deadlock or, on Galera, a certification failure. */
private const val MAX_DEADLOCK_RETRIES = 3
private const val DEADLOCK_OR_CERTIFICATION_FAILURE = "40001"

private fun Exception.isDeadlockOrCertificationFailure(): Boolean =
    generateSequence<Throwable>(this) { current -> current.cause?.takeIf { it !== current } }
        .any { it is SQLException && it.sqlState == DEADLOCK_OR_CERTIFICATION_FAILURE }

class StatisticsFacade internal constructor(
    private val journalist: Journalist,
    private val statisticsEnabled: Reference<Boolean>,
    private val dateIntervalProvider: Reference<DateIntervalProvider>,
    private val statisticsRepository: StatisticsRepository,
    private val accessTokenFacade: AccessTokenFacade,
    private val clock: Clock = Clock.systemDefaultZone()
) : Journalist, Facade {

    private val resolvedRequestsBulk: ConcurrentHashMap<Identifier, Long> = ConcurrentHashMap()
    private val flushMonitor = Any()

    fun incrementResolvedRequest(incrementResolvedRequest: IncrementResolvedRequest) {
        when {
            statisticsEnabled.get() -> resolvedRequestsBulk.merge(incrementResolvedRequest.identifier, incrementResolvedRequest.count) { cached, value -> cached + value }
            else -> logger.debug("Statistics | Cannot increment ${incrementResolvedRequest.identifier}, because statistics are disabled")
        }
    }

    /**
     * Writes the buffered counts. Only the amount that was written is subtracted afterwards, so a request counted during the
     * write stays buffered for the next flush. A failed write leaves its counts in the buffer and is logged instead of thrown,
     * so a shutdown is not blocked by it. Flushes run one at a time, so a count is never written by two flushes.
     */
    fun saveRecordsBulk() {
        synchronized(flushMonitor) {
            val snapshot = resolvedRequestsBulk.toMap()
            if (snapshot.isEmpty()) {
                return
            }

            try {
                writeWithDeadlockRetries(snapshot)
            } catch (exception: Exception) {
                logger.error("Statistics | Cannot save ${snapshot.size} resolved request counts, they stay buffered for the next flush")
                logger.exception(exception)
                return
            }

            snapshot.forEach { (identifier, written) ->
                resolvedRequestsBulk.computeIfPresent(identifier) { _, current -> (current - written).takeIf { it > 0 } }
            }
            logger.debug("Statistics | Saved bulk with ${snapshot.size} records")
        }
    }

    private fun writeWithDeadlockRetries(snapshot: Map<Identifier, Long>) {
        val date = dateIntervalProvider.get().createDate(clock)
        var retries = 0
        while (true) {
            try {
                statisticsRepository.incrementResolvedRequests(snapshot, date)
                return
            } catch (exception: Exception) {
                if (!exception.isDeadlockOrCertificationFailure() || retries == MAX_DEADLOCK_RETRIES) {
                    throw exception
                }
                retries++
            }
        }
    }

    /**
     * The highest entries of the phrase that the token may read, at most [limit] of them. A null [accessToken] or a manager
     * sees every entry. For other tokens the GAV prefixes of their read routes narrow the query before the limit applies,
     * and every entry is then checked against the permissions of the token, so a route cannot widen what a rule denies.
     */
    fun findResolvedRequestsByPhrase(
        repository: String = "",
        phrase: String,
        limit: Int = MAX_PAGE_SIZE,
        accessToken: AccessTokenIdentifier? = null
    ): Result<ResolvedCountResponse, ErrorResponse> {
        if (limit !in 1..MAX_PAGE_SIZE) {
            return badRequestError("Requested invalid page size ($limit, expected 1..$MAX_PAGE_SIZE)")
        }

        val requests =
            if (accessToken == null || accessTokenFacade.hasPermission(accessToken, MANAGER)) {
                statisticsRepository.findResolvedRequestsByPhrase(repository, phrase, limit)
            } else {
                statisticsRepository.findResolvedRequestsByPhrase(repository, phrase, limit, readablePrefixes(accessToken, repository))
                    .filter { accessTokenFacade.hasAccessTo(accessToken, resolvedPath(repository, it.gav), Action.READ) }
            }

        return ResolvedCountResponse(
            sum = requests.sumOf { it.count },
            requests = requests
        ).asSuccess()
    }

    /**
     * The GAV prefixes of the read routes of the token in [repository]. Null when the token has no read route, so the query
     * is not narrowed by prefix and the permissions decide each entry instead.
     */
    private fun readablePrefixes(accessToken: AccessTokenIdentifier, repository: String): Set<String>? {
        val readRoutes = accessTokenFacade.getRoutes(accessToken).filter { it.permission == READ }
        if (readRoutes.isEmpty()) {
            return null
        }
        return readRoutes.mapNotNullTo(mutableSetOf()) { it.toGavPrefix(repository) }
    }

    private fun Route.toGavPrefix(repository: String): String? {
        val repositoryRoot = "/$repository"
        return when {
            repositoryRoot.startsWith(path, ignoreCase = true) -> ""
            path.startsWith("$repositoryRoot/", ignoreCase = true) -> path.substring(repositoryRoot.length + 1)
            else -> null
        }
    }

    private fun resolvedPath(repository: String, gav: String): String =
        "/$repository/${gav.trimStart('/')}"

    fun getAllResolvedStatistics(): Result<AllResolvedResponse, ErrorResponse> =
        when {
            statisticsEnabled.get() ->
                AllResolvedResponse(
                    repositories =
                        statisticsRepository.getAllResolvedRequestsPerRepositoryAsTimeSeries()
                            .mapValues { (_, records) ->
                                val timeSeries = dateIntervalProvider
                                    .map { it.createTimeSeries() }
                                    .associateWith { 0L }

                                (timeSeries + records)
                                    .asSequence()
                                    .map { (date, count) -> IntervalRecord(date.toUTCMillis(), count) }
                                    .sortedBy { it.date }
                                    .toList()
                            }
                            .asSequence()
                            .map { (repository, records) -> RepositoryStatistics(repository, records) }
                            .sortedWith(compareBy({ repository -> -repository.data.sumOf { it.count } }, { it.name }))
                            .toList()
                )
            else ->
                AllResolvedResponse(statisticsEnabled = false)
        }.asSuccess()

    fun countUniqueRecords(): Long =
        statisticsRepository.countUniqueResolvedRequests()

    fun countRecords(): Long =
        statisticsRepository.countResolvedRequests()

    fun statisticsEnabled(): Reference<Boolean> =
        statisticsEnabled

    override fun getLogger(): Logger =
        journalist.logger

}
