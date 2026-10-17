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

package com.reposilite.identity

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import net.onelitefeather.ingot.identity.api.CreatedTokenDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.HttpRequest.BodyPublishers
import java.util.Base64
import java.util.stream.Stream
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put

/**
 * The admin areas over HTTP. Every admin endpoint is called by five callers on one fresh instance: a principal with the area
 * node, one without it, an instance admin denied the area, an instance admin whose token narrows the area away, and the
 * legacy manager. The area node decides, an area denial never restricts an instance admin, and a token narrows it.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class AdminAreaEnforcementIntegrationTest : ReposiliteSpecification() {

    private val admin: Pair<String, String>
        get() = useDefaultManagementToken()

    private val legacyManager: String
        get() = basic(admin.first, admin.second)

    private val client: HttpClient = HttpClient.newHttpClient()

    private fun identity(path: String): String =
        "$base/api/identity$path"

    private fun basic(name: String, secret: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$name:$secret".toByteArray())

    /** Creates a machine principal with the given nodes, and a token of it that narrows to [tokenNodes] when given. */
    private fun machine(name: String, nodes: List<Pair<String, Boolean>>? = null, tokenNodes: List<Pair<String, Boolean>>? = null): String {
        val created = post(identity("/principals")).basicAuth(admin.first, admin.second).body(mapOf("kind" to "MACHINE", "name" to name)).asString()
        assertThat(created.status).describedAs("status of creating $name").isEqualTo(200)
        nodes?.let { setNodes(name, it) }
        // Token names are unique across the instance, so each principal names its token after itself.
        val body = mutableMapOf<String, Any>("name" to "$name-token")
        tokenNodes?.let { body["nodes"] = it.map { (node, allowed) -> mapOf("node" to node, "allowed" to allowed) } }
        val token = post(identity("/principals/$name/tokens")).basicAuth(admin.first, admin.second).body(body).asJacksonObject(CreatedTokenDto::class)
        assertThat(token.status).describedAs("status of the token of $name").isEqualTo(200)
        return "Bearer ${token.body.secret}"
    }

    private fun setNodes(principal: String, nodes: List<Pair<String, Boolean>>) {
        val etag = get(identity("/principals/$principal/nodes")).basicAuth(admin.first, admin.second).asString().headers.getFirst("ETag")
        val replaced = put(identity("/principals/$principal/nodes"))
            .basicAuth(admin.first, admin.second)
            .header("If-Match", etag)
            .body(mapOf("nodes" to nodes.map { (node, allowed) -> mapOf("node" to node, "allowed" to allowed) }))
            .asString()
        assertThat(replaced.status).describedAs("status of the nodes of $principal").isEqualTo(200)
    }

    private fun statusOf(method: String, path: String, authorization: String, body: String?): Int {
        val builder = HttpRequest.newBuilder(URI.create("$base$path"))
            .header("Authorization", authorization)
            .header("Content-Type", "application/json")
        val publisher = if (body == null) BodyPublishers.noBody() else BodyPublishers.ofString(body)
        return client.send(builder.method(method, publisher).build(), BodyHandlers.discarding()).statusCode()
    }

    @ParameterizedTest(name = "{0}: {1} {2}")
    @MethodSource("areaEndpoints")
    fun `an admin endpoint is decided by its area node, and an area denial never restricts an instance admin`(
        area: String,
        method: String,
        path: String,
        body: String?,
    ) {
        // given: one caller per shape
        val withArea = machine("area-bot", nodes = listOf("ingot.admin.$area" to true))
        val withoutArea = machine("bare-bot")
        val denied = machine("denied-bot", nodes = listOf("ingot.admin.*" to true, "ingot.admin.$area" to false))
        val narrowed = machine(
            "narrowed-bot",
            nodes = listOf("ingot.admin.*" to true),
            tokenNodes = listOf("ingot.admin.*" to true, "ingot.admin.$area" to false),
        )

        // when / then
        assertThat(statusOf(method, path, withoutArea, body))
            .describedAs("$area: a principal without the area node is refused")
            .isEqualTo(403)
        assertThat(statusOf(method, path, withArea, body))
            .describedAs("$area: a principal with the area node is allowed")
            .isNotIn(401, 403)
        assertThat(statusOf(method, path, denied, body))
            .describedAs("$area: an instance admin with a denial of the area is still allowed")
            .isNotIn(401, 403)
        assertThat(statusOf(method, path, narrowed, body))
            .describedAs("$area: an admin token narrowed without the area is refused")
            .isEqualTo(403)
        assertThat(statusOf(method, path, legacyManager, body))
            .describedAs("$area: the legacy manager keeps full access")
            .isNotIn(401, 403)
    }

    @Test
    fun `the check answers an admin area the way the endpoint does for an instance admin`() {
        machine("boss-bot", nodes = listOf("ingot.admin.*" to true, "ingot.admin.audit" to false))

        val check = get(identity("/check?principal=boss-bot&node=ingot.admin.audit")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class)

        assertThat(check.status).isEqualTo(200)
        assertThat(check.body.get("allowed").asBoolean()).describedAs("allowed").isTrue
        assertThat(check.body.get("decision").get("decidingKind").asText()).describedAs("deciding kind").isEqualTo("INSTANCE_ADMIN")
    }

    @Test
    fun `the check of an admin area respects the token narrowing of the principal`() {
        machine(
            "narrowed-bot",
            nodes = listOf("ingot.admin.*" to true),
            tokenNodes = listOf("ingot.admin.*" to true, "ingot.admin.audit" to false),
        )

        val check = get(identity("/check?principal=narrowed-bot&token=narrowed-bot-token&node=ingot.admin.audit"))
            .basicAuth(admin.first, admin.second)
            .asJacksonObject(JsonNode::class)

        assertThat(check.body.get("allowed").asBoolean()).describedAs("allowed through the narrowed token").isFalse
    }

    @Test
    fun `the console log stream names the area when a principal without it connects`() {
        // The console stream takes Basic credentials of a token, so the bearer secret of the principal is sent as its token.
        val bare = machine("bare-bot").removePrefix("Bearer ")

        val connection = (URI.create("$base/api/console/log").toURL().openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Authorization", basic("bare-bot-token", bare))
        }
        val stream = runCatching { connection.inputStream }
            .getOrElse { connection.errorStream }
            .bufferedReader()
            .readText()

        assertThat(stream).describedAs("the stream reports the refusal and names the console area").contains("console area")
    }

    companion object {
        @JvmStatic
        fun areaEndpoints(): Stream<Arguments> = Stream.of(
            arguments("settings", "GET", "/api/settings/domains", null),
            arguments("settings", "GET", "/api/settings/domain/maven", null),
            arguments("settings", "PUT", "/api/settings/domain/maven", "{}"),
            arguments("audit", "GET", "/api/audit", null),
            arguments("audit", "GET", "/api/audit/export", null),
            arguments("maintenance", "GET", "/api/maven/storage", null),
            arguments("maintenance", "POST", "/api/maven/storage/refresh", null),
            arguments("maintenance", "GET", "/api/cleanup/runs", null),
            arguments("maintenance", "POST", "/api/cleanup/policies/none/dry-run", null),
            arguments("maintenance", "POST", "/api/cleanup/policies/none/run", null),
            arguments("maintenance", "GET", "/api/maven/search/index", null),
            arguments("maintenance", "POST", "/api/maven/search/index/rebuild", "{}"),
            arguments("maintenance", "POST", "/api/maven/search/index/verify", "{}"),
            arguments("maintenance", "POST", "/api/maven/search/index/runs/none/cancel", null),
            arguments("dashboard", "GET", "/api/status/instance", null),
            arguments("dashboard", "GET", "/api/status/snapshots", null),
            arguments("dashboard", "GET", "/api/status/failures", null),
            arguments("dashboard", "DELETE", "/api/status/failures", null),
            arguments("dashboard", "GET", "/api/statistics/resolved/unique", null),
            arguments("dashboard", "GET", "/api/statistics/resolved/all", null),
            arguments("console", "POST", "/api/console/execute", "help"),
            arguments("users", "GET", "/api/identity/principals", null),
            arguments("users", "POST", "/api/identity/principals", "{\"kind\":\"MACHINE\",\"name\":\"made-by-test\"}"),
            arguments("users", "GET", "/api/identity/principals/bare-bot", null),
            arguments("users", "GET", "/api/identity/repositories/releases/access", null),
            arguments("teams", "GET", "/api/identity/teams", null),
            arguments("teams", "POST", "/api/identity/teams", "{\"name\":\"made-by-test\"}"),
            arguments("teams", "GET", "/api/identity/teams/none", null),
            arguments("teams", "PUT", "/api/identity/teams/none/parents", "{\"parents\":[]}"),
            arguments("tokens", "GET", "/api/tokens", null),
            arguments("tokens", "DELETE", "/api/tokens/none", null),
            arguments("tokens", "GET", "/api/identity/principals/bare-bot/tokens", null),
        )
    }
}
