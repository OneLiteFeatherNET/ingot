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

@file:Suppress("FunctionName")

package com.reposilite.web

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The dashboard answers its path-based routes with index.html. A repository whose id equals the first
 * segment of such a route keeps answering every non-browser request, and only a top-level browser navigation
 * shows the dashboard there. The repository "settings" is configured on purpose: it collides with the
 * dashboard route, so the tests below pin down which side answers which request.
 */
internal abstract class DashboardRoutesSpecification : ReposiliteSpecification() {

    protected val dashboardTitle = "<title>Ingot Repository</title>"

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { old ->
            old.copy(repositories = old.repositories + RepositorySettings(id = "settings"))
        }
    }

    /** Sends a GET, with the fetch metadata of a top-level browser navigation when [navigation] is set. */
    protected fun fetch(path: String, navigation: Boolean = false): HttpResponse<String> {
        val request = Unirest.get("$base$path")
        if (navigation) {
            request.header("Sec-Fetch-Mode", "navigate").header("Sec-Fetch-Dest", "document")
        }
        return request.asString()
    }

    protected fun assertDashboardResponse(path: String, navigation: Boolean = false) {
        val response = fetch(path, navigation)

        assertThat(response.status).describedAs("status of GET $path (navigation=$navigation)").isEqualTo(OK.code)
        assertThat(response.body).describedAs("body of GET $path (navigation=$navigation)").contains(dashboardTitle)
        assertThat(response.body).describedAs("placeholders left unresolved in GET $path").doesNotContain("{{REPOSILITE.")
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class DashboardRoutesIntegrationTest : DashboardRoutesSpecification() {

    @Test
    fun `should serve the dashboard with resolved placeholders on app routes without a repository of the same id`() {
        listOf(
            "/browse",
            "/browse/releases/gav",
            "/dashboard",
            "/tokens",
            "/tokens/manager",
            "/console",
        ).forEach { path ->
            // when / then: no repository is called after the first segment, so each app route answers with the dashboard
            assertDashboardResponse(path)
        }
    }

    @Test
    fun `should answer head requests on app routes without a repository of the same id like get without a body`() {
        listOf("/browse/releases/gav", "/dashboard", "/tokens/manager", "/console").forEach { path ->
            // when: the head request reaches an app route that no repository claims
            val response = Unirest.head("$base$path").asEmpty()

            // then: the dashboard answers with the same status and content type as GET
            assertThat(response.status).describedAs("status of HEAD $path").isEqualTo(OK.code)
            assertThat(response.headers.getFirst("Content-Type")).describedAs("content type of HEAD $path").startsWith("text/html")
        }
    }

    @Test
    fun `should answer a top-level navigation on a colliding app route with the dashboard`() {
        listOf("/settings", "/settings/maven/repositories/releases").forEach { path ->
            // when / then: a browser navigating to the path gets the dashboard, although a repository has the same id
            assertDashboardResponse(path, navigation = true)
        }
    }

    @Test
    fun `should answer a get without fetch metadata on a colliding path with the artifact from maven`() {
        // given: an artifact deployed to the repository "settings", which shadows the dashboard route
        val (repository, gav, file, content) = useDocument("settings", "gav/1.0", "a.jar", "artifact-content", store = true)

        // when: a client without fetch metadata requests the artifact
        val response = fetch("/$repository/$gav/$file")

        // then: Maven serves the file, exactly as a Maven or Gradle client expects
        assertThat(response.status).describedAs("status of artifact GET").isEqualTo(OK.code)
        assertThat(response.body).describedAs("artifact content").isEqualTo(content)
    }

    @Test
    fun `should list the directory of a colliding repository from maven for a get without fetch metadata`() {
        // given: an artifact deployed to the repository "settings"
        val (repository, gav, file) = useDocument("settings", "gav/1.0", "a.jar", "artifact-content", store = true)

        // when: the directory is requested by a client without fetch metadata
        val response = fetch("/$repository/$gav")

        // then: the Maven directory index lists the file, and the dashboard is not served
        assertThat(response.body).describedAs("directory index of $file").contains("a.jar")
        assertThat(response.body).describedAs("directory index is not the dashboard").doesNotContain(dashboardTitle)
    }

    @Test
    fun `should answer a head request on a colliding path from maven with the content type of get`() {
        // given: an artifact deployed to the repository "settings"
        val (repository, gav, file) = useDocument("settings", "gav/1.0", "a.jar", "artifact-content", store = true)
        val path = "/$repository/$gav/$file"

        // when: the same file is requested with GET and with HEAD
        val getContentType = fetch(path).headers.getFirst("Content-Type")
        val headResponse = Unirest.head("$base$path").asEmpty()

        // then: HEAD is answered by Maven, not by the dashboard, with the content type of GET
        assertThat(headResponse.status).describedAs("status of HEAD on colliding artifact").isEqualTo(OK.code)
        assertThat(headResponse.headers.getFirst("Content-Type"))
            .describedAs("content type of HEAD on colliding artifact")
            .isEqualTo(getContentType)
            .doesNotStartWith("text/html")
    }

    @Test
    fun `should answer a top-level navigation on a colliding artifact path with the dashboard`() {
        // given: an artifact deployed to the repository "settings", which shadows the dashboard route
        val (repository, gav, file) = useDocument("settings", "gav/1.0", "a.jar", "artifact-content", store = true)

        // when / then: a browser navigating to the reserved path gets the dashboard, and the contents stay under /browse
        assertDashboardResponse("/$repository/$gav/$file", navigation = true)
    }

    @Test
    fun `should answer a bare colliding path without fetch metadata with not found as upstream does`() {
        // when: a client requests the bare path, which no Maven route matches, without navigating to it
        val response = fetch("/settings")

        // then: nothing answers with the dashboard, and the server reports the path as missing
        assertThat(response.status).describedAs("status of bare colliding path").isEqualTo(NOT_FOUND.code)
        assertThat(response.body).describedAs("bare colliding path is not the dashboard").doesNotContain(dashboardTitle)
    }

    @Test
    fun `should follow a repository that is added while the server runs`() {
        // given: no repository "dashboard" exists, so the app route answers a plain GET with the dashboard
        assertDashboardResponse("/dashboard")

        // when: the repository is added through the shared configuration at runtime
        useFacade<SharedConfigurationFacade>().getDomainSettings<MavenSettings>().update { old ->
            old.copy(repositories = old.repositories + RepositorySettings(id = "dashboard"))
        }

        // then: the next plain GET is answered by the repository, without a restart
        val plain = fetch("/dashboard")
        assertThat(plain.status).describedAs("status of plain GET after the repository was added").isEqualTo(NOT_FOUND.code)
        assertThat(plain.body).describedAs("plain GET is no longer the dashboard").doesNotContain(dashboardTitle)

        // and a top-level navigation still gets the dashboard
        assertDashboardResponse("/dashboard", navigation = true)
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class DetachedDashboardRoutesIntegrationTest : DashboardRoutesSpecification() {

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        localConfiguration.defaultFrontend.update { false }
    }

    @Test
    fun `should leave reserved paths to maven when the dashboard is not served by this instance`() {
        // given: an artifact deployed to the repository "settings"
        val (repository, gav, file, content) = useDocument("settings", "gav/1.0", "a.jar", "artifact-content", store = true)

        // when: the artifact is requested through its reserved path
        val response = Unirest.get("$base/$repository/$gav/$file").asString()

        // then: Maven answers, so a separately deployed dashboard can own the same paths without a rebuild
        assertThat(response.status).describedAs("status of artifact GET").isEqualTo(OK.code)
        assertThat(response.body).describedAs("artifact content").isEqualTo(content)
    }

    @Test
    fun `should not serve the dashboard on app routes when it is disabled`() {
        // when: an app route is requested on an instance without the bundled dashboard
        val response = Unirest.get("$base/settings/maven/repositories/releases").asString()

        // then: nothing claims the route and the server answers 404
        assertThat(response.status).describedAs("status of GET on disabled app route").isEqualTo(NOT_FOUND.code)
    }

}
