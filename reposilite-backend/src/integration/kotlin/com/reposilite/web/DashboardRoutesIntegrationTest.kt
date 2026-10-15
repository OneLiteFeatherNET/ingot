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
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The dashboard answers its path-based routes with index.html, and everything else under the same
 * first segment still reaches Maven. The repository "settings" is configured on purpose: it collides
 * with the dashboard route, so these tests also pin down which side wins.
 */
internal abstract class DashboardRoutesSpecification : ReposiliteSpecification() {

    protected val dashboardTitle = "<title>Ingot Repository</title>"

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { old ->
            old.copy(repositories = old.repositories + RepositorySettings(id = "settings"))
        }
    }

    protected fun assertDashboardResponse(path: String) {
        val response = Unirest.get("$base$path").asString()

        assertThat(response.status).describedAs("status of GET $path").isEqualTo(OK.code)
        assertThat(response.body).describedAs("body of GET $path").contains(dashboardTitle)
        assertThat(response.body).describedAs("placeholders left unresolved in GET $path").doesNotContain("{{REPOSILITE.")
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class DashboardRoutesIntegrationTest : DashboardRoutesSpecification() {

    @Test
    fun `should serve the dashboard with resolved placeholders on every app route`() {
        listOf(
            "/browse",
            "/browse/releases/gav",
            "/dashboard",
            "/tokens",
            "/tokens/manager",
            "/console",
            "/settings",
            "/settings/maven/repositories/releases",
        ).forEach { path ->
            // when / then: each app route answers with the dashboard index
            assertDashboardResponse(path)
        }
    }

    @Test
    fun `should answer head requests on app routes like get without a body`() {
        // when: the head request reaches a path that would otherwise match a Maven repository route
        val response = Unirest.head("$base/settings/maven/repositories/releases").asEmpty()

        // then: it is answered by the dashboard, with the same status and content type as GET
        assertThat(response.status).describedAs("status of HEAD on app route").isEqualTo(OK.code)
        assertThat(response.headers.getFirst("Content-Type")).describedAs("content type of HEAD on app route").startsWith("text/html")
    }

    @Test
    fun `should keep serving artifacts of a regular repository from maven`() {
        // given: an artifact deployed to a repository that is not a dashboard route
        val (repository, gav, file, content) = useDocument("releases", "gav/1.0", "a.jar", "artifact-content", store = true)

        // when: the artifact is requested
        val response = Unirest.get("$base/$repository/$gav/$file").asString()

        // then: Maven serves the file itself, not the dashboard
        assertThat(response.status).describedAs("status of artifact GET").isEqualTo(OK.code)
        assertThat(response.body).describedAs("artifact content").isEqualTo(content)
    }

    @Test
    fun `should keep listing directories of a regular repository from maven`() {
        // given: an artifact deployed to a regular repository
        val (repository, gav) = useDocument("releases", "gav/1.0", "a.jar", "artifact-content", store = true)

        // when: the directory is requested
        val response = Unirest.get("$base/$repository/$gav").asString()

        // then: the Maven directory index lists the file, and the dashboard is not served
        assertThat(response.body).describedAs("directory index").contains("a.jar")
        assertThat(response.body).describedAs("directory index is not the dashboard").doesNotContain(dashboardTitle)
    }

    @Test
    fun `should answer a reserved path with the dashboard before a repository of the same name`() {
        // given: an artifact deployed to the repository "settings", which shadows the dashboard route
        val (repository, gav, file) = useDocument("settings", "gav/1.0", "a.jar", "artifact-content", store = true)

        // when: the artifact is requested through its reserved path
        // then: the dashboard wins, which is why the startup warning tells operators to rename the repository
        assertDashboardResponse("/$repository/$gav/$file")
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
