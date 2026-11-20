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

package com.reposilite.frontend

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.Reposilite
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.frontend.api.PluginUiBlock
import com.reposilite.frontend.api.PluginUiFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.plugin.ui.PluginUiReaderPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The address of the reader pages, `/plugins/<plugin>/<page>`. The server claims it only while a reader page is registered
 * when the routes are set up, and then hands it over like any dashboard route: a repository named `plugins` keeps answering
 * every request that is not a top-level browser navigation. Each test boots its own instance. The start warning for such a
 * repository is covered by DashboardPathsTest with a captured logger.
 */
internal abstract class PluginReaderAddressSpecification : ReposiliteSpecification() {

    @Plugin(name = "example")
    protected class ExamplePlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { old ->
            old.copy(repositories = old.repositories + RepositorySettings(id = "plugins"))
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

}

@OptIn(ExperimentalIngotPluginApi::class)
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class PluginReaderAddressIntegrationTest : PluginReaderAddressSpecification() {

    override fun beforeLaunch(reposilite: Reposilite) {
        reposilite.extensions.facade<PluginUiFacade>()
            .forPlugin(ExamplePlugin())
            .registerReaderPage(
                PluginUiReaderPage(
                    id = "guides",
                    title = "Guides",
                    blocks = listOf(PluginUiBlock.KeyValue("/api/plugins/example/guides")),
                )
            )
    }

    @Test
    fun `should answer a browser navigation to a reader page with the dashboard`() {
        // when: a browser opens the address of the reader page
        val response = fetch("/plugins/example/guides", navigation = true)

        // then: the dashboard document answers
        assertThat(response.status).describedAs("status of the reader page navigation").isEqualTo(OK.code)
        assertThat(response.headers.getFirst("Content-Type")).describedAs("content type of the reader page").startsWith("text/html")
    }

    @Test
    fun `should keep serving the artifacts of a repository named plugins to a maven client`() {
        // given: an artifact in the repository that shares the segment with the reader address
        val (repository, gav, file, content) = useDocument("plugins", "gav/1.0", "a.jar", "artifact-content", store = true)

        // when: a client without fetch metadata requests the artifact
        val response = fetch("/$repository/$gav/$file")

        // then: the repository answers, not the dashboard
        assertThat(response.status).describedAs("status of the artifact GET").isEqualTo(OK.code)
        assertThat(response.body).describedAs("artifact content").isEqualTo(content)
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class PluginReaderAddressUnclaimedIntegrationTest : PluginReaderAddressSpecification() {

    @Test
    fun `should leave the reader address to the repository while no reader page exists`() {
        // when: a browser opens a path under /plugins on an instance without reader pages
        val response = fetch("/plugins/example/guides", navigation = true)

        // then: the dashboard does not claim it, and the repository answers that the file does not exist
        assertThat(response.status).describedAs("status of the unclaimed navigation").isEqualTo(NOT_FOUND.code)
    }

}
