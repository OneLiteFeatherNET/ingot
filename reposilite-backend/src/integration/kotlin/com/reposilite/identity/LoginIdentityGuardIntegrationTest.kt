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

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import net.onelitefeather.ingot.identity.api.CreatedTokenDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import java.util.Base64

/**
 * The guard of the login domain over HTTP, on a fresh instance with the default mode. A settings administrator may change
 * the unrelated login fields. An identity-relevant field, such as an admin group, needs `ingot.admin.idp` as well, and the
 * refusal is a generic 403.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LoginIdentityGuardIntegrationTest : ReposiliteSpecification() {

    private val admin: Pair<String, String>
        get() = useDefaultManagementToken()

    private fun identity(path: String): String = "$base/api/identity$path"

    private fun settings(domain: String): String = "$base/api/settings/domain/$domain"

    private fun basic(name: String, secret: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$name:$secret".toByteArray())

    /** A machine principal with the given nodes and one token of its own, returned as a basic authorization header. */
    private fun machine(name: String, nodes: List<String>): String {
        val created = post(identity("/principals")).basicAuth(admin.first, admin.second).body(mapOf("kind" to "MACHINE", "name" to name)).asString()
        assertThat(created.status).describedAs("status of creating $name").isEqualTo(200)
        val etag = get(identity("/principals/$name/nodes")).basicAuth(admin.first, admin.second).asString().headers.getFirst("ETag")
        val replaced = put(identity("/principals/$name/nodes"))
            .basicAuth(admin.first, admin.second)
            .header("If-Match", etag)
            .body(mapOf("nodes" to nodes.map { mapOf("node" to it, "allowed" to true) }))
            .asString()
        assertThat(replaced.status).describedAs("status of the nodes of $name").isEqualTo(200)
        val token = post(identity("/principals/$name/tokens")).basicAuth(admin.first, admin.second)
            .body(mapOf("name" to "$name-token"))
            .asJacksonObject(CreatedTokenDto::class)
        assertThat(token.status).describedAs("status of the token of $name").isEqualTo(200)
        return basic("$name-token", token.body.secret)
    }

    private fun readLogin(authorization: String): Pair<String, ObjectNode> {
        val response = get(settings("login")).header("Authorization", authorization).asString()
        assertThat(response.status).describedAs("status of reading the login domain").isEqualTo(200)
        return response.headers.getFirst("ETag") to (DEFAULT_OBJECT_MAPPER.readTree(response.body) as ObjectNode)
    }

    private fun writeLogin(authorization: String, document: ObjectNode): HttpResponse<String> =
        put(settings("login")).header("Authorization", authorization).body(document.toString()).asString()

    private fun withProvider(document: ObjectNode, adminGroups: List<String>): ObjectNode {
        val provider = DEFAULT_OBJECT_MAPPER.createObjectNode()
            .put("id", "corp")
            .put("enabled", true)
            .put("issuerUrl", "https://sso.example.org")
            .put("clientId", "ingot")
            .put("clientSecret", "client-secret")
        provider.putArray("adminGroups").apply { adminGroups.forEach { add(it) } }
        document.putArray("oidcProviders").add(provider)
        return document
    }

    @Test
    fun `a settings administrator cannot change an admin group of the login domain, the idp administrator can`() {
        // given: an OIDC provider with one admin group, written by the instance admin
        val instanceAdmin = basic(admin.first, admin.second)
        val (_, document) = readLogin(instanceAdmin)
        assertThat(writeLogin(instanceAdmin, withProvider(document, listOf("ingot-admins"))).status)
            .describedAs("status of the instance admin").isEqualTo(200)
        val settingsOnly = machine("settings-bot", listOf("ingot.admin.settings"))
        val idpAdmin = machine("idp-bot", listOf("ingot.admin.settings", "ingot.admin.idp"))

        // when: a settings administrator adds a group to the admin groups
        val (etag, changed) = readLogin(settingsOnly)
        adminGroupsOf(changed).add("everyone")
        val refused = put(settings("login")).header("Authorization", settingsOnly).header("If-Match", etag).body(changed.toString()).asString()

        // then: refused, and the body names neither the new nor the stored group
        assertThat(refused.status).describedAs("status without the idp area").isEqualTo(403)
        assertThat(refused.body).describedAs("body of the refusal").doesNotContain("everyone").doesNotContain("ingot-admins")

        // and: the idp administrator may write the same change
        val (idpEtag, idpChanged) = readLogin(idpAdmin)
        adminGroupsOf(idpChanged).add("everyone")
        val accepted = put(settings("login")).header("Authorization", idpAdmin).header("If-Match", idpEtag).body(idpChanged.toString()).asString()
        assertThat(accepted.status).describedAs("status with the idp area").isEqualTo(200)
    }

    private fun adminGroupsOf(document: ObjectNode): ArrayNode =
        document.get("oidcProviders").get(0).get("adminGroups") as ArrayNode

    @Test
    fun `a settings administrator cannot enable the legacy LDAP authentication, the idp administrator can`() {
        // given
        val settingsOnly = machine("ldap-bot", listOf("ingot.admin.settings"))
        val idpAdmin = machine("ldap-idp-bot", listOf("ingot.admin.settings", "ingot.admin.idp"))
        val (etag, document) = readDomain("authentication", settingsOnly)

        // when: a settings administrator enables the directory login
        (document.get("ldap") as ObjectNode).put("enabled", true)
        val refused = put(settings("authentication")).header("Authorization", settingsOnly).header("If-Match", etag).body(document.toString()).asString()

        // then
        assertThat(refused.status).describedAs("status of enabling the directory without the idp area").isEqualTo(403)

        // and: the same settings administrator may still change the brute force protection
        val (bruteEtag, brute) = readDomain("authentication", settingsOnly)
        (brute.get("bruteForceProtection") as ObjectNode).put("maxAttempts", 3)
        val unrelated = put(settings("authentication")).header("Authorization", settingsOnly).header("If-Match", bruteEtag).body(brute.toString()).asString()
        assertThat(unrelated.status).describedAs("status of an unrelated change").isEqualTo(200)

        // and: the idp administrator may change an identity field of the directory
        val (idpEtag, idpDocument) = readDomain("authentication", idpAdmin)
        (idpDocument.get("ldap") as ObjectNode).put("hostname", "ldap.example.org")
        val accepted = put(settings("authentication")).header("Authorization", idpAdmin).header("If-Match", idpEtag).body(idpDocument.toString()).asString()
        assertThat(accepted.status).describedAs("status with the idp area").isEqualTo(200)
    }

    private fun readDomain(domain: String, authorization: String): Pair<String, ObjectNode> {
        val response = get(settings(domain)).header("Authorization", authorization).asString()
        assertThat(response.status).describedAs("status of reading the $domain domain").isEqualTo(200)
        return response.headers.getFirst("ETag") to (DEFAULT_OBJECT_MAPPER.readTree(response.body) as ObjectNode)
    }

    @Test
    fun `a settings administrator may change an unrelated login field`() {
        // given
        val settingsOnly = machine("session-bot", listOf("ingot.admin.settings"))
        val (etag, document) = readLogin(settingsOnly)

        // when
        document.put("sessionLifetimeMinutes", 45)
        val response = put(settings("login")).header("Authorization", settingsOnly).header("If-Match", etag).body(document.toString()).asString()

        // then
        assertThat(response.status).describedAs("status of an unrelated change").isEqualTo(200)
    }
}
