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
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.UNAUTHORIZED
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The caller's own access summary over HTTP. A plain member, without any administrative node, reads its own summary, and
 * the summary never names another principal.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class OwnAccessIntegrationTest : ReposiliteSpecification() {

    private val admin: Pair<String, String>
        get() = useDefaultManagementToken()

    private fun identity(path: String): String =
        "$base/api/identity$path"

    private fun etagOfPrincipalNodes(name: String): String =
        get(identity("/principals/$name/nodes")).basicAuth(admin.first, admin.second).asString().headers.getFirst("ETag")

    /** Creates a plain member "alice" that may read releases and holds one token, and returns the token as basic auth. */
    private fun plainMemberToken(): Pair<String, String> {
        post(identity("/principals")).basicAuth(admin.first, admin.second).body(mapOf("kind" to "USER", "name" to "alice")).asString()
        put(identity("/principals/alice/nodes"))
            .basicAuth(admin.first, admin.second)
            .header("If-Match", etagOfPrincipalNodes("alice"))
            .body(mapOf("nodes" to listOf(mapOf("node" to "ingot.repository.releases.read", "allowed" to true))))
            .asString()
        val created = post(identity("/principals/alice/tokens"))
            .basicAuth(admin.first, admin.second)
            .body(mapOf("name" to "alice-read"))
            .asJacksonObject(JsonNode::class)
        return "alice-read" to created.body.get("secret").asText()
    }

    @Test
    fun `anonymous callers are asked to sign in`() {
        // when
        val response = get(identity("/me/access")).asString()

        // then
        assertThat(response.status).describedAs("status without credentials").isEqualTo(UNAUTHORIZED.code)
    }

    @Test
    fun `a plain member reads its own summary without an administrative node`() {
        // given
        val (name, secret) = plainMemberToken()

        // when
        val response = get(identity("/me/access")).basicAuth(name, secret).asJacksonObject(JsonNode::class)

        // then
        assertThat(response.status).describedAs("status").isEqualTo(OK.code)
        assertThat(response.body.get("principal").get("name").asText()).describedAs("principal").isEqualTo("alice")
        assertThat(response.body.get("nodes").map { it.asText() }).describedAs("nodes").containsExactly("ingot.repository.releases.read")
        assertThat(response.body.get("token").asText()).describedAs("token that made the request").isEqualTo("alice-read")
    }

    @Test
    fun `a principal parameter naming another principal does not change the answer`() {
        // given: the manager is another principal than the caller
        val (name, secret) = plainMemberToken()

        // when
        val response = get(identity("/me/access?principal=manager")).basicAuth(name, secret).asJacksonObject(JsonNode::class)

        // then
        assertThat(response.status).describedAs("status").isEqualTo(OK.code)
        assertThat(response.body.get("principal").get("name").asText()).describedAs("principal of the caller").isEqualTo("alice")
        assertThat(response.body.get("instanceAdmin").asBoolean()).describedAs("instance administrator").isFalse
    }
}
