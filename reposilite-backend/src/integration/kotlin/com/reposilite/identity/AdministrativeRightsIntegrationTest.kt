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

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import net.onelitefeather.ingot.identity.api.CreatedTokenDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import kong.unirest.core.Unirest.delete

/**
 * Granting rights over HTTP: a holder of an area may manage its own objects, but may not raise anyone, itself included,
 * above the rights it holds, and only an instance admin may grant or revoke the instance role. Every test runs on a fresh
 * instance; the callers are principals with bearer tokens, and the setup is made by the legacy manager.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class AdministrativeRightsIntegrationTest : ReposiliteSpecification() {

    private val admin: Pair<String, String>
        get() = useDefaultManagementToken()

    private fun identity(path: String): String =
        "$base/api/identity$path"

    /** Creates a machine principal with the given nodes and a token of it; returns the bearer header of the token. */
    private fun machine(name: String, nodes: List<Pair<String, Boolean>> = emptyList()): String {
        val created = post(identity("/principals")).basicAuth(admin.first, admin.second).body(mapOf("kind" to "MACHINE", "name" to name)).asString()
        assertThat(created.status).describedAs("status of creating $name").isEqualTo(200)
        if (nodes.isNotEmpty()) {
            val etag = get(identity("/principals/$name/nodes")).basicAuth(admin.first, admin.second).asString().headers.getFirst("ETag")
            val set = put(identity("/principals/$name/nodes"))
                .basicAuth(admin.first, admin.second)
                .header("If-Match", etag)
                .body(mapOf("nodes" to nodes.map { (node, allowed) -> mapOf("node" to node, "allowed" to allowed) }))
                .asString()
            assertThat(set.status).describedAs("status of the nodes of $name").isEqualTo(200)
        }
        val token = post(identity("/principals/$name/tokens"))
            .basicAuth(admin.first, admin.second)
            .body(mapOf("name" to "$name-token"))
            .asJacksonObject(CreatedTokenDto::class)
        assertThat(token.status).describedAs("status of the token of $name").isEqualTo(200)
        return "Bearer ${token.body.secret}"
    }

    private fun team(name: String, nodes: List<Pair<String, Boolean>> = emptyList()) {
        val created = post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to name)).asString()
        assertThat(created.status).describedAs("status of creating team $name").isEqualTo(200)
        if (nodes.isNotEmpty()) {
            val set = put(identity("/teams/$name/nodes"))
                .basicAuth(admin.first, admin.second)
                .body(mapOf("nodes" to nodes.map { (node, allowed) -> mapOf("node" to node, "allowed" to allowed) }))
                .asString()
            assertThat(set.status).describedAs("status of the nodes of team $name").isEqualTo(200)
        }
    }

    @Test
    fun `a users holder is refused when it grants the instance role to itself`() {
        val users = machine("users-bot", listOf("ingot.admin.users" to true))

        val status = put(identity("/principals/users-bot/instance-admin")).header("Authorization", users).asString().status

        assertThat(status).describedAs("status of a self grant of the instance role").isEqualTo(403)
    }

    @Test
    fun `a users holder is refused when it grants the instance role to another principal`() {
        val users = machine("users-bot", listOf("ingot.admin.users" to true))
        machine("target-bot")

        val status = put(identity("/principals/target-bot/instance-admin")).header("Authorization", users).asString().status

        assertThat(status).describedAs("status of granting the instance role to another principal").isEqualTo(403)
    }

    @Test
    fun `a users holder is refused when it revokes the instance role`() {
        val boss = machine("boss-bot", listOf("ingot.admin.*" to true))
        machine("other-bot", listOf("ingot.admin.*" to true))
        val users = machine("users-bot", listOf("ingot.admin.users" to true))
        assertThat(boss).isNotBlank()

        val status = delete(identity("/principals/other-bot/instance-admin")).header("Authorization", users).asString().status

        assertThat(status).describedAs("status of revoking the instance role").isEqualTo(403)
    }

    @Test
    fun `a users holder is refused when it gives itself the instance admin node`() {
        val users = machine("users-bot", listOf("ingot.admin.users" to true))

        val status = put(identity("/principals/users-bot/nodes"))
            .header("Authorization", users)
            .body(mapOf("nodes" to listOf(mapOf("node" to "ingot.admin.*", "allowed" to true))))
            .asString()
            .status

        assertThat(status).describedAs("status of giving itself ingot.admin.*").isEqualTo(403)
    }

    @Test
    fun `a users holder may give another principal a node it holds itself`() {
        val users = machine("users-bot", listOf("ingot.admin.users" to true))
        machine("target-bot")

        val status = put(identity("/principals/target-bot/nodes"))
            .header("Authorization", users)
            .body(mapOf("nodes" to listOf(mapOf("node" to "ingot.admin.users", "allowed" to true))))
            .asString()
            .status

        assertThat(status).describedAs("status of giving a held node").isEqualTo(200)
    }

    @Test
    fun `a teams holder is refused a team node it does not hold`() {
        val teams = machine("teams-bot", listOf("ingot.admin.teams" to true))
        team("deployers")

        val status = put(identity("/teams/deployers/nodes"))
            .header("Authorization", teams)
            .body(mapOf("nodes" to listOf(mapOf("node" to "ingot.repository.releases.read", "allowed" to true))))
            .asString()
            .status

        assertThat(status).describedAs("status of a team node it does not hold").isEqualTo(403)
    }

    @Test
    fun `a teams holder is refused to add itself to a team that holds the instance admin node`() {
        val teams = machine("teams-bot", listOf("ingot.admin.teams" to true))
        team("root", listOf("ingot.admin.*" to true))

        val status = put(identity("/teams/root/members"))
            .header("Authorization", teams)
            .body(mapOf("members" to listOf("teams-bot")))
            .asString()
            .status

        assertThat(status).describedAs("status of adding itself to an admin team").isEqualTo(403)
    }

    @Test
    fun `a teams holder is refused a repository grant it does not hold`() {
        val teams = machine("teams-bot", listOf("ingot.admin.teams" to true))
        team("deployers")

        val status = put(identity("/teams/deployers/grants"))
            .header("Authorization", teams)
            .body(mapOf("grants" to listOf(mapOf("repository" to "releases", "pathPrefix" to "net/example", "role" to "DEPLOY"))))
            .asString()
            .status

        assertThat(status).describedAs("status of a grant it does not hold").isEqualTo(403)
    }

    @Test
    fun `a tokens holder is refused a token of a principal that holds more than it does`() {
        val tokens = machine("tokens-bot", listOf("ingot.admin.tokens" to true))
        machine("boss-bot", listOf("ingot.admin.*" to true))

        val status = post(identity("/principals/boss-bot/tokens"))
            .header("Authorization", tokens)
            .body(mapOf("name" to "stolen"))
            .asString()
            .status

        assertThat(status).describedAs("status of a token for a richer principal").isEqualTo(403)
    }

    @Test
    fun `an instance admin grants the instance role and the admin nodes`() {
        val boss = machine("boss-bot", listOf("ingot.admin.*" to true))
        machine("target-bot")

        val grant = put(identity("/principals/target-bot/instance-admin")).header("Authorization", boss).asString().status
        val nodes = put(identity("/principals/target-bot/nodes"))
            .header("Authorization", boss)
            .body(mapOf("nodes" to listOf(mapOf("node" to "ingot.admin.*", "allowed" to true))))
            .asString()
            .status

        assertThat(grant).describedAs("status of the instance role grant").isEqualTo(200)
        assertThat(nodes).describedAs("status of the admin node").isEqualTo(200)
    }
}
