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

import com.reposilite.statistics.specification.StatisticsSpecification
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.api.CreateAccessTokenRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk

internal class StatisticsFacadeTest : StatisticsSpecification() {

    @Test
    fun `should increase records after saving the bulk`() {
        // given: an uri to stored request
        val (identifier, count) = useResolvedIdentifier("releases", "/panda-lang/reposilite", 2)
        val (repository, gav) = identifier

        // when: the given phrase is requested
        val result = statisticsFacade.findResolvedRequestsByPhrase(repository, gav)

        // then: it should be properly stored in repository as a single record
        val response = assertOk(result)
        assertThat(response.sum).isEqualTo(count)
        assertThat(response.requests.size).isEqualTo(1)
        assertThat(response.requests[0].gav).isEqualTo(gav)
    }

    @Test
    fun `should find record by given phrase`() {
        // given: a requested uri and a phrase to search for
        val (identifier, count) = useResolvedIdentifier("releases", "/panda-lang/reposilite")
        val phrase = "reposilite"

        // when: the given phrase is requested
        val result = statisticsFacade.findResolvedRequestsByPhrase(identifier.repository, phrase)

        // then: the phrase should be found
        val response = assertOk(result)
        assertThat(response.sum).isEqualTo(count)
        assertThat(response.requests[0].gav).isEqualTo(identifier.gav)
    }

    @Test
    fun `should properly count records and unique records`() {
        // given: two different identifiers
        useResolvedIdentifier("releases", "/first", 2)
        useResolvedIdentifier("releases", "/first/second")
        useResolvedIdentifier("snapshots", "/first/second")

        // then: count should properly respect criteria of uniqueness (type & identifier)
        assertThat(statisticsFacade.countRecords()).isEqualTo(4)
        assertThat(statisticsFacade.countUniqueRecords()).isEqualTo(3)
    }

    @Test
    fun `should return only the requested number of the highest entries`() {
        // given: three entries matching the phrase, with counts 5, 3 and 1
        useResolvedIdentifier("releases", "com/example/a", 5)
        useResolvedIdentifier("releases", "com/example/b", 3)
        useResolvedIdentifier("releases", "com/example/c", 1)

        // when: two entries are requested
        val result = statisticsFacade.findResolvedRequestsByPhrase("releases", "com/example", 2)

        // then: the two highest entries are returned and the sum covers only them
        val response = assertOk(result)
        assertThat(response.requests.map { it.gav })
            .describedAs("the limit of two must return the two highest entries, highest first")
            .containsExactly("com/example/a", "com/example/b")
        assertThat(response.sum)
            .describedAs("the sum must be the sum of the returned entries only")
            .isEqualTo(8)
    }

    @Test
    fun `should refuse a limit of zero`() {
        // given: an entry matching the phrase
        useResolvedIdentifier("releases", "com/example/a")

        // when: a limit of zero is requested
        val result = statisticsFacade.findResolvedRequestsByPhrase("releases", "com/example", 0)

        // then: the request is refused instead of answered with an empty list
        assertError(result)
    }

    @Test
    fun `should refuse a limit above the maximum page size`() {
        // given: an entry matching the phrase
        useResolvedIdentifier("releases", "com/example/a")

        // when: a limit above the maximum page size is requested
        val result = statisticsFacade.findResolvedRequestsByPhrase("releases", "com/example", 101)

        // then: the request is refused
        assertError(result)
    }

    @Test
    fun `should return only entries under paths the token may read`() {
        // given: a token that may read com/example only, and a higher entry outside of it
        val reader = useReadToken("reader", "/releases/com/example")
        useResolvedIdentifier("releases", "com/example/a", 5)
        useResolvedIdentifier("releases", "x/com/example/b", 9)

        // when: the phrase is requested with a generous limit
        val result = statisticsFacade.findResolvedRequestsByPhrase("releases", "com/example", 10, reader)

        // then: only the readable entry is returned
        val response = assertOk(result)
        assertThat(response.requests.map { it.gav })
            .describedAs("an entry outside the readable path must not be returned to the token")
            .containsExactly("com/example/a")
        assertThat(response.sum).describedAs("the sum must not count unreadable entries").isEqualTo(5)
    }

    @Test
    fun `should apply the limit after the visibility filter`() {
        // given: a token that may read com/example only, and an unreadable entry ranked above the readable one
        val reader = useReadToken("reader", "/releases/com/example")
        useResolvedIdentifier("releases", "x/com/example/b", 9)
        useResolvedIdentifier("releases", "com/example/a", 5)

        // when: a single entry is requested
        val result = statisticsFacade.findResolvedRequestsByPhrase("releases", "com/example", 1, reader)

        // then: the readable entry is returned instead of an empty list
        val response = assertOk(result)
        assertThat(response.requests.map { it.gav })
            .describedAs("the readable entry must fill the limit, the unreadable one must not take the slot")
            .containsExactly("com/example/a")
    }

    @Test
    fun `should show every matching entry to a manager`() {
        // given: a manager token and the same entries as for a reader
        val manager = accessTokenFacade.createAccessToken(CreateAccessTokenRequest(TEMPORARY, "manager")).accessToken.identifier
        accessTokenFacade.addPermission(manager, MANAGER)
        useResolvedIdentifier("releases", "com/example/a", 5)
        useResolvedIdentifier("releases", "x/com/example/b", 9)

        // when: the phrase is requested by the manager
        val result = statisticsFacade.findResolvedRequestsByPhrase("releases", "com/example", 10, manager)

        // then: both entries are returned
        val response = assertOk(result)
        assertThat(response.requests.map { it.gav })
            .describedAs("a manager must see every matching entry")
            .containsExactly("x/com/example/b", "com/example/a")
        assertThat(response.sum).describedAs("the sum must cover every matching entry").isEqualTo(14)
    }

}
