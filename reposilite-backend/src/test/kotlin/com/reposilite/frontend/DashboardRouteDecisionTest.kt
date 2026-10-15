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

package com.reposilite.frontend

import com.reposilite.frontend.DashboardRouteTarget.DASHBOARD
import com.reposilite.frontend.DashboardRouteTarget.MAVEN
import io.javalin.http.HandlerType.GET
import io.javalin.http.HandlerType.HEAD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class DashboardRouteDecisionTest {

    @Test
    fun `should answer get with the dashboard when no repository has the id of the path`() {
        // when: a GET arrives on an app route without a repository of the same id
        val target = DashboardRouteDecision.decide(repositoryExists = false, method = GET, secFetchMode = null, secFetchDest = null)

        // then: the dashboard answers, as it always did
        assertThat(target).describedAs("target of GET without repository").isEqualTo(DASHBOARD)
    }

    @Test
    fun `should answer head with the dashboard when no repository has the id of the path`() {
        // when: a HEAD arrives on an app route without a repository of the same id
        val target = DashboardRouteDecision.decide(repositoryExists = false, method = HEAD, secFetchMode = null, secFetchDest = null)

        // then: the dashboard answers, with the same status as GET
        assertThat(target).describedAs("target of HEAD without repository").isEqualTo(DASHBOARD)
    }

    @Test
    fun `should answer get with the dashboard without a repository whatever the fetch metadata says`() {
        // when: a GET arrives on an app route without a repository, and carries fetch metadata of a script
        val target = DashboardRouteDecision.decide(repositoryExists = false, method = GET, secFetchMode = "cors", secFetchDest = "empty")

        // then: the dashboard still answers, because nothing else claims the path
        assertThat(target).describedAs("target of GET without repository and with fetch metadata").isEqualTo(DASHBOARD)
    }

    @Test
    fun `should answer get without fetch metadata with maven when the repository exists`() {
        // when: a non-browser client sends a GET to a colliding path
        val target = DashboardRouteDecision.decide(repositoryExists = true, method = GET, secFetchMode = null, secFetchDest = null)

        // then: the repository answers, as it does upstream
        assertThat(target).describedAs("target of GET from a client without fetch metadata").isEqualTo(MAVEN)
    }

    @Test
    fun `should answer head with maven when the repository exists even for a top-level navigation`() {
        // when: a HEAD arrives on a colliding path, carrying the headers of a navigation
        val target = DashboardRouteDecision.decide(repositoryExists = true, method = HEAD, secFetchMode = "navigate", secFetchDest = "document")

        // then: HEAD always belongs to the repository, so deploy tools and link checkers see the artifact
        assertThat(target).describedAs("target of HEAD on a colliding path").isEqualTo(MAVEN)
    }

    @Test
    fun `should answer a top-level navigation with the dashboard when the repository exists`() {
        // when: a browser navigates to a colliding path
        val target = DashboardRouteDecision.decide(repositoryExists = true, method = GET, secFetchMode = "navigate", secFetchDest = "document")

        // then: the dashboard answers, so users reach the UI from the address bar
        assertThat(target).describedAs("target of top-level navigation").isEqualTo(DASHBOARD)
    }

    @ParameterizedTest(name = "mode={0}, dest={1}")
    @CsvSource(
        "navigate, iframe",
        "navigate, empty",
        "cors, empty",
        "no-cors, document",
        "same-origin, document",
        "navigate, ''",
    )
    fun `should answer a request that is not a top-level navigation with maven when the repository exists`(mode: String, dest: String) {
        // when: a GET arrives with fetch metadata that is not a top-level navigation
        val target = DashboardRouteDecision.decide(
            repositoryExists = true,
            method = GET,
            secFetchMode = mode,
            secFetchDest = dest.takeUnless { it.isEmpty() },
        )

        // then: the repository answers, because only a document navigation may show the dashboard
        assertThat(target).describedAs("target of GET with mode=$mode dest=$dest").isEqualTo(MAVEN)
    }

}
