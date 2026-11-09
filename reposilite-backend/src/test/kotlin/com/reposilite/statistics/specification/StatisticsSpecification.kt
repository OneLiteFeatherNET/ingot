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

package com.reposilite.statistics.specification

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.api.Identifier
import com.reposilite.statistics.DailyDateIntervalProvider
import com.reposilite.statistics.StatisticsFacade
import com.reposilite.statistics.api.IncrementResolvedRequest
import com.reposilite.statistics.infrastructure.InMemoryStatisticsRepository
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.ExportService
import com.reposilite.token.Route
import com.reposilite.token.RoutePermission
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.infrastructure.InMemoryAccessTokenRepository
import panda.std.reactive.Reference
import panda.std.reactive.toReference

internal open class StatisticsSpecification {

    private val logger = InMemoryLogger()

    protected val accessTokenFacade = AccessTokenFacade(
        logger,
        InMemoryAccessTokenRepository(),
        InMemoryAccessTokenRepository(),
        ExportService(),
        bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH,
    )

    protected val statisticsFacade = StatisticsFacade(
        journalist = logger,
        statisticsEnabled = Reference.reference(true),
        dateIntervalProvider = DailyDateIntervalProvider.toReference(),
        statisticsRepository = InMemoryStatisticsRepository(),
        accessTokenFacade = accessTokenFacade
    )

    protected fun useResolvedIdentifier(repository: String, gav: String, count: Long = 1): Pair<Identifier, Long> {
        val identifier = Identifier(repository, gav)
        increaseAndSave(identifier, count)
        return identifier to count
    }

    protected fun useReadToken(name: String, path: String): AccessTokenIdentifier {
        val identifier = accessTokenFacade.createAccessToken(CreateAccessTokenRequest(TEMPORARY, name)).accessToken.identifier
        accessTokenFacade.addRoute(identifier, Route(path, RoutePermission.READ))
        return identifier
    }

    private fun increaseAndSave(identifier: Identifier, count: Long) {
        statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier, count))
        statisticsFacade.saveRecordsBulk()
    }

}
