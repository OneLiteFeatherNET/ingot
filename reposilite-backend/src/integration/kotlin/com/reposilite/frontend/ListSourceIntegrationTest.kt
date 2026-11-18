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
import com.reposilite.frontend.api.PluginUiBlock
import com.reposilite.frontend.api.PluginUiFacade
import com.reposilite.frontend.api.PluginUiPage
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.RoutingSetupEvent as RoutingEvent
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.javalin.community.routing.Route.GET as READ

/**
 * A list page over HTTP: the manifest carries the list block with its source and page size, and the source answers
 * its items and the next cursor for the query the dashboard sends. Each test runs on a fresh instance.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ListSourceIntegrationTest : ReposiliteSpecification() {

    @Plugin(name = "example")
    private class ExamplePlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    private val source = "/api/plugins/example/items"

    private fun machine(name: String): String {
        val (manager, secret) = useDefaultManagementToken()
        val identity = "$base/api/identity"
        post("$identity/principals").basicAuth(manager, secret).body(mapOf("kind" to "MACHINE", "name" to name)).asString()
        val nodes = get("$identity/principals/$name/nodes").basicAuth(manager, secret).asString().headers.getFirst("ETag")
        kong.unirest.core.Unirest.put("$identity/principals/$name/nodes")
            .basicAuth(manager, secret)
            .header("If-Match", nodes)
            .body(mapOf("nodes" to listOf(mapOf("node" to "ingot.admin.maintenance", "allowed" to true))))
            .asString()
        val token = post("$identity/principals/$name/tokens").basicAuth(manager, secret).body(mapOf("name" to "$name-token")).asString()
        return "Bearer " + ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(token.body)["secret"].asText()
    }

    @Test
    fun `should answer the list block with its source and page size in the manifest`() {
        // given: a page with a list of the plugin, and a principal that may read the page
        useFacade<PluginUiFacade>().forPlugin(ExamplePlugin()).registerPage(
            PluginUiPage(id = "catalogue", title = "Catalogue", area = "maintenance", blocks = listOf(PluginUiBlock.ListBlock(source, pageSize = 20))),
        )
        val bearer = machine("catalogue-bot")

        // when: the manifest is requested
        val manifest = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(
            get("$base/api/frontend/plugin-ui").header("Authorization", bearer).asString().body,
        )
        val block = manifest["pages"].first { it["id"].asText() == "catalogue" }["blocks"].first()

        // then: the block is a list with its source and page size
        assertThat(block["type"].asText()).describedAs("type").isEqualTo("list")
        assertThat(block["source"].asText()).describedAs("source").isEqualTo(source)
        assertThat(block["pageSize"].asInt()).describedAs("page size").isEqualTo(20)
    }

    @Test
    fun `should answer the items and the next cursor for a query with a cursor`() {
        // given: a principal that may read the route of the list
        val bearer = machine("reader-bot")

        // when: the source is called with the cursor the dashboard sends for the second page
        val response = get("$base$source?cursor=page-2").header("Authorization", bearer).asString()

        // then: the items of that page and the cursor of the page after it are answered
        assertThat(response.status).describedAs("status").isEqualTo(200)
        val body = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(response.body)
        assertThat(body["items"].map { it["title"].asText() }).describedAs("titles").containsExactly("second")
        assertThat(body.path("next").isTextual).describedAs("a cursor after the last page").isFalse()
    }

    override fun beforeLaunch(reposilite: Reposilite) {
        reposilite.extensions.registerEvent { event: RoutingEvent ->
            event.register(
                ReposiliteRoute<Unit>("/api/plugins/example/items", READ) {
                    ctx.json(
                        if (ctx.queryParam("cursor") == "page-2") {
                            mapOf("items" to listOf(mapOf("title" to "second")), "next" to null)
                        } else {
                            mapOf("items" to listOf(mapOf("title" to "first")), "next" to "page-2")
                        },
                    )
                },
            )
        }
    }
}
