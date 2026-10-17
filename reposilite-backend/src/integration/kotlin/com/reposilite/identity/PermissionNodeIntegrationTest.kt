@file:Suppress("FunctionName")

package com.reposilite.identity

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.token.RoutePermission.READ
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.PRECONDITION_FAILED
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The permission node endpoints over HTTP, on a fresh instance with a fresh database for every test. The caller is the
 * temporary `--token` manager unless a test says otherwise.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class PermissionNodeIntegrationTest : ReposiliteSpecification() {

    private val admin: Pair<String, String>
        get() = useDefaultManagementToken()

    private fun identity(path: String): String =
        "$base/api/identity$path"

    private fun etagOfTeam(name: String): String =
        get(identity("/teams/$name/nodes")).basicAuth(admin.first, admin.second).asString().headers.getFirst("ETag")

    @Test
    fun `a team node list is replaced as a whole and read back with its entity tag`() {
        // given
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "deployers")).asString()
        val etag = etagOfTeam("deployers")

        // when
        val replaced = put(identity("/teams/deployers/nodes"))
            .basicAuth(admin.first, admin.second)
            .header("If-Match", etag)
            .body(mapOf("nodes" to listOf(mapOf("node" to "ingot.repository.releases.deploy", "allowed" to true))))
            .asJacksonObject(JsonNode::class)
        val read = get(identity("/teams/deployers/nodes")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class)

        // then
        assertThat(replaced.status).isEqualTo(OK.code)
        assertThat(read.body.get("nodes").single().get("node").asText()).isEqualTo("ingot.repository.releases.deploy")
        assertThat(read.body.get("nodes").single().get("allowed").asBoolean()).isTrue
    }

    @Test
    fun `a node list with a stale entity tag is refused with 412`() {
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "deployers")).asString()
        val first = etagOfTeam("deployers")
        put(identity("/teams/deployers/nodes")).basicAuth(admin.first, admin.second).body(mapOf("nodes" to listOf(mapOf("node" to "*", "allowed" to true)))).asString()

        val stale = put(identity("/teams/deployers/nodes"))
            .basicAuth(admin.first, admin.second)
            .header("If-Match", first)
            .body(mapOf("nodes" to emptyList<Any>()))
            .asString()

        assertThat(stale.status).describedAs("status").isEqualTo(PRECONDITION_FAILED.code)
    }

    @Test
    fun `an invalid node list is refused with 400 and names every problem`() {
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "deployers")).asString()

        val refused = put(identity("/teams/deployers/nodes"))
            .basicAuth(admin.first, admin.second)
            .body(mapOf("nodes" to listOf(mapOf("node" to "ingot.admin.billing", "allowed" to true), mapOf("node" to "ingot.*", "allowed" to true))))
            .asJacksonObject(JsonNode::class)

        assertThat(refused.status).isEqualTo(BAD_REQUEST.code)
        assertThat(refused.body.get("message").asText()).contains("ingot.admin.billing").contains("ingot.*")
        assertThat(refused.body.get("problems").map { it.get("node").asText() })
            .describedAs("one problem per invalid row")
            .containsExactly("ingot.admin.billing", "ingot.*")
    }

    @Test
    fun `an artifact node with an ambiguous or reserved form is refused with the reason`() {
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "deployers")).asString()

        val refused = put(identity("/teams/deployers/nodes"))
            .basicAuth(admin.first, admin.second)
            .body(
                mapOf(
                    "nodes" to listOf(
                        mapOf("node" to "ingot.artifact.*:mycelium.deploy", "allowed" to true),
                        mapOf("node" to "ingot.repository.my.artifact.read", "allowed" to true),
                    ),
                ),
            )
            .asJacksonObject(JsonNode::class)

        assertThat(refused.status).isEqualTo(BAD_REQUEST.code)
        val reasons = refused.body.get("problems").associate { it.get("node").asText() to it.get("message").asText() }
        assertThat(reasons["ingot.artifact.*:mycelium.deploy"]).describedAs("star group with an artifactId").contains("ambiguous")
        assertThat(reasons["ingot.repository.my.artifact.read"]).describedAs("repository name rule").contains("may not contain a segment named 'artifact'")
    }

    @Test
    fun `a parent change that closes a cycle is refused with 409`() {
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "a")).asString()
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "b")).asString()
        put(identity("/teams/a/parents")).basicAuth(admin.first, admin.second).body(mapOf("parents" to listOf("b"))).asString()

        val cycle = put(identity("/teams/b/parents")).basicAuth(admin.first, admin.second).body(mapOf("parents" to listOf("a"))).asJacksonObject(JsonNode::class)

        assertThat(cycle.status).isEqualTo(CONFLICT.code)
        assertThat(cycle.body.get("message").asText()).contains("cycle")
    }

    @Test
    fun `the catalog lists the admin nodes and the wildcards`() {
        val catalog = get(identity("/nodes/catalog?prefix=ingot.admin.")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class)

        assertThat(catalog.status).isEqualTo(OK.code)
        assertThat(catalog.body.get("nodes").map { it.get("node").asText() }).contains("ingot.admin.users", "ingot.admin.*")
        assertThat(catalog.body.get("nodes").map { it.get("node").asText() }).noneMatch { !it.startsWith("ingot.admin.") }
    }

    @Test
    fun `the templates are served with their ids`() {
        val templates = get(identity("/nodes/templates")).basicAuth(admin.first, admin.second).asJacksonObject(JsonNode::class)

        assertThat(templates.body.get("templates").map { it.get("id").asText() })
            .containsExactly(
                "read", "deploy", "maintain", "admin", "instance-admin",
                "artifact-read", "artifact-deploy", "repository-artifact-deploy",
            )
    }

    @Test
    fun `the check explains the decision of a principal`() {
        post(identity("/principals")).basicAuth(admin.first, admin.second).body(mapOf("kind" to "MACHINE", "name" to "ci-bot")).asString()
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "deployers")).asString()
        put(identity("/teams/deployers/nodes")).basicAuth(admin.first, admin.second)
            .body(mapOf("nodes" to listOf(mapOf("node" to "ingot.repository.releases.deploy", "allowed" to true)))).asString()
        put(identity("/teams/deployers/members")).basicAuth(admin.first, admin.second).body(mapOf("members" to listOf("ci-bot"))).asString()

        val check = get(identity("/check?principal=ci-bot&node=ingot.repository.releases.deploy"))
            .basicAuth(admin.first, admin.second)
            .asJacksonObject(JsonNode::class)

        assertThat(check.status).isEqualTo(OK.code)
        assertThat(check.body.get("allowed").asBoolean()).isTrue
        assertThat(check.body.get("decision").get("source").asText()).isEqualTo("TEAM")
        assertThat(check.body.get("trace").map { it.get("matched").asBoolean() }).contains(true)
    }

    @Test
    fun `the check refuses a wildcard node with 400`() {
        val check = get(identity("/check?principal=ci-bot&node=ingot.repository.*.read")).basicAuth(admin.first, admin.second).asString()

        assertThat(check.status).isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `a caller without the admin node may check its own principal but not another`() {
        // given: a reader that may only read a route, and a second principal
        val (name, secret) = useAuth("reader", "reader-secret", routes = mapOf("/releases" to READ))
        post(identity("/principals")).basicAuth(admin.first, admin.second).body(mapOf("kind" to "MACHINE", "name" to "ci-bot")).asString()

        // when
        val other = get(identity("/check?principal=ci-bot&node=ingot.repository.releases.read")).basicAuth(name, secret).asString()

        // then
        assertThat(other.status).describedAs("another principal").isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `a repository-wide team grant is refused and points at the node endpoint`() {
        post(identity("/teams")).basicAuth(admin.first, admin.second).body(mapOf("name" to "deployers")).asString()

        val refused = put(identity("/teams/deployers/grants"))
            .basicAuth(admin.first, admin.second)
            .body(mapOf("grants" to listOf(mapOf("repository" to "releases", "role" to "DEPLOY"))))
            .asJacksonObject(JsonNode::class)

        assertThat(refused.status).isEqualTo(BAD_REQUEST.code)
        assertThat(refused.body.get("message").asText()).contains("nodes")
        assertThat(refused.body.has("problems")).describedAs("only a refused list carries problems").isFalse()
    }
}
