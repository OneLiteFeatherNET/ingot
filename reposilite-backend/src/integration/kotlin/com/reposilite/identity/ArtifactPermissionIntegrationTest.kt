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
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Artifact scoped permission nodes over HTTP: a deploy decided by nodes of the owner, and the check that names the deciding
 * node. Each test runs on a fresh instance; the principals, nodes and tokens are created through the identity API.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ArtifactPermissionIntegrationTest : ReposiliteSpecification() {

    private val admin: Pair<String, String>
        get() = useDefaultManagementToken()

    private fun identity(path: String): String =
        "$base/api/identity$path"

    /** Creates a machine principal that holds [nodes] directly, with one token named like the principal, and returns its credentials. */
    private fun principalWithNodes(name: String, vararg nodes: Pair<String, Boolean>): Pair<String, String> {
        post(identity("/principals")).basicAuth(admin.first, admin.second).body(mapOf("kind" to "MACHINE", "name" to name)).asString()
        put(identity("/principals/$name/nodes")).basicAuth(admin.first, admin.second)
            .body(mapOf("nodes" to nodes.map { (node, allowed) -> mapOf("node" to node, "allowed" to allowed) })).asString()
        return tokenOf(name, tokenName = name, secret = "$name-secret")
    }

    private fun tokenOf(principal: String, tokenName: String, secret: String, vararg nodes: Pair<String, Boolean>): Pair<String, String> {
        val body = mutableMapOf<String, Any>("name" to tokenName, "secret" to secret)
        if (nodes.isNotEmpty()) body["nodes"] = nodes.map { (node, allowed) -> mapOf("node" to node, "allowed" to allowed) }
        val created = post(identity("/principals/$principal/tokens")).basicAuth(admin.first, admin.second).body(body).asString()
        assertThat(created.status).describedAs("token $tokenName created").isEqualTo(OK.code)
        return tokenName to secret
    }

    private fun deployStatus(credentials: Pair<String, String>, repository: String, path: String): Int =
        put("$base/$repository/$path").body("content").basicAuth(credentials.first, credentials.second).asString().status

    @Test
    fun `deploy is allowed inside the subtree of an artifact node`() {
        val alice = principalWithNodes("alice", "ingot.artifact.net.onelitefeather.deploy" to true)

        val status = deployStatus(alice, "releases", "net/onelitefeather/mycelium-bom/1.0/mycelium-bom-1.0.jar")

        assertThat(status).describedAs("deploy inside the group").isEqualTo(OK.code)
    }

    @Test
    fun `deploy is denied outside the subtree of an artifact node`() {
        val alice = principalWithNodes("alice", "ingot.artifact.net.onelitefeather.deploy" to true)

        assertThat(deployStatus(alice, "releases", "org/other/lib/1.0/lib-1.0.jar")).describedAs("another group").isEqualTo(FORBIDDEN.code)
        assertThat(deployStatus(alice, "releases", "net/onelitefeatherX/lib/1.0/lib-1.0.jar")).describedAs("longer sibling name").isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `deploy is denied by an artifact negation although the repository allows it`() {
        val alice = principalWithNodes(
            "alice",
            "ingot.repository.releases.deploy" to true,
            "ingot.artifact.net.onelitefeather:mycelium-bom.deploy" to false,
        )

        assertThat(deployStatus(alice, "releases", "net/onelitefeather/mycelium-bom/1.0/mycelium-bom-1.0.jar")).describedAs("denied artifact").isEqualTo(FORBIDDEN.code)
        assertThat(deployStatus(alice, "releases", "net/onelitefeather/other/1.0/other-1.0.jar")).describedAs("rest of the repository").isEqualTo(OK.code)
    }

    @Test
    fun `a repository qualified artifact node applies in its repository only`() {
        val alice = principalWithNodes("alice", "ingot.repository.releases.artifact.net.onelitefeather.deploy" to true)

        assertThat(deployStatus(alice, "releases", "net/onelitefeather/lib/1.0/lib-1.0.jar")).describedAs("releases").isEqualTo(OK.code)
        assertThat(deployStatus(alice, "snapshots", "net/onelitefeather/lib/1.0-SNAPSHOT/lib-1.0-SNAPSHOT.jar")).describedAs("snapshots").isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `the check names the artifact node that decided`() {
        principalWithNodes(
            "alice",
            "ingot.repository.releases.deploy" to true,
            "ingot.artifact.net.onelitefeather.deploy" to false,
        )

        val check = get(identity("/check?principal=alice&node=ingot.artifact.net.onelitefeather:mycelium-bom.deploy"))
            .basicAuth(admin.first, admin.second)
            .asJacksonObject(JsonNode::class)

        assertThat(check.status).isEqualTo(OK.code)
        assertThat(check.body.get("allowed").asBoolean()).describedAs("allowed").isFalse
        assertThat(check.body.get("decision").get("decidingNode").asText()).describedAs("deciding node").isEqualTo("ingot.artifact.net.onelitefeather.deploy")
        assertThat(check.body.get("decision").get("decidingKind").asText()).describedAs("deciding kind").isEqualTo("ARTIFACT")
        assertThat(check.body.get("decision").get("explanation").asText()).describedAs("explanation").contains("Denied")
        assertThat(check.body.get("trace").map { it.get("kind").asText() }).describedAs("trace kinds").contains("ARTIFACT", "REPOSITORY")
    }

    @Test
    fun `a token narrowed to one artifact deploys that artifact and not a sibling in the same group`() {
        principalWithNodes("alice", "ingot.repository.releases.deploy" to true)
        val narrowed = tokenOf("alice", tokenName = "alice-bom", secret = "alice-bom-secret", "ingot.artifact.net.onelitefeather:mycelium-bom.deploy" to true)

        assertThat(deployStatus(narrowed, "releases", "net/onelitefeather/mycelium-bom/1.0/mycelium-bom-1.0.jar")).describedAs("the artifact").isEqualTo(OK.code)
        assertThat(deployStatus(narrowed, "releases", "net/onelitefeather/mycelium-api/1.0/mycelium-api-1.0.jar")).describedAs("sibling artifact").isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `a token narrowed to an artifact of one repository is denied in another repository`() {
        principalWithNodes("alice", "ingot.repository.*.deploy" to true)
        val narrowed = tokenOf(
            "alice", tokenName = "alice-releases", secret = "alice-releases-secret",
            "ingot.repository.releases.artifact.net.onelitefeather:mycelium-bom.deploy" to true,
        )

        assertThat(deployStatus(narrowed, "releases", "net/onelitefeather/mycelium-bom/1.0/mycelium-bom-1.0.jar")).describedAs("releases").isEqualTo(OK.code)
        assertThat(deployStatus(narrowed, "snapshots", "net/onelitefeather/mycelium-bom/1.0-SNAPSHOT/mycelium-bom-1.0-SNAPSHOT.jar")).describedAs("snapshots").isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `a token cannot deploy through an artifact node its owner lacks`() {
        principalWithNodes("alice", "ingot.artifact.net.onelitefeather:mycelium-bom.deploy" to true)
        val wide = tokenOf("alice", tokenName = "alice-wide", secret = "alice-wide-secret", "ingot.artifact.net.onelitefeather.deploy" to true)

        assertThat(deployStatus(wide, "releases", "net/onelitefeather/mycelium-bom/1.0/mycelium-bom-1.0.jar")).describedAs("owner may").isEqualTo(OK.code)
        assertThat(deployStatus(wide, "releases", "net/onelitefeather/mycelium-api/1.0/mycelium-api-1.0.jar")).describedAs("owner may not").isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `a token with no artifact node for the path is denied although its owner may deploy`() {
        val owner = principalWithNodes("alice", "ingot.repository.releases.deploy" to true)
        val narrowed = tokenOf("alice", tokenName = "alice-bom", secret = "alice-bom-secret", "ingot.artifact.net.onelitefeather:mycelium-bom.deploy" to true)

        assertThat(deployStatus(owner, "releases", "org/other/lib/1.0/lib-1.0.jar")).describedAs("owner token").isEqualTo(OK.code)
        assertThat(deployStatus(narrowed, "releases", "org/other/lib/1.0/lib-1.0.jar")).describedAs("narrowed token").isEqualTo(FORBIDDEN.code)
    }
}
