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

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DashboardPathsTest {

    @Test
    fun `should cover exactly the segments of the shared dashboard segment list`() {
        // given: the segment list that the dashboard router, the dashboard server and the chart read
        val shared = readSharedSegments()

        // when: the first segment of every dashboard route is read
        val routeSegments = DashboardPaths.APP_ROUTES.map { it.removePrefix("/").substringBefore('/') }.toSet()

        // then: the route table covers the shared list, so a page added without its segment fails here
        assertThat(routeSegments)
            .describedAs("dashboard route segments that differ from reposilite-frontend/src/router/dashboard-segments.json")
            .isEqualTo(shared)
    }

    private fun readSharedSegments(): Set<String> {
        // Gradle runs unit tests in the Gradle project directory, one level below the repository root.
        val file = Path.of("..", "reposilite-frontend", "src", "router", "dashboard-segments.json").toAbsolutePath().normalize()
        assertThat(Files.exists(file))
            .describedAs("shared segment list at $file; the test expects the Gradle project to sit one level below the repository root")
            .isTrue()
        return SEGMENT.findAll(Files.readString(file)).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `should reserve the first segment of every dashboard route and the server segments`() {
        // when: the reserved segments are read
        val reserved = DashboardPaths.RESERVED_SEGMENTS

        // then: the dashboard routes and the API and asset segments are all covered
        assertThat(reserved).containsExactlyInAnyOrder("browse", "search", "dashboard", "analytics", "tokens", "console", "maintenance", "audit", "settings", "access", "account", "api", "assets")
    }

    @Test
    fun `should answer the access and account paths with the dashboard`() {
        // when: the dashboard route table is read
        val routes = DashboardPaths.APP_ROUTES

        // then: the access section, its sub-paths and the account page are dashboard routes
        assertThat(routes)
            .describedAs("dashboard routes for the access and account pages")
            .contains("/access", "/access/<path>", "/account")
    }

    @Test
    fun `should answer the search path with the dashboard`() {
        // when: the dashboard route table and the reserved segments are read
        val reserved = DashboardPaths.RESERVED_SEGMENTS
        val routes = DashboardPaths.APP_ROUTES

        // then: the search page is a dashboard route without sub-paths, and its segment is reserved
        assertThat(reserved).describedAs("reserved segments").contains("search")
        assertThat(routes.filter { it.startsWith("/search") }).describedAs("search routes").containsExactly("/search")
    }

    @Test
    fun `should answer the analytics path with the dashboard`() {
        // when: the dashboard route table and the reserved segments are read
        val reserved = DashboardPaths.RESERVED_SEGMENTS
        val routes = DashboardPaths.APP_ROUTES

        // then: the analytics page is a dashboard route without sub-paths, so a reload serves index.html
        assertThat(reserved).describedAs("reserved segments").contains("analytics")
        assertThat(routes.filter { it.startsWith("/analytics") }).describedAs("analytics routes").containsExactly("/analytics")
    }

    @Test
    fun `should flag repository ids that equal a reserved segment`() {
        // when: repositories with and without reserved ids are checked
        val flagged = DashboardPaths.reservedRepositoryWarnings(listOf("releases", "settings", "api", "assets", "snapshots"))

        // then: exactly the reserved ids are reported
        assertThat(flagged)
            .describedAs("warnings for reserved repository ids")
            .hasSize(3)
        assertThat(flagged[0]).contains("'settings'")
        assertThat(flagged[1]).contains("'api'")
        assertThat(flagged[2]).contains("'assets'")
    }

    @Test
    fun `should not flag repository ids that only start with a reserved segment`() {
        // when: repository ids that merely contain a reserved word are checked
        val flagged = DashboardPaths.reservedRepositoryWarnings(listOf("settings-archive", "my-api", "browser", "releases"))

        // then: none of them collide, because the browser routes match whole segments only
        assertThat(flagged).describedAs("warnings for ids that are not reserved").isEmpty()
    }

    @Test
    fun `should explain in the dashboard warning that maven clients keep the repository`() {
        // when: the warning for a repository that collides with a dashboard route is built
        val warning = DashboardPaths.reservedRepositoryWarnings(listOf("settings")).single()

        // then: it says that Maven clients keep the repository, that only a browser navigation shows the dashboard,
        // and where the contents are reachable in the UI
        assertThat(warning)
            .describedAs("warning text")
            .contains("'/settings/<gav>'")
            .contains("Maven")
            .contains("top-level browser navigation")
            .contains("'/browse/settings'")
            .contains("not rejected")
    }

    @Test
    fun `should warn about a repository named after the maintenance route like any other dashboard route`() {
        // when: a repository with the id of the maintenance route is checked
        val warning = DashboardPaths.reservedRepositoryWarnings(listOf("maintenance")).single()

        // then: the warning names the maintenance route and the browse path that reaches the repository from the dashboard
        assertThat(warning)
            .describedAs("warning text for the maintenance route")
            .contains("'/maintenance'")
            .contains("'/browse/maintenance'")
            .contains("top-level browser navigation")
    }

    @Test
    fun `should warn about a repository named after the audit route like any other dashboard route`() {
        // when: a repository with the id of the audit route is checked
        val warning = DashboardPaths.reservedRepositoryWarnings(listOf("audit")).single()

        // then: the warning names the audit route and the browse path that reaches the repository from the dashboard
        assertThat(warning)
            .describedAs("warning text for the audit route")
            .contains("'/audit'")
            .contains("'/browse/audit'")
            .contains("top-level browser navigation")
            .contains("not rejected")
    }

    @Test
    fun `should warn about a repository named after the access route like any other dashboard route`() {
        // when: a repository with the id of the access route is checked
        val warning = DashboardPaths.reservedRepositoryWarnings(listOf("access")).single()

        // then: the warning names the access route and the browse path that reaches the repository from the dashboard
        assertThat(warning)
            .describedAs("warning text for the access route")
            .contains("'/access'")
            .contains("'/browse/access'")
            .contains("top-level browser navigation")
            .contains("not rejected")
    }

    @Test
    fun `should warn about a repository named after the account route like any other dashboard route`() {
        // when: a repository with the id of the account route is checked
        val warning = DashboardPaths.reservedRepositoryWarnings(listOf("account")).single()

        // then: the warning names the account route and the browse path that reaches the repository from the dashboard
        assertThat(warning)
            .describedAs("warning text for the account route")
            .contains("'/account'")
            .contains("'/browse/account'")
            .contains("top-level browser navigation")
            .contains("not rejected")
    }

    @Test
    fun `should warn about a repository named after the search route like any other dashboard route`() {
        // when: a repository with the id of the search route is checked
        val warning = DashboardPaths.reservedRepositoryWarnings(listOf("search")).single()

        // then: the warning names the search route and the browse path that reaches the repository from the dashboard
        assertThat(warning)
            .describedAs("warning text for the search route")
            .contains("'/search'")
            .contains("'/browse/search'")
            .contains("top-level browser navigation")
            .contains("not rejected")
    }

    @Test
    fun `should keep the server segment warning for a repository that collides with the api`() {
        // when: the warning for a repository that collides with the API is built
        val warning = DashboardPaths.reservedRepositoryWarnings(listOf("api")).single()

        // then: the API keeps its own explanation, because the dashboard navigation rule does not apply to it
        assertThat(warning)
            .describedAs("warning text for the API segment")
            .contains("'/api/'")
            .contains("renaming the repository avoids this")
    }

    private companion object {
        // The list is a JSON array of plain names, so a pattern for quoted strings is enough and needs no parser.
        val SEGMENT = Regex("\"([a-z]+)\"")
    }

}
