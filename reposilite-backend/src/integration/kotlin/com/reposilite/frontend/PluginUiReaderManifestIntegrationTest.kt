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
import com.reposilite.ReposiliteObjectMapper
import com.reposilite.ReposiliteSpecification
import com.reposilite.Reposilite
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.frontend.api.PluginUiBlock
import com.reposilite.frontend.api.PluginUiFacade
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.RoutingSetupEvent as RoutingEvent
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.plugin.ui.PluginUiReaderPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.javalin.community.routing.Route.GET as READ

/**
 * The reader manifest of the dashboard, over HTTP. Anonymous and signed in callers get the same pages, and the reader
 * manifest carries no stylesheet and no list of plugins.
 */
@OptIn(ExperimentalIngotPluginApi::class)
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class PluginUiReaderManifestIntegrationTest : ReposiliteSpecification() {

    @Plugin(name = "example")
    private class ExamplePlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    private val readerManifestPath = "/api/frontend/plugin-ui/readers"

    private val pluginUi: PluginUiFacade
        get() = useFacade<PluginUiFacade>()

    private fun identity(path: String): String = "$base/api/identity$path"

    /** A machine principal without admin nodes, and the bearer header of its token. */
    private fun machine(name: String): String {
        val (manager, secret) = useDefaultManagementToken()
        post(identity("/principals")).basicAuth(manager, secret).body(mapOf("kind" to "MACHINE", "name" to name)).asString()
        val token = post(identity("/principals/$name/tokens"))
            .basicAuth(manager, secret)
            .body(mapOf("name" to "$name-token"))
            .asString()
        return "Bearer " + ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(token.body)["secret"].asText()
    }

    private fun readerManifest(bearer: String? = null): HttpResponse<String> =
        get("$base$readerManifestPath").also { request -> bearer?.let { request.header("Authorization", it) } }.asString()

    private fun json(response: HttpResponse<String>) = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(response.body)

    private fun releasesPage() = PluginUiReaderPage(
        id = "releases",
        title = "Releases",
        blocks = listOf(PluginUiBlock.KeyValue("/api/plugins/example/readers")),
    )

    @Test
    fun `should answer the reader manifest to an anonymous caller`() {
        // given: a reader page of the example plugin
        pluginUi.forPlugin(ExamplePlugin()).registerReaderPage(releasesPage())

        // when: the reader manifest is requested without credentials
        val response = readerManifest()

        // then: the page is listed with its plugin and the version is 1
        assertThat(response.status).describedAs("status").isEqualTo(200)
        assertThat(json(response)["version"].asInt()).describedAs("version").isEqualTo(1)
        assertThat(json(response)["pages"].map { it["id"].asText() }).describedAs("page ids").containsExactly("releases")
        assertThat(json(response)["pages"][0]["plugin"].asText()).describedAs("plugin").isEqualTo("example")
    }

    @Test
    fun `should give a signed in caller the same reader pages as an anonymous caller`() {
        // given: a reader page and a signed in machine without any admin node
        pluginUi.forPlugin(ExamplePlugin()).registerReaderPage(releasesPage())
        val bearer = machine("plain-bot")

        // when: the reader manifest is requested anonymously and signed in
        val anonymous = readerManifest()
        val signedIn = readerManifest(bearer)

        // then: both answers are the same
        assertThat(signedIn.status).describedAs("signed in status").isEqualTo(200)
        assertThat(json(signedIn)).describedAs("signed in body").isEqualTo(json(anonymous))
    }

    @Test
    fun `should leave out the stylesheets and the plugin names from the reader manifest`() {
        // given: a reader page
        pluginUi.forPlugin(ExamplePlugin()).registerReaderPage(releasesPage())

        // when: the reader manifest is requested by a signed in caller
        val body = json(readerManifest(machine("plain-bot")))

        // then: neither the stylesheets, nor the enabled plugins, nor the admin sections are in it
        assertThat(body.has("stylesheets")).describedAs("stylesheets").isFalse()
        assertThat(body.has("enabledPlugins")).describedAs("enabledPlugins").isFalse()
        assertThat(body.has("sections")).describedAs("sections").isFalse()
    }

    @Test
    fun `should keep the admin manifest closed to an anonymous caller`() {
        // given: a reader page of the example plugin
        pluginUi.forPlugin(ExamplePlugin()).registerReaderPage(releasesPage())

        // when: the admin manifest is requested without credentials
        val response = get("$base/api/frontend/plugin-ui").asString()

        // then: it still answers 401
        assertThat(response.status).describedAs("status").isEqualTo(401)
    }

    @Test
    fun `should serve no reader page while the readers switch is off`() {
        // given: a reader page, and the readers switch turned off on the running instance
        pluginUi.forPlugin(ExamplePlugin()).registerReaderPage(releasesPage())
        reposilite.extensions.facade<LocalConfiguration>().pluginReaders.update { false }

        // when: the reader manifest is requested anonymously
        val response = readerManifest()

        // then: the answer is an empty list of pages
        assertThat(response.status).describedAs("status").isEqualTo(200)
        assertThat(json(response)["pages"].size()).describedAs("page count").isZero()
    }

    override fun beforeLaunch(reposilite: Reposilite) {
        reposilite.extensions.registerEvent { event: RoutingEvent ->
            event.register(
                ReposiliteRoute<Unit>("/api/plugins/example/readers", READ) { ctx.result("readers-handled") },
            )
        }
    }

}
