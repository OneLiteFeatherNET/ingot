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

import com.reposilite.ReposiliteObjectMapper
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.Reposilite
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.frontend.api.PluginUiBlock
import com.reposilite.frontend.api.PluginUiFacade
import com.reposilite.frontend.api.PluginUiPage
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.RoutingSetupEvent as RoutingEvent
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.javalin.community.routing.Route.GET as READ

/**
 * The manifest of the dashboard pages and the guard of the data routes, over HTTP. The pages are registered on a running
 * instance by a fixture plugin, and every caller is a principal with the admin nodes it needs.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class PluginUiIntegrationTest : ReposiliteSpecification() {

    @Plugin(name = "example")
    private class ExamplePlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    private val manifestPath = "/api/frontend/plugin-ui"

    private val pluginUi: PluginUiFacade
        get() = useFacade<PluginUiFacade>()

    private fun identity(path: String): String = "$base/api/identity$path"

    /** A machine principal with the given admin nodes, and the bearer header of its token. */
    private fun machine(name: String, nodes: List<String>): String {
        val (manager, secret) = useDefaultManagementToken()
        post(identity("/principals")).basicAuth(manager, secret).body(mapOf("kind" to "MACHINE", "name" to name)).asString()
        if (nodes.isNotEmpty()) {
            val etag = get(identity("/principals/$name/nodes")).basicAuth(manager, secret).asString().headers.getFirst("ETag")
            put(identity("/principals/$name/nodes"))
                .basicAuth(manager, secret)
                .header("If-Match", etag)
                .body(mapOf("nodes" to nodes.map { mapOf("node" to it, "allowed" to true) }))
                .asString()
        }
        val token = post(identity("/principals/$name/tokens"))
            .basicAuth(manager, secret)
            .body(mapOf("name" to "$name-token"))
            .asString()
        return "Bearer " + ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(token.body)["secret"].asText()
    }

    private fun manifest(bearer: String? = null): HttpResponse<String> =
        get("$base$manifestPath").also { request -> bearer?.let { request.header("Authorization", it) } }.asString()

    private fun pageIds(response: HttpResponse<String>): List<String> =
        ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(response.body)["pages"].map { it["id"].asText() }

    private fun maintenancePage(id: String, area: String = "maintenance") =
        PluginUiPage(id = id, title = "Status $id", area = area, blocks = listOf(PluginUiBlock.Text("Hello")))

    @Test
    fun `should answer the manifest with 401 to an anonymous caller`() {
        // when: the manifest is requested without credentials
        val response = manifest()

        // then: it is refused
        assertThat(response.status).describedAs("status").isEqualTo(401)
    }

    @Test
    fun `should give a caller with the area the page and version 1`() {
        // given: a page that needs the maintenance area, and a principal that holds it
        pluginUi.forPlugin(ExamplePlugin()).registerPage(maintenancePage("status"))
        val bearer = machine("maintenance-bot", listOf("ingot.admin.maintenance"))

        // when: the manifest is requested
        val response = manifest(bearer)

        // then: the page is in it, and the version is 1
        assertThat(response.status).describedAs("status").isEqualTo(200)
        assertThat(pageIds(response)).describedAs("page ids").containsExactly("status")
        assertThat(ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(response.body)["version"].asInt()).isEqualTo(1)
    }

    @Test
    fun `should leave out the page and its name for a caller without the area`() {
        // given: a page that needs the audit area, and a principal that holds only users
        pluginUi.forPlugin(ExamplePlugin()).registerPage(maintenancePage("secret-audit", area = "audit"))
        val bearer = machine("users-bot", listOf("ingot.admin.users"))

        // when: the manifest is requested
        val response = manifest(bearer)

        // then: neither the page nor its id is in the answer
        assertThat(response.status).describedAs("status").isEqualTo(200)
        assertThat(response.body).describedAs("body").doesNotContain("secret-audit")
    }

    @Test
    fun `should report the enabled plugins to a signed in caller`() {
        // given: a principal without any admin node
        val bearer = machine("plain-bot", emptyList())

        // when: the manifest is requested
        val enabled = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(manifest(bearer).body)["enabledPlugins"].map { it.asText() }

        // then: the loaded plugins are named and a plugin that is not loaded is not
        assertThat(enabled).describedAs("enabled plugins").contains("frontend", "plugin-ui").doesNotContain("nexus-import")
    }

    @Test
    fun `should serve no page while the switch is off and still report the plugins`() {
        // given: a page the caller may open, and the switch turned off on the running instance
        pluginUi.forPlugin(ExamplePlugin()).registerPage(maintenancePage("status"))
        val bearer = machine("maintenance-bot", listOf("ingot.admin.maintenance"))
        reposilite.extensions.facade<LocalConfiguration>().pluginUi.update { false }

        // when: the manifest is requested
        val response = manifest(bearer)

        // then: no page is served and the enabled plugins are still named
        assertThat(pageIds(response)).describedAs("page ids").isEmpty()
        assertThat(ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(response.body)["enabledPlugins"].map { it.asText() })
            .describedAs("enabled plugins").contains("plugin-ui")
    }

    @Test
    fun `should refuse an unguarded data route of a plugin with a page to a caller without the area`() {
        // given: a route of the plugin that forgot its own check, and a page that needs the audit area
        pluginUi.forPlugin(ExamplePlugin()).registerPage(maintenancePage("secret-audit", area = "audit"))
        val bearer = machine("users-bot", listOf("ingot.admin.users"))

        // when: the route is called by a caller without the area
        val response = get("$base/api/plugins/example/anything").header("Authorization", bearer).asString()

        // then: the answer is 403 and the handler did not run
        assertThat(response.status).describedAs("status").isEqualTo(403)
        assertThat(response.body).describedAs("body").doesNotContain("handled")
    }

    @Test
    fun `should leave the routes of a plugin without a page as they were`() {
        // when: a route of a plugin without a page is called without credentials
        val response = get("$base/api/plugins/other/status").asString()

        // then: it is handled as before, which for this fixture is an answer of 200
        assertThat(response.status).describedAs("status").isEqualTo(200)
    }

    @Test
    fun `should not guard a plugin whose name only starts with the name of a guarded one`() {
        // when: a route of a plugin named example-other is called without credentials
        val response = get("$base/api/plugins/example-other/status").asString()

        // then: the guard of example does not apply to it
        assertThat(response.status).describedAs("status").isEqualTo(200)
    }

    override fun beforeLaunch(reposilite: Reposilite) {
        reposilite.extensions.registerEvent { event: RoutingEvent ->
            event.register(
                ReposiliteRoute<Unit>("/api/plugins/example/anything", READ) { ctx.result("handled") },
            )
            event.register(
                ReposiliteRoute<Unit>("/api/plugins/other/status", READ) { ctx.result("other") },
            )
            event.register(
                ReposiliteRoute<Unit>("/api/plugins/example-other/status", READ) { ctx.result("look-alike") },
            )
        }
    }

}
