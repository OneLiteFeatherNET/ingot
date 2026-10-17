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

package com.reposilite.maven

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.token.RoutePermission.READ
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Directory listings of a private repository for a subject whose only read right comes from artifact nodes: the repository
 * is listed, the listing leads into the allowed subtree and shows nothing beside it. Each test runs on a fresh instance with
 * its own principal, nodes and artifacts.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ArtifactListingIntegrationTest : ReposiliteSpecification() {

    private val repository = "private"

    private val admin: Pair<String, String>
        get() = useDefaultManagementToken()

    @BeforeEach
    fun storeArtifacts() {
        useDocument(repository, "net/onelitefeather/mycelium-bom/1.0", "mycelium-bom-1.0.pom", store = true)
        useDocument(repository, "net/onelitefeather/internal/1.0", "internal-1.0.pom", store = true)
        useDocument(repository, "net/other/lib/1.0", "lib-1.0.pom", store = true)
        useDocument(repository, "org/other/lib/1.0", "lib-1.0.pom", store = true)
    }

    private fun identity(path: String): String =
        "$base/api/identity$path"

    /** Creates a machine principal that holds [nodes] directly, with one token named like the principal, and returns its credentials. */
    private fun principalWithNodes(name: String, vararg nodes: Pair<String, Boolean>): Pair<String, String> {
        post(identity("/principals")).basicAuth(admin.first, admin.second).body(mapOf("kind" to "MACHINE", "name" to name)).asString()
        put(identity("/principals/$name/nodes")).basicAuth(admin.first, admin.second)
            .body(mapOf("nodes" to nodes.map { (node, allowed) -> mapOf("node" to node, "allowed" to allowed) })).asString()
        val created = post(identity("/principals/$name/tokens")).basicAuth(admin.first, admin.second)
            .body(mapOf("name" to name, "secret" to "$name-secret")).asString()
        assertThat(created.status).describedAs("token of $name created").isEqualTo(OK.code)
        return name to "$name-secret"
    }

    /** The names listed by the details API for the directory [path] of the repository, or the status when it is refused. */
    private fun listed(credentials: Pair<String, String>, path: String): List<String> {
        val response = get("$base/api/maven/details/$repository/$path").basicAuth(credentials.first, credentials.second)
            .asJacksonObject(JsonNode::class)
        assertThat(response.status).describedAs("status of the listing of '$path'").isEqualTo(OK.code)
        return response.body.get("files").map { it.get("name").asText() }
    }

    private fun statusOfListing(credentials: Pair<String, String>, path: String): Int =
        get("$base/api/maven/details/$repository/$path").basicAuth(credentials.first, credentials.second).asString().status

    @Test
    fun `the repository is part of the repository list of a subject that reads an artifact subtree`() {
        val alice = principalWithNodes("alice", "ingot.artifact.net.onelitefeather.read" to true)

        val response = get("$base/api/maven/repositories").basicAuth(alice.first, alice.second).asJacksonObject(JsonNode::class)

        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body.get("repositories").map { it.get("id").asText() }).describedAs("listed repositories").contains(repository)
    }

    @Test
    fun `the repository is part of the root directory of a subject that reads an artifact subtree`() {
        val alice = principalWithNodes("alice", "ingot.artifact.net.onelitefeather.read" to true)

        val response = get("$base/api/maven/details").basicAuth(alice.first, alice.second).asJacksonObject(JsonNode::class)

        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body.get("files").map { it.get("name").asText() }).describedAs("root entries").contains(repository)
    }

    @Test
    fun `the repository is not part of the repository list of a subject without a read right`() {
        val alice = principalWithNodes("alice", "ingot.artifact.net.onelitefeather.deploy" to true)

        val response = get("$base/api/maven/repositories").basicAuth(alice.first, alice.second).asJacksonObject(JsonNode::class)

        assertThat(response.body.get("repositories").map { it.get("id").asText() }).describedAs("listed repositories").doesNotContain(repository)
    }

    @Test
    fun `the repository root shows only the first segment of the allowed subtree`() {
        val alice = principalWithNodes("alice", "ingot.artifact.net.onelitefeather.read" to true)

        assertThat(listed(alice, "")).describedAs("root entries").containsExactly("net")
    }

    @Test
    fun `an intermediate directory shows only the allowed subtree`() {
        val alice = principalWithNodes("alice", "ingot.artifact.net.onelitefeather.read" to true)

        assertThat(listed(alice, "net")).describedAs("entries of net").containsExactly("onelitefeather")
    }

    @Test
    fun `the allowed subtree shows its artifacts`() {
        val alice = principalWithNodes("alice", "ingot.artifact.net.onelitefeather.read" to true)

        assertThat(listed(alice, "net/onelitefeather")).describedAs("entries of the group").containsExactlyInAnyOrder("mycelium-bom", "internal")
    }

    @Test
    fun `a negated artifact node hides its artifact inside the allowed subtree`() {
        val alice = principalWithNodes(
            "alice",
            "ingot.artifact.net.onelitefeather.read" to true,
            "ingot.artifact.net.onelitefeather:internal.read" to false,
        )

        assertThat(listed(alice, "net/onelitefeather")).describedAs("entries of the group").containsExactly("mycelium-bom")
    }

    @Test
    fun `a single artifact shows its parents and nothing beside it`() {
        val alice = principalWithNodes("alice", "ingot.artifact.net.onelitefeather:mycelium-bom.read" to true)

        assertThat(listed(alice, "")).describedAs("root").containsExactly("net")
        assertThat(listed(alice, "net")).describedAs("net").containsExactly("onelitefeather")
        assertThat(listed(alice, "net/onelitefeather")).describedAs("group").containsExactly("mycelium-bom")
    }

    @Test
    fun `a directory outside the allowed subtree is refused like any directory the subject may not read`() {
        val alice = principalWithNodes("alice", "ingot.artifact.net.onelitefeather.read" to true)

        assertThat(statusOfListing(alice, "org")).describedAs("sibling of the intermediate directory").isEqualTo(FORBIDDEN.code)
        assertThat(statusOfListing(alice, "net/other")).describedAs("sibling inside the intermediate directory").isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `a refused directory answers the same status as for a subject without any rights`() {
        val alice = principalWithNodes("alice", "ingot.artifact.net.onelitefeather.read" to true)
        val bob = principalWithNodes("bob")

        assertThat(statusOfListing(alice, "org")).describedAs("refused for the artifact reader").isEqualTo(statusOfListing(bob, "org"))
    }

    @Test
    fun `a legacy route keeps refusing the directories above it`() {
        val legacy = useAuth("legacy", "legacy-secret", routes = mapOf("/$repository/net/onelitefeather" to READ))

        assertThat(statusOfListing(legacy, "net")).describedAs("directory above the route").isEqualTo(FORBIDDEN.code)
        assertThat(statusOfListing(legacy, "net/onelitefeather")).describedAs("the route itself").isEqualTo(OK.code)
    }

    @Test
    fun `a directory refused by a negated artifact node answers as a refused directory`() {
        val alice = principalWithNodes(
            "alice",
            "ingot.artifact.net.onelitefeather.read" to true,
            "ingot.artifact.net.onelitefeather:internal.read" to false,
        )

        assertThat(statusOfListing(alice, "net/onelitefeather/internal")).describedAs("denied artifact").isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `the html index lists the same entries as the details api`() {
        val alice = principalWithNodes("alice", "ingot.artifact.net.onelitefeather.read" to true)

        val index = get("$base/$repository/net/").basicAuth(alice.first, alice.second).asString()

        assertThat(index.status).isEqualTo(OK.code)
        assertThat(index.body).describedAs("index of net").contains("onelitefeather").doesNotContain("other")
    }

    @Test
    fun `a subject with repository wide read still sees every entry`() {
        val alice = principalWithNodes("alice", "ingot.repository.private.read" to true)

        assertThat(listed(alice, "")).describedAs("root entries").containsExactlyInAnyOrder("net", "org")
        assertThat(listed(alice, "net")).describedAs("entries of net").containsExactlyInAnyOrder("onelitefeather", "other")
    }
}
