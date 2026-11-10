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

@file:Suppress("FunctionName")

package com.reposilite.statistics

import com.reposilite.ExperimentalLocalSpecificationJunitExtension
import com.reposilite.ExperimentalRemoteSpecificationJunitExtension
import com.reposilite.PostgresSpecificationJunitExtension
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.RecommendedRemoteSpecificationJunitExtension
import com.reposilite.maven.api.Identifier
import com.reposilite.statistics.api.AllResolvedResponse
import com.reposilite.statistics.api.IntervalRecord
import com.reposilite.statistics.api.RepositoryStatistics
import com.reposilite.statistics.api.ResolvedCountResponse
import com.reposilite.statistics.infrastructure.SqlStatisticsRepository
import com.reposilite.statistics.specification.StatisticsIntegrationSpecification
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.RoutePermission.READ
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.UNAUTHORIZED
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kong.unirest.core.Unirest.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import panda.std.component1

@ExtendWith(ExperimentalRemoteSpecificationJunitExtension::class)
internal class ExperimentalRemoteStatisticsIntegrationTest : StatisticsIntegrationTest()

@ExtendWith(RecommendedRemoteSpecificationJunitExtension::class)
internal class RemoteStatisticsIntegrationTest : StatisticsIntegrationTest()

@ExtendWith(PostgresSpecificationJunitExtension::class)
internal class PostgresStatisticsIntegrationTest : StatisticsIntegrationTest()

@ExtendWith(ExperimentalLocalSpecificationJunitExtension::class)
internal class ExperimentalLocalStatisticsIntegrationTest : StatisticsIntegrationTest()

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalStatisticsIntegrationTest : StatisticsIntegrationTest()

internal abstract class StatisticsIntegrationTest : StatisticsIntegrationSpecification() {

    @Test
    fun `should return unique number of requests`() {
        // given: a route to request
        val endpoint = "$base/api/statistics/resolved/unique"
        repeat(10) { useResolvedRequest("releases", "com/reposilite.jar", "content") }

        // when: stats service is requested without valid credentials
        val unauthorizedResponse = get(endpoint).asString()

        // then: service rejects request
        assertThat(unauthorizedResponse.status).isEqualTo(UNAUTHORIZED.code)

        // given: a valid credentials
        val (name, secret) = useAuth("name", "secret", listOf(MANAGER))

        // when: service is requested with valid credentials
        val response = get(endpoint)
            .basicAuth(name, secret)
            .asObject { it.contentAsString.toLong() }

        // then: service responds with valid stats data
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo(1)
    }

    @Test
    fun `should return registered number of endpoint calls`() {
        // given: a route to request and check
        val (identifier) = useResolvedRequest("releases", "com/reposilite.jar", "content")
        val endpoint = "$base/api/statistics/resolved/phrase/1$identifier"

        // when: stats service is requested without valid credentials
        val unauthorizedResponse = get(endpoint).asString()

        // then: service rejects request
        assertThat(unauthorizedResponse.status).isEqualTo(UNAUTHORIZED.code)

        // given: a valid credentials
        val (name, secret) = useAuth("name", "secret", emptyList(),  mapOf(identifier.toString() to READ))

        // when: service is requested with valid credentials
        val response = get(endpoint)
            .basicAuth(name, secret)
            .asObject(ResolvedCountResponse::class.java)

        // then: service responds with valid stats data
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body.sum).isEqualTo(1)
        assertThat(response.body.requests[0].gav).isEqualTo(identifier.gav)
    }

    @Test
    fun `should return time-series`() {
        // given: a database with some requests
        val hackyDatabaseStateAccessor = SqlStatisticsRepository(reposilite.database, reposilite.journalist, emptyArray())

        repeat(2) { // repeat 2 times to verify aggregation
            repeat(24) { index -> // 24 months
                val date = LocalDate.now()
                    .minusMonths(index.toLong())
                    .withDayOfMonth(1)

                hackyDatabaseStateAccessor.incrementResolvedRequests(
                    requests = mapOf(
                        Identifier("releases", "/com/reposilite/1.0.0/reposilite-1.0.0.jar") to index.toLong(),
                        Identifier("snapshots", "/com/reposilite/1.0.0-SNAPSHOT/reposilite-1.0.0-SNAPSHOT.jar") to index.toLong()
                    ),
                    date = date
                )
            }
        }

        // when: stats service is requested without valid credentials
        val unauthorizedResponse = get("$base/api/statistics/resolved/all").asString()

        // then: service rejects request
        assertThat(unauthorizedResponse.status).isEqualTo(UNAUTHORIZED.code)

        // given: a valid credentials
        val (name, secret) = useAuth("name", "secret", listOf(MANAGER))

        // when: service is requested with valid credentials
        val response = get("$base/api/statistics/resolved/all")
            .basicAuth(name, secret)
            .asObject(AllResolvedResponse::class.java)

        // then: service should respond with time-series not older than a year
        assertThat(response.status).isEqualTo(OK.code)

        assertThat(response.body).isEqualTo(
            AllResolvedResponse(
                repositories = listOf("releases", "snapshots")
                    .map { repository ->
                        RepositoryStatistics(
                            name = repository,
                            data = (0..ChronoUnit.MONTHS.between(LocalDate.now().minusYears(1), LocalDate.now())) // may be 12 or 13 months
                                .map { index ->
                                    IntervalRecord(
                                        date = LocalDate.now().minusMonths(index).withDayOfMonth(1).toUTCMillis(),
                                        count = 2L * index
                                    )
                                }
                                .sortedBy { it.date }
                        )
                    }
                    .sortedBy { it.name }
            )
        )
    }

    @Test
    fun `should return as many entries as the phrase limit asks for`() {
        // given: two entries matching the phrase, with 2 and 3 resolutions
        useResolvedPhraseEntries()
        val (name, secret) = useAuth("manager", "secret", listOf(MANAGER))

        // when: two entries are requested
        val response = get("$base/api/statistics/resolved/phrase/2/releases/com/reposilite")
            .basicAuth(name, secret)
            .asObject(ResolvedCountResponse::class.java)

        // then: both entries are returned and the sum covers both
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body.requests.map { it.gav })
            .describedAs("a limit of two must return both matching entries, highest first")
            .containsExactly("x/com/reposilite/b.jar", "com/reposilite/a.jar")
        assertThat(response.body.sum).describedAs("the sum must cover the returned entries").isEqualTo(5)
    }

    @Test
    fun `should refuse a phrase limit outside of the allowed range`() {
        // given: a manager token and entries matching the phrase
        useResolvedPhraseEntries()
        val (name, secret) = useAuth("manager", "secret", listOf(MANAGER))

        listOf("0", "101").forEach { limit ->
            // when: a limit outside of 1..100 is requested
            val response = get("$base/api/statistics/resolved/phrase/$limit/releases/com/reposilite")
                .basicAuth(name, secret)
                .asString()

            // then: the request is refused with a bad request
            assertThat(response.status)
                .describedAs("the limit $limit is outside of 1..100 and must be refused")
                .isEqualTo(BAD_REQUEST.code)
        }
    }

    @Test
    fun `should refuse a phrase limit that is not a number`() {
        // given: a manager token and entries matching the phrase
        useResolvedPhraseEntries()
        val (name, secret) = useAuth("manager", "secret", listOf(MANAGER))

        // when: a limit that is not a number is requested
        val response = get("$base/api/statistics/resolved/phrase/many/releases/com/reposilite")
            .basicAuth(name, secret)
            .asString()

        // then: the request is refused with a bad request
        assertThat(response.status).describedAs("a limit that is not a number must be refused").isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `should return only entries under paths the token may read`() {
        // given: entries matching the phrase, and a token that may read com/reposilite only
        useResolvedPhraseEntries()
        val (name, secret) = useAuth("reader", "secret", emptyList(), mapOf("/releases/com/reposilite" to READ))

        // when: the phrase is requested with a generous limit
        val response = get("$base/api/statistics/resolved/phrase/10/releases/com/reposilite")
            .basicAuth(name, secret)
            .asObject(ResolvedCountResponse::class.java)

        // then: only the readable entry is returned
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body.requests.map { it.gav })
            .describedAs("an entry outside the readable path must not be returned to the token")
            .containsExactly("com/reposilite/a.jar")
        assertThat(response.body.sum).describedAs("the sum must not count unreadable entries").isEqualTo(2)
    }

    @Test
    fun `should return the readable entry when the limit is reached by an unreadable one`() {
        // given: entries matching the phrase, the unreadable one has the highest count
        useResolvedPhraseEntries()
        val (name, secret) = useAuth("reader", "secret", emptyList(), mapOf("/releases/com/reposilite" to READ))

        // when: a single entry is requested
        val response = get("$base/api/statistics/resolved/phrase/1/releases/com/reposilite")
            .basicAuth(name, secret)
            .asObject(ResolvedCountResponse::class.java)

        // then: the readable entry fills the limit instead of an empty list
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body.requests.map { it.gav })
            .describedAs("the readable entry must be returned when the limit is one")
            .containsExactly("com/reposilite/a.jar")
    }

    @Test
    fun `should show every matching entry to a manager`() {
        // given: entries matching the phrase
        useResolvedPhraseEntries()
        val (name, secret) = useAuth("manager", "secret", listOf(MANAGER))

        // when: the phrase is requested by a manager
        val response = get("$base/api/statistics/resolved/phrase/10/releases/com/reposilite")
            .basicAuth(name, secret)
            .asObject(ResolvedCountResponse::class.java)

        // then: every matching entry is returned
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body.requests.map { it.gav })
            .describedAs("a manager must see every matching entry")
            .containsExactly("x/com/reposilite/b.jar", "com/reposilite/a.jar")
    }

    @Test
    fun `should refuse the phrase lookup to a token without read access to the phrase path`() {
        // given: entries matching the phrase, and a token that may read nothing
        useResolvedPhraseEntries()
        val (name, secret) = useAuth("stranger", "secret")

        // when: the phrase is requested
        val response = get("$base/api/statistics/resolved/phrase/10/releases/com/reposilite")
            .basicAuth(name, secret)
            .asString()

        // then: the request is refused as forbidden
        assertThat(response.status).describedAs("a token without read access must be refused").isEqualTo(FORBIDDEN.code)
    }

    private fun useResolvedPhraseEntries() {
        repeat(2) { useResolvedRequest("releases", "com/reposilite/a.jar", "content") }
        repeat(3) { useResolvedRequest("releases", "x/com/reposilite/b.jar", "content") }
    }

}