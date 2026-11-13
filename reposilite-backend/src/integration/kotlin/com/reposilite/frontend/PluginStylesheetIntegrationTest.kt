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
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.frontend.api.PluginUiBlock
import com.reposilite.frontend.api.PluginUiFacade
import com.reposilite.frontend.api.PluginUiPage
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The stylesheets of plugins, over HTTP: the manifest entry, the served bytes, their cache policy and the switch.
 * The sheet is a resource of this test module, and the expected bytes and digests are computed here, not by the code.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class PluginStylesheetIntegrationTest : ReposiliteSpecification() {

    @Plugin(name = "example")
    private class ExamplePlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    private val manifestPath = "/api/frontend/plugin-ui"

    private val sheetResource = "plugin-stylesheet/dashboard.css"

    private val sheetText = "[data-ingot-component=\"plugin-page\"] [data-ingot-part=\"header\"] { color: red; }\n"

    private val wrapped = "@layer ingot-plugins {\n$sheetText\n}\n"

    private val wrappedDigest: String = MessageDigest.getInstance("SHA-256").digest(wrapped.toByteArray(UTF_8))
        .joinToString(separator = "") { "%02x".format(it) }

    private val wrappedHash = wrappedDigest.take(16)

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

    private fun manifestJson(bearer: String) = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(manifest(bearer).body)

    private fun registerSheet() {
        pluginUi.forPlugin(ExamplePlugin()).registerStylesheet(sheetResource)
    }

    private fun sheetPath(): String = "/api/frontend/plugin-styles/example/$wrappedHash.css"

    @Test
    fun `should list the stylesheet of a plugin with its path and digest for a signed in caller`() {
        // given: a plugin that registered a valid sheet, and a principal that holds no admin area
        registerSheet()
        val bearer = machine("plain-bot", emptyList())

        // when: the manifest is requested
        val entry = manifestJson(bearer)["stylesheets"].single()

        // then: the entry names the plugin, the path with the hash, and the digest of the served bytes
        assertThat(entry["plugin"].asText()).describedAs("plugin").isEqualTo("example")
        assertThat(entry["path"].asText()).describedAs("path").isEqualTo(sheetPath())
        assertThat(entry["sha256"].asText()).describedAs("sha256").isEqualTo(wrappedDigest)
    }

    @Test
    fun `should keep the manifest at version 1 when it lists a stylesheet`() {
        // given: a registered sheet
        registerSheet()
        val bearer = machine("plain-bot", emptyList())

        // when: the manifest is requested
        val version = manifestJson(bearer)["version"].asInt()

        // then: the version did not change, because the field is additive
        assertThat(version).describedAs("version").isEqualTo(1)
    }

    @Test
    fun `should answer the manifest with 401 to an anonymous caller even when a stylesheet is registered`() {
        // given: a registered sheet
        registerSheet()

        // when: the manifest is requested without credentials
        val response = manifest()

        // then: it is refused, so the path is learnt by signed in callers only
        assertThat(response.status).describedAs("status").isEqualTo(401)
    }

    @Test
    fun `should list no stylesheet for an installation whose plugins registered none`() {
        // given: a signed in caller and no plugin with a sheet
        val bearer = machine("plain-bot", emptyList())

        // when: the manifest is requested
        val stylesheets = manifestJson(bearer)["stylesheets"]

        // then: the list is present and empty
        assertThat(stylesheets.isArray).describedAs("stylesheets is a list").isTrue()
        assertThat(stylesheets.size()).describedAs("stylesheets").isZero()
    }

    @Test
    fun `should serve the wrapped sheet at the listed path with the year-long cache policy and an ETag`() {
        // given: a registered sheet
        registerSheet()

        // when: the path is requested without credentials
        val response = get("$base${sheetPath()}").asString()

        // then: the body is the wrapped sheet, with the content type, the immutable policy and the hash as ETag
        assertThat(response.status).describedAs("status").isEqualTo(200)
        assertThat(response.headers.getFirst("Content-Type")).describedAs("content type").isEqualTo("text/css; charset=utf-8")
        assertThat(response.headers.getFirst("Cache-Control")).describedAs("cache policy").isEqualTo("public, max-age=31536000, immutable")
        assertThat(response.headers.getFirst("ETag")).describedAs("ETag").isEqualTo("\"$wrappedHash\"")
        assertThat(response.body).describedAs("body").isEqualTo(wrapped)
    }

    @Test
    fun `should withdraw the no-cache headers of the api from a stylesheet`() {
        // given: a registered sheet, which the api answers with the no-cache headers of the cache bypass
        registerSheet()

        // when: the path is requested
        val response = get("$base${sheetPath()}").asString()

        // then: the pragma and expires headers that would contradict the year-long cache are not sent
        assertThat(response.headers.containsKey("Pragma")).describedAs("Pragma header").isFalse()
        assertThat(response.headers.containsKey("Expires")).describedAs("Expires header").isFalse()
    }

    @Test
    fun `should answer 404 for a hash that does not match the registered bytes`() {
        // given: a registered sheet
        registerSheet()

        // when: the path is requested with another hash
        val response = get("$base/api/frontend/plugin-styles/example/0123456789abcdef.css").asString()

        // then: the answer is not found
        assertThat(response.status).describedAs("status").isEqualTo(404)
    }

    @Test
    fun `should answer 404 for a plugin that registered no sheet`() {
        // given: a registered sheet of the plugin example
        registerSheet()

        // when: the same hash is requested under another plugin name
        val response = get("$base/api/frontend/plugin-styles/other/$wrappedHash.css").asString()

        // then: the answer is not found
        assertThat(response.status).describedAs("status").isEqualTo(404)
    }

    @Test
    fun `should answer 404 for a path with an extra segment`() {
        // given: a registered sheet
        registerSheet()

        // when: the path has one segment more than the served pattern
        val response = get("$base/api/frontend/plugin-styles/example/nested/$wrappedHash.css").asString()

        // then: the answer is not found
        assertThat(response.status).describedAs("status").isEqualTo(404)
    }

    @Test
    fun `should list no stylesheet and answer 404 while the switch is off, and keep the pages`() {
        // given: a plugin with a sheet and a page, and the plugin styles switched off on the running instance
        registerSheet()
        pluginUi.forPlugin(ExamplePlugin()).registerPage(
            PluginUiPage(id = "status", title = "Status", area = "maintenance", blocks = listOf(PluginUiBlock.Text("Hello")))
        )
        val bearer = machine("maintenance-bot", listOf("ingot.admin.maintenance"))
        reposilite.extensions.facade<LocalConfiguration>().pluginStyles.update { false }

        // when: the manifest and the path are requested
        val json = manifestJson(bearer)
        val path = get("$base${sheetPath()}").asString()

        // then: the list is empty, the path is not found, and the page of the same plugin is still listed
        assertThat(json["stylesheets"].size()).describedAs("stylesheets").isZero()
        assertThat(path.status).describedAs("status of the path").isEqualTo(404)
        assertThat(json["pages"].map { it["id"].asText() }).describedAs("page ids").containsExactly("status")
    }

}
