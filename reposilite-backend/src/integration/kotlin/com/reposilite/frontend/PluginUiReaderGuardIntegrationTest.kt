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
import com.reposilite.frontend.api.PluginUiPage
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.RoutingSetupEvent as RoutingEvent
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.head
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.plugin.ui.PluginUiReaderPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.javalin.community.routing.Route.GET as READ
import io.javalin.community.routing.Route.HEAD
import io.javalin.community.routing.Route.POST as WRITE

/**
 * The guard of the reader sources, over HTTP. A declared source answers an anonymous GET and HEAD, and every other request
 * to a plugin's routes keeps the rules of its admin pages.
 */
@OptIn(ExperimentalIngotPluginApi::class)
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class PluginUiReaderGuardIntegrationTest : ReposiliteSpecification() {

    @Plugin(name = "example")
    private class ExamplePlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Plugin(name = "bare")
    private class BarePlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    private val readers = "/api/plugins/example/readers"

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

    private fun readersPage(source: String) = PluginUiReaderPage(
        id = "readers",
        title = "Readers",
        blocks = listOf(PluginUiBlock.KeyValue(source)),
    )

    /** The example plugin has an admin page, so every route of it that is not a source needs the audit area. */
    private fun registerExample() {
        pluginUi.forPlugin(ExamplePlugin()).registerPage(
            PluginUiPage(id = "secret-audit", title = "Audit", area = "audit", blocks = listOf(PluginUiBlock.Text("Hello")))
        )
        pluginUi.forPlugin(ExamplePlugin()).registerReaderPage(readersPage(readers))
    }

    @Test
    fun `should answer a GET of a declared source to an anonymous caller`() {
        // given: a plugin with a reader source
        registerExample()

        // when: the source is requested anonymously
        val response = get("$base$readers").asString()

        // then: the handler answers
        assertThat(response.status).describedAs("status").isEqualTo(200)
        assertThat(response.body).describedAs("body").isEqualTo("readers-handled")
    }

    @Test
    fun `should answer a HEAD of a declared source to an anonymous caller`() {
        // given: a plugin with a reader source
        registerExample()

        // when: the source is requested with HEAD anonymously
        val response = head("$base$readers").asString()

        // then: the answer is 200
        assertThat(response.status).describedAs("status").isEqualTo(200)
    }

    @Test
    fun `should refuse a POST to a declared source to an anonymous caller`() {
        // given: a plugin with a reader source and a POST route at the same address
        registerExample()

        // when: the address is posted to anonymously
        val response = post("$base$readers").asString()

        // then: the caller is asked to sign in and the handler did not run
        assertThat(response.status).describedAs("status").isEqualTo(401)
        assertThat(response.body).describedAs("body").doesNotContain("posted")
    }

    @Test
    fun `should refuse a POST to a declared source of a plugin without an admin page to an anonymous caller`() {
        // given: a plugin with a reader page only, and a POST route at its source
        pluginUi.forPlugin(BarePlugin()).registerReaderPage(readersPage("/api/plugins/bare/readers"))

        // when: the source is posted to anonymously
        val response = post("$base/api/plugins/bare/readers").asString()

        // then: the caller is asked to sign in and the handler did not run
        assertThat(response.status).describedAs("status").isEqualTo(401)
        assertThat(response.body).describedAs("body").doesNotContain("bare-posted")
    }

    @Test
    fun `should not exempt a path with a double slash`() {
        // given: a plugin with a reader source
        registerExample()

        // when: a path with an empty segment is requested anonymously
        val response = get("$base/api/plugins/example//readers").asString()

        // then: the admin rule applies and the caller is asked to sign in
        assertThat(response.status).describedAs("status").isEqualTo(401)
    }

    @Test
    fun `should not exempt a path with a trailing slash`() {
        // given: a plugin with a reader source
        registerExample()

        // when: the source is requested with a trailing slash anonymously
        val response = get("$base$readers/").asString()

        // then: the admin rule applies and the caller is asked to sign in
        assertThat(response.status).describedAs("status").isEqualTo(401)
        assertThat(response.body).describedAs("body").doesNotContain("readers-handled")
    }

    @Test
    fun `should keep the admin floor for a route that is not a source`() {
        // given: a plugin with an admin page and a route that is not a source
        registerExample()

        // when: the route is requested by a signed in caller without the area
        val response = get("$base/api/plugins/example/anything").header("Authorization", machine("users-bot", listOf("ingot.admin.users"))).asString()

        // then: the answer is 403 and the handler did not run
        assertThat(response.status).describedAs("status").isEqualTo(403)
        assertThat(response.body).describedAs("body").doesNotContain("handled")
    }

    @Test
    fun `should answer a source to a signed in caller without the area`() {
        // given: a plugin with a reader source and a signed in caller without the audit area
        registerExample()

        // when: the source is requested with the bearer of that caller
        val response = get("$base$readers").header("Authorization", machine("users-bot", listOf("ingot.admin.users"))).asString()

        // then: the source answers
        assertThat(response.status).describedAs("status").isEqualTo(200)
    }

    @Test
    fun `should answer a source with the headers of a per viewer answer`() {
        // given: a plugin with a reader source
        registerExample()

        // when: the source is requested anonymously
        val response = get("$base$readers").asString()

        // then: the answer may not be shared between viewers
        assertThat(response.headers.getFirst("Vary")).describedAs("Vary").isEqualTo("Cookie, Authorization")
        assertThat(response.headers.getFirst("Cache-Control")).describedAs("Cache-Control").isEqualTo("private, no-cache")
    }

    @Test
    fun `should answer 404 to a source while the readers switch is off without running the handler`() {
        // given: a plugin with a reader source, and the readers switch turned off on the running instance
        registerExample()
        reposilite.extensions.facade<LocalConfiguration>().pluginReaders.update { false }

        // when: the source is requested anonymously
        val response = get("$base$readers").asString()

        // then: the answer is 404 and the handler did not run
        assertThat(response.status).describedAs("status").isEqualTo(404)
        assertThat(response.body).describedAs("body").doesNotContain("readers-handled")
    }

    override fun beforeLaunch(reposilite: Reposilite) {
        reposilite.extensions.registerEvent { event: RoutingEvent ->
            event.register(ReposiliteRoute<Unit>("/api/plugins/example/readers", READ, HEAD) { ctx.result("readers-handled") })
            event.register(ReposiliteRoute<Unit>("/api/plugins/example/readers", WRITE) { ctx.result("posted") })
            event.register(ReposiliteRoute<Unit>("/api/plugins/example/anything", READ) { ctx.result("handled") })
            event.register(ReposiliteRoute<Unit>("/api/plugins/bare/readers", READ) { ctx.result("bare-readers") })
            event.register(ReposiliteRoute<Unit>("/api/plugins/bare/readers", WRITE) { ctx.result("bare-posted") })
        }
    }

}
