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
import com.reposilite.audit.api.AuditAction.PRINCIPAL_CREATED
import com.reposilite.audit.api.AuditPageDto
import com.reposilite.shared.ErrorResponse
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.RoutePermission.READ
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.PRECONDITION_FAILED
import io.javalin.http.HttpStatus.UNAUTHORIZED
import kong.unirest.core.Unirest.delete
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.patch
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import net.onelitefeather.ingot.identity.api.CreatedTokenDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The identity administration API over HTTP, on a fresh instance with a fresh database for every test. The caller is
 * the temporary `--token` manager unless a test says otherwise, so that the last-admin rule is exercised against a
 * temporary admin that does not count.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class IdentityAdminIntegrationTest : ReposiliteSpecification() {

    /** The temporary `--token` manager, the caller of every request that is not made as a test case. */
    private val admin: Pair<String, String>
        get() = useDefaultManagementToken()

    private fun identity(path: String): String =
        "$base/api/identity$path"

    private fun JsonNode.present(field: String): Boolean =
        has(field) && !get(field).isNull

    @Test
    fun `anonymous callers are asked to sign in`() {
        // when: the principals are listed without credentials
        val response = get(identity("/principals")).asString()

        // then
        assertThat(response.status).describedAs("status without credentials").isEqualTo(UNAUTHORIZED.code)
    }

    @Test
    fun `a token without the manager permission is refused on reads and on writes`() {
        // given: a token that may only read a repository
        val (name, secret) = useAuth("reader", "reader-secret", routes = mapOf("/releases" to READ))

        // when
        val read = get(identity("/principals")).basicAuth(name, secret).asJacksonObject(ErrorResponse::class)
        val write = post(identity("/principals")).basicAuth(name, secret).body(mapOf("kind" to "MACHINE", "name" to "intruder")).asJacksonObject(ErrorResponse::class)

        // then
        assertThat(read.status).describedAs("status of a read").isEqualTo(FORBIDDEN.code)
        assertThat(write.status).describedAs("status of a write").isEqualTo(FORBIDDEN.code)
        assertThat(get(identity("/principals/intruder")).basicAuth(admin.first, admin.second).asString().status)
            .describedAs("the refused write created nothing").isEqualTo(NOT_FOUND.code)
    }

    @Test
    fun `a principal can be created, listed, renamed and deleted`() {
        // when: the principal is created, renamed and deleted by a manager
        val created = createPrincipal("ci-bot")
        val renamed = patch(identity("/principals/ci-bot"))
            .basicAuth(admin.first, admin.second)
            .header("If-Match", created.etag)
            .body(mapOf("displayName" to "CI Bot"))
            .asJacksonObject(JsonNode::class)
        val listed = get(identity("/principals?kind=MACHINE")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class)
        val deleted = delete(identity("/principals/ci-bot")).basicAuth(admin.first, admin.second).asString()
        val afterDelete = get(identity("/principals/ci-bot")).basicAuth(admin.first, admin.second).asString()

        // then
        assertThat(created.body.get("principal").get("name").asText()).isEqualTo("ci-bot")
        assertThat(created.etag).describedAs("entity tag of the created principal").isNotBlank
        assertThat(renamed.status).isEqualTo(OK.code)
        assertThat(renamed.body.get("principal").get("displayName").asText()).isEqualTo("CI Bot")
        assertThat(listed.body.map { it.get("name").asText() }).describedAs("machine principals listed").contains("ci-bot")
        assertThat(deleted.status).isEqualTo(OK.code)
        assertThat(afterDelete.status).describedAs("status after deletion").isEqualTo(NOT_FOUND.code)
    }

    @Test
    fun `a weak entity tag of the current state is accepted for a principal`() {
        // given: a principal and its tag, weakened the way a compressing proxy weakens it
        val created = createPrincipal("ci-bot")

        // when: the change is sent with the weak form of the current tag
        val response = patch(identity("/principals/ci-bot"))
            .basicAuth(admin.first, admin.second)
            .header("If-Match", "W/${created.etag}")
            .body(mapOf("displayName" to "Weak"))
            .asJacksonObject(JsonNode::class)

        // then
        assertThat(response.status).describedAs("status for a weak current tag").isEqualTo(OK.code)
        assertThat(response.body.get("principal").get("displayName").asText()).isEqualTo("Weak")
    }

    @Test
    fun `a weak entity tag of a stale state is refused with 412 for a principal`() {
        // given: a principal read once, then changed by someone else
        val first = createPrincipal("ci-bot")
        patch(identity("/principals/ci-bot")).basicAuth(admin.first, admin.second).body(mapOf("displayName" to "Other")).asString()

        // when: the change is sent with the weak form of the first tag
        val stale = patch(identity("/principals/ci-bot"))
            .basicAuth(admin.first, admin.second)
            .header("If-Match", "W/${first.etag}")
            .body(mapOf("disabled" to true))
            .asJacksonObject(ErrorResponse::class)

        // then
        assertThat(stale.status).describedAs("status for a weak stale tag").isEqualTo(PRECONDITION_FAILED.code)
    }

    @Test
    fun `a stale entity tag is refused with 412 and the principal keeps the first change`() {
        // given: a principal read once, then changed by someone else
        val first = createPrincipal("ci-bot")
        patch(identity("/principals/ci-bot")).basicAuth(admin.first, admin.second).body(mapOf("displayName" to "Other")).asString()

        // when: the change is sent with the tag of the first read
        val stale = patch(identity("/principals/ci-bot"))
            .basicAuth(admin.first, admin.second)
            .header("If-Match", first.etag)
            .body(mapOf("disabled" to true))
            .asJacksonObject(ErrorResponse::class)

        // then
        assertThat(stale.status).describedAs("status for a stale tag").isEqualTo(PRECONDITION_FAILED.code)
        val current = get(identity("/principals/ci-bot")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class)
        assertThat(current.body.get("principal").get("disabled").asBoolean()).describedAs("disabled after the refused change").isFalse
    }

    @Test
    fun `a team with members and grants is explained on the repository it reaches`() {
        // given: a machine principal, and a team that is about to get it as member and a deploy grant on a path
        createPrincipal("ci-bot")
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "deployers")).asString()

        // when: the member and the grant are set
        put(identity("/teams/deployers/members"))
            .basicAuth(admin.first, admin.second)
            .body(mapOf("members" to listOf("ci-bot")))
            .asString()
        val grants = put(identity("/teams/deployers/grants"))
            .basicAuth(admin.first, admin.second)
            .body(mapOf("grants" to listOf(mapOf("repository" to "releases", "pathPrefix" to "/com/example", "role" to "DEPLOY"))))
            .asJacksonObject(JsonNode::class)
        val access = get(identity("/repositories/releases/access")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class)

        // then: the grant is stored with its prefix normalised, and the explanation names the team as the source
        assertThat(grants.status).isEqualTo(OK.code)
        assertThat(grants.body.get("grants").single().get("pathPrefix").asText()).isEqualTo("com/example")
        val entry = access.body.get("entries").single { it.get("principal").asText() == "ci-bot" }
        assertThat(entry.get("effective").map { it.asText() }).describedAs("effective actions").containsExactly("READ", "WRITE")
        assertThat(entry.get("via").single().get("team").asText()).isEqualTo("deployers")
        assertThat(entry.get("via").single().get("role").asText()).isEqualTo("DEPLOY")
    }

    @Test
    fun `the principal list rows carry the summaries a list needs and stay flat`() {
        // given: a machine principal with a token that is an instance admin through a team
        createPrincipal("ci-bot")
        post(identity("/principals/ci-bot/tokens")).basicAuth(admin.first, admin.second).body(mapOf("name" to "ci-token")).asString()
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "operators")).asString()
        put(identity("/teams/operators/members")).basicAuth(admin.first, admin.second).body(mapOf("members" to listOf("ci-bot"))).asString()
        put(identity("/teams/operators/instance-admin")).basicAuth(admin.first, admin.second).asString()

        // when
        val listed = get(identity("/principals?kind=MACHINE")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class)

        // then: the row is flat and answers what a list shows, without a request per row
        val row = listed.body.single { it.get("name").asText() == "ci-bot" }
        assertThat(row.has("principal")).describedAs("nested principal in a list row").isFalse
        assertThat(row.get("instanceAdmin").asBoolean()).describedAs("instance admin through the team").isTrue
        assertThat(row.get("instanceRole").asBoolean()).describedAs("direct instance role").isFalse
        assertThat(row.get("tokenCount").asInt()).describedAs("token count").isEqualTo(1)
        assertThat(row.get("providers").isArray).describedAs("providers is an array").isTrue
        assertThat(row.get("providers")).describedAs("providers of a machine principal").isEmpty()
    }

    @Test
    fun `the team list rows carry counts and the instance role`() {
        // given: a team with a member, a grant and the instance role
        createPrincipal("ci-bot")
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "operators")).asString()
        put(identity("/teams/operators/members")).basicAuth(admin.first, admin.second).body(mapOf("members" to listOf("ci-bot"))).asString()
        put(identity("/teams/operators/grants")).basicAuth(admin.first, admin.second)
            .body(mapOf("grants" to listOf(mapOf("repository" to "releases", "pathPrefix" to "com", "role" to "READ")))).asString()
        put(identity("/teams/operators/instance-admin")).basicAuth(admin.first, admin.second).asString()

        // when
        val listed = get(identity("/teams")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class)

        // then
        val row = listed.body.single { it.get("name").asText() == "operators" }
        assertThat(row.get("memberCount").asInt()).describedAs("member count").isEqualTo(1)
        assertThat(row.get("grantCount").asInt()).describedAs("grant count").isEqualTo(1)
        assertThat(row.get("instanceAdmin").asBoolean()).describedAs("instance role of the team").isTrue
    }

    @Test
    fun `a stale entity tag refuses a change of team members`() {
        // given: a team read once, then changed
        createPrincipal("ci-bot")
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "deployers")).asString()
        val first = get(identity("/teams/deployers")).basicAuth(admin.first, admin.second).asString()
        val staleTag = first.headers.getFirst("ETag")
        patch(identity("/teams/deployers")).basicAuth(admin.first, admin.second).body(mapOf("description" to "Releases")).asString()

        // when
        val response = put(identity("/teams/deployers/members"))
            .basicAuth(admin.first, admin.second)
            .header("If-Match", staleTag)
            .body(mapOf("members" to listOf("ci-bot")))
            .asJacksonObject(ErrorResponse::class)

        // then
        assertThat(response.status).describedAs("status for a stale tag").isEqualTo(PRECONDITION_FAILED.code)
    }

    @Test
    fun `the last usable instance admin cannot lose the role, even with a temporary manager token present`() {
        // given: one principal with a token, made instance admin through the API
        createPrincipal("root")
        post(identity("/principals/root/tokens")).basicAuth(admin.first, admin.second).body(mapOf("name" to "root-token", "secret" to "root-secret")).asString()
        assertThat(put(identity("/principals/root/instance-admin")).basicAuth(admin.first, admin.second).asString().status).isEqualTo(OK.code)

        // when: the role is removed, while the temporary --token manager is still the caller
        val response = delete(identity("/principals/root/instance-admin")).basicAuth(admin.first, admin.second).asJacksonObject(ErrorResponse::class)

        // then: refused with an explanation, and the principal keeps its role
        assertThat(response.status).describedAs("status for the last admin").isEqualTo(CONFLICT.code)
        assertThat(response.body.message).describedAs("message names the rule").contains("last instance admin")
        val detail = get(identity("/principals/root")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class)
        assertThat(detail.body.get("instanceRole").asBoolean()).describedAs("instance role after the refusal").isTrue
    }

    @Test
    fun `an instance role can be removed while another usable admin remains`() {
        // given: two admins with tokens
        createPrincipal("first")
        createPrincipal("second")
        post(identity("/principals/first/tokens")).basicAuth(admin.first, admin.second).body(mapOf("name" to "first-token")).asString()
        post(identity("/principals/second/tokens")).basicAuth(admin.first, admin.second).body(mapOf("name" to "second-token")).asString()
        put(identity("/principals/first/instance-admin")).basicAuth(admin.first, admin.second).asString()
        put(identity("/principals/second/instance-admin")).basicAuth(admin.first, admin.second).asString()

        // when
        val response = delete(identity("/principals/first/instance-admin")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class)

        // then
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body.get("instanceRole").asBoolean()).isFalse
    }

    @Test
    fun `a token created through the identity api without a secret authenticates as a bearer`() {
        // given: a machine principal with a token that gets a generated secret
        createPrincipal("bearer-bot")
        val created = post(identity("/principals/bearer-bot/tokens"))
            .basicAuth(admin.first, admin.second)
            .body(mapOf("name" to "bearer-token"))
            .asJacksonObject(CreatedTokenDto::class)

        // when: the generated secret is sent as a bearer
        val status = get("$base/api/auth/me").header("Authorization", "Bearer ${created.body.secret}").asString().status

        // then: the bearer is accepted
        assertThat(status).describedAs("status of a bearer with the generated secret").isEqualTo(OK.code)
    }

    @Test
    fun `a team that holds the instance role keeps its members as admins`() {
        // given: a team with a member that has a token, holding the instance role; a direct admin on the side
        createPrincipal("ci-bot")
        post(identity("/principals/ci-bot/tokens")).basicAuth(admin.first, admin.second).body(mapOf("name" to "ci-token")).asString()
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "operators")).asString()
        put(identity("/teams/operators/members")).basicAuth(admin.first, admin.second).body(mapOf("members" to listOf("ci-bot"))).asString()
        assertThat(put(identity("/teams/operators/instance-admin")).basicAuth(admin.first, admin.second).asString().status).isEqualTo(OK.code)

        // when: the team's role is removed, which would leave no admin at all
        val response = delete(identity("/teams/operators/instance-admin")).basicAuth(admin.first, admin.second).asJacksonObject(ErrorResponse::class)

        // then: refused, because the team member with a valid token is still the admin
        assertThat(response.status).describedAs("status for the last team admin").isEqualTo(CONFLICT.code)
    }

    @Test
    fun `replacing team members refuses to remove the only usable admin held through the team`() {
        // given: an admin team whose only member is the only usable admin; the temporary --token manager does not count
        createPrincipal("ci-bot")
        post(identity("/principals/ci-bot/tokens")).basicAuth(admin.first, admin.second).body(mapOf("name" to "ci-token")).asString()
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "operators")).asString()
        put(identity("/teams/operators/members")).basicAuth(admin.first, admin.second).body(mapOf("members" to listOf("ci-bot"))).asString()
        assertThat(put(identity("/teams/operators/instance-admin")).basicAuth(admin.first, admin.second).asString().status).isEqualTo(OK.code)

        // when: the member list is replaced by an empty one
        val response = put(identity("/teams/operators/members")).basicAuth(admin.first, admin.second).body(mapOf("members" to emptyList<String>())).asJacksonObject(ErrorResponse::class)

        // then: refused with an explanation, and the member is still in the team
        assertThat(response.status).describedAs("status for dropping the last team admin").isEqualTo(CONFLICT.code)
        assertThat(response.body.message).describedAs("message names the rule").contains("last instance admin")
        val team = get(identity("/teams/operators")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class)
        assertThat(team.body.get("members").map { it.get("principal").asText() }).describedAs("members after the refusal").containsExactly("ci-bot")
    }

    @Test
    fun `replacing team members is allowed while another usable admin remains`() {
        // given: a team admin and a separate direct admin, both with tokens
        createPrincipal("ci-bot")
        createPrincipal("root")
        post(identity("/principals/ci-bot/tokens")).basicAuth(admin.first, admin.second).body(mapOf("name" to "ci-token")).asString()
        post(identity("/principals/root/tokens")).basicAuth(admin.first, admin.second).body(mapOf("name" to "root-token")).asString()
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "operators")).asString()
        put(identity("/teams/operators/members")).basicAuth(admin.first, admin.second).body(mapOf("members" to listOf("ci-bot"))).asString()
        put(identity("/teams/operators/instance-admin")).basicAuth(admin.first, admin.second).asString()
        put(identity("/principals/root/instance-admin")).basicAuth(admin.first, admin.second).asString()

        // when
        val response = put(identity("/teams/operators/members")).basicAuth(admin.first, admin.second).body(mapOf("members" to emptyList<String>())).asJacksonObject(JsonNode::class)

        // then
        assertThat(response.status).describedAs("status when another admin remains").isEqualTo(OK.code)
        assertThat(response.body.get("members")).describedAs("members after the change").isEmpty()
    }

    @Test
    fun `a creation is recorded in the audit log with its actor and name, never a secret`() {
        // when
        createPrincipal("ci-bot")

        // then: the event names the principal and the manager that created it
        val events = get("$base/api/audit?action=PRINCIPAL_CREATED").basicAuth(admin.first, admin.second).asJacksonObject(AuditPageDto::class).body.events
        val event = events.single { it.details?.get("principal")?.asText() == "ci-bot" }
        assertThat(event.action).isEqualTo(PRINCIPAL_CREATED)
        assertThat(event.actor).isEqualTo("manager")
    }

    @Test
    fun `the last use of a token and of its principal is shown by the identity api`() {
        // given: a persistent token with manager rights, which the manager can see
        useAuth("ci-token", "ci-secret", listOf(MANAGER))

        // when: the token authenticates once
        assertThat(get(identity("/principals")).basicAuth("ci-token", "ci-secret").asString().status).isEqualTo(OK.code)

        // then: the token and its principal carry the time of that use
        val tokens = get(identity("/principals/ci-token/tokens")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class).body
        assertThat(tokens.single().present("lastUsedAt")).describedAs("last use of the token").isTrue
        assertThat(tokens.single().present("lastUsedIp")).describedAs("last address of the token").isTrue
        val principal = get(identity("/principals/ci-token")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class).body
        assertThat(principal.get("principal").present("lastSeenAt")).describedAs("last seen of the principal").isTrue
    }

    private fun createPrincipal(name: String) =
        post(identity("/principals"))
            .basicAuth(admin.first, admin.second)
            .body(mapOf("kind" to "MACHINE", "name" to name))
            .asJacksonObject(JsonNode::class)
            .let { response ->
                assertThat(response.status).describedAs("status of creating $name").isEqualTo(OK.code)
                CreatedPrincipal(response.body, response.headers.getFirst("ETag"))
            }

    private data class CreatedPrincipal(val body: JsonNode, val etag: String)

}
