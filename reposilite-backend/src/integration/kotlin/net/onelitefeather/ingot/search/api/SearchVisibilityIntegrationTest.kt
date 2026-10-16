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

package net.onelitefeather.ingot.search.api

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.RepositoryVisibility
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.token.RoutePermission.READ
import io.javalin.Javalin
import kong.unirest.core.HttpRequest
import kong.unirest.core.Unirest.delete
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.patch
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import net.onelitefeather.ingot.search.application.SearchFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The visibility matrix of artifact search (artifact search spec, 10.4), through the REST API. Every test boots its own
 * instance and builds its own fixture: a PUBLIC repository, a HIDDEN one, a PRIVATE one and two proxies that cache through
 * an upstream. The outbox is drained by explicit ticks; nothing waits for the poller.
 *
 * The rows are identified by artifactId, which is unique in the fixture: `app` and `shared` in `releases` (PUBLIC), `secret`
 * in `auditlog` (HIDDEN), `vault`, `ledger`, `twin` (com/examples), `tool` (org/other) and `upper` (com/EXAMPLE) in
 * `secrets` (PRIVATE), and the cached copies `cached` in `publiccache` and `cachedvault` in `vaultproxy`.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SearchVisibilityIntegrationTest : ReposiliteSpecification() {

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + listOf(
                    RepositorySettings(id = "auditlog", visibility = RepositoryVisibility.HIDDEN),
                    RepositorySettings(id = "secrets", visibility = RepositoryVisibility.PRIVATE),
                    RepositorySettings(
                        id = "vaultproxy",
                        visibility = RepositoryVisibility.PRIVATE,
                        proxied = listOf(MirroredRepositorySettings(reference = upstreamReference(UNSTARTED_UPSTREAM_PORT), store = true)),
                    ),
                    RepositorySettings(
                        id = "publiccache",
                        proxied = listOf(MirroredRepositorySettings(reference = upstreamReference(UNSTARTED_UPSTREAM_PORT), store = true)),
                    ),
                )
            )
        }
    }

    private val manager: Pair<String, String>
        get() = useDefaultManagementToken()

    private val search: SearchFacade
        get() = useFacade()

    private fun identity(path: String): String = "$base/api/identity$path"

    /**
     * Deploys the fixture of the matrix and drains the outbox, so the index holds every row before the first search. The
     * background poller is paused first: the outbox then moves only by the explicit ticks of [drainOutbox].
     */
    private fun deployFixture() {
        search.admin!!.pollingPaused = true
        useDocument("releases", "com/example/app/1.0.0", "app-1.0.0.jar", store = true)
        useDocument("releases", "com/examples/shared/1.0.0", "shared-1.0.0.jar", store = true)
        useDocument("auditlog", "com/example/secret/1.0.0", "secret-1.0.0.jar", store = true)
        useDocument("secrets", "com/example/vault/1.0.0", "vault-1.0.0.jar", store = true)
        useDocument("secrets", "com/example/ledger/1.0.0", "ledger-1.0.0.jar", store = true)
        useDocument("secrets", "com/examples/twin/1.0.0", "twin-1.0.0.jar", store = true)
        useDocument("secrets", "org/other/tool/1.0.0", "tool-1.0.0.jar", store = true)
        useDocument("secrets", "com/EXAMPLE/upper/1.0.0", "upper-1.0.0.jar", store = true)
        drainOutbox()
    }

    /** Ticks the outbox until a tick claims nothing. The bound turns a stuck entry into a failure instead of a hang. */
    private fun drainOutbox() {
        var ticks = 0
        while (search.admin!!.drainOutbox().claimed > 0) {
            check(++ticks < MAX_DRAIN_TICKS) { "the outbox did not drain in $MAX_DRAIN_TICKS ticks" }
        }
    }

    private fun search(credentials: Pair<String, String>? = null, query: String = ""): JsonNode {
        val request: HttpRequest<*> = get("$base/api/maven/search$query")
        val response = withCredentials(request, credentials).asJacksonObject(JsonNode::class)
        assertThat(response.status).describedAs("search $query").isEqualTo(200)
        return response.body
    }

    private fun artifactIds(body: JsonNode): Set<String> =
        body.get("items").map { it.get("artifactId").asText() }.toSet()

    private fun withCredentials(request: HttpRequest<*>, credentials: Pair<String, String>?): HttpRequest<*> =
        credentials?.let { request.basicAuth(it.first, it.second) } ?: request

    /** Creates a machine principal that holds [nodes] directly, with a token of its own, and returns the token. */
    private fun principal(name: String, vararg nodes: Pair<String, Boolean>): Pair<String, String> {
        val created = post(identity("/principals")).basicAuth(manager.first, manager.second)
            .body(mapOf("kind" to "MACHINE", "name" to name)).asString()
        assertThat(created.status).describedAs("principal $name").isEqualTo(200)
        if (nodes.isNotEmpty()) {
            put(identity("/principals/$name/nodes")).basicAuth(manager.first, manager.second)
                .body(mapOf("nodes" to nodes.map { (node, allowed) -> mapOf("node" to node, "allowed" to allowed) })).asString()
        }
        return token(name, tokenName = name, secret = "$name-secret")
    }

    /** A token of [principal], narrowed to [nodes] when any are given. */
    private fun token(principal: String, tokenName: String, secret: String, vararg nodes: Pair<String, Boolean>): Pair<String, String> {
        val body = mutableMapOf<String, Any>("name" to tokenName, "secret" to secret)
        if (nodes.isNotEmpty()) body["nodes"] = nodes.map { (node, allowed) -> mapOf("node" to node, "allowed" to allowed) }
        val created = post(identity("/principals/$principal/tokens")).basicAuth(manager.first, manager.second).body(body).asString()
        assertThat(created.status).describedAs("token $tokenName").isEqualTo(200)
        return tokenName to secret
    }

    @Test
    fun `anonymous callers see the public repository only, and the facets count only those rows`() {
        deployFixture()

        val body = search()

        assertThat(artifactIds(body)).containsExactlyInAnyOrder("app", "shared")
        assertThat(body.get("facets").get("repository").map { it.get("value").asText() }).containsExactly("releases")
        assertThat(body.get("total").asLong()).isEqualTo(2)
    }

    @Test
    fun `a reader with READ on a PRIVATE repository sees its rows and not the HIDDEN one`() {
        deployFixture()
        val reader = principal("reader", "ingot.repository.secrets.read" to true)

        val body = search(reader)

        assertThat(artifactIds(body)).contains("app", "vault", "ledger", "twin", "tool", "upper")
        assertThat(artifactIds(body)).doesNotContain("secret")
    }

    @Test
    fun `a team grant on a path shows only the granted subtree`() {
        deployFixture()
        principal("member")
        post(identity("/teams")).basicAuth(manager.first, manager.second).body(mapOf("name" to "vault-team")).asString()
        put(identity("/teams/vault-team/members")).basicAuth(manager.first, manager.second).body(mapOf("members" to listOf("member"))).asString()
        put(identity("/teams/vault-team/grants")).basicAuth(manager.first, manager.second)
            .body(mapOf("grants" to listOf(mapOf("repository" to "secrets", "pathPrefix" to "com/example", "role" to "READ")))).asString()
        val member = token("member", tokenName = "member-token", secret = "member-secret")

        val body = search(member)

        assertThat(artifactIds(body)).contains("vault", "ledger")
        assertThat(artifactIds(body)).describedAs("the sibling com/examples").doesNotContain("twin")
        assertThat(artifactIds(body)).describedAs("another group").doesNotContain("tool", "upper")
    }

    @Test
    fun `a legacy route token sees the prefix rows, the case variants and the segment quirk of the legacy model`() {
        deployFixture()
        val legacy = useAuth("legacy-token", "legacy-secret", routes = mapOf("/secrets/com/ex" to READ))

        val body = search(legacy)

        assertThat(artifactIds(body)).contains("vault", "ledger", "twin", "upper")
        assertThat(artifactIds(body)).describedAs("another group").doesNotContain("tool")
    }

    @Test
    fun `a legacy route for the root sees every row, including the HIDDEN repository`() {
        deployFixture()
        val root = useAuth("root-token", "root-secret", routes = mapOf("/" to READ))

        val body = search(root)

        assertThat(artifactIds(body)).containsExactlyInAnyOrder("app", "shared", "secret", "vault", "ledger", "twin", "tool", "upper")
    }

    @Test
    fun `a HIDDEN repository is visible to a READ holder and not to anonymous callers`() {
        deployFixture()
        val auditor = principal("auditor", "ingot.repository.auditlog.read" to true)

        assertThat(artifactIds(search(auditor))).contains("secret")
        assertThat(artifactIds(search())).doesNotContain("secret")
    }

    @Test
    fun `a token narrowed to one artifact sees that artifact only`() {
        deployFixture()
        principal("alice", "ingot.repository.secrets.read" to true)
        val narrowed = token("alice", tokenName = "alice-vault", secret = "alice-vault-secret", "ingot.artifact.com.example:vault.read" to true)

        val body = search(narrowed)

        // The PUBLIC repository is visible to every token (5.1); the narrowed token adds only the one artifact.
        assertThat(artifactIds(body)).containsExactlyInAnyOrder("app", "shared", "vault")
    }

    @Test
    fun `an artifact node that denies one artifact hides it from a reader of the repository`() {
        deployFixture()
        principal(
            "bob",
            "ingot.repository.secrets.read" to true,
            "ingot.artifact.com.example:vault.read" to false,
        )
        val bob = token("bob", tokenName = "bob-token", secret = "bob-secret")

        val body = search(bob)

        assertThat(artifactIds(body)).describedAs("the denied artifact").doesNotContain("vault")
        assertThat(artifactIds(body)).describedAs("the rest of the repository").contains("ledger", "twin")
    }

    @Test
    fun `a token without READ sees the public repository only`() {
        deployFixture()
        principal("carol", "ingot.repository.secrets.read" to true)
        val deployOnly = token("carol", tokenName = "carol-deploy", secret = "carol-deploy-secret", "ingot.repository.secrets.deploy" to true)

        val body = search(deployOnly)

        assertThat(artifactIds(body)).containsExactlyInAnyOrder("app", "shared")
    }

    @Test
    fun `a disabled principal is treated as anonymous over HTTP`() {
        deployFixture()
        val dave = principal("dave", "ingot.repository.secrets.read" to true)
        assertThat(artifactIds(search(dave))).contains("vault")
        val disabled = patch(identity("/principals/dave")).basicAuth(manager.first, manager.second).body(mapOf("disabled" to true)).asString()
        assertThat(disabled.status).isEqualTo(200)

        val body = search(dave)

        assertThat(artifactIds(body)).containsExactlyInAnyOrder("app", "shared")
    }

    @Test
    fun `an instance admin sees every row`() {
        deployFixture()
        principal("root")
        put(identity("/principals/root/instance-admin")).basicAuth(manager.first, manager.second).asString()
        val root = token("root", tokenName = "root-token", secret = "root-token-secret")

        val body = search(root)

        assertThat(artifactIds(body)).containsExactlyInAnyOrder("app", "shared", "secret", "vault", "ledger", "twin", "tool", "upper")
    }

    @Test
    fun `a deployed artifact is found after the outbox tick`() {
        deployFixture()
        val deployed = put("$base/releases/com/example/fresh/1.0.0/fresh-1.0.0.jar").basicAuth(manager.first, manager.second).body("content").asString()
        assertThat(deployed.status).isEqualTo(200)

        drainOutbox()

        assertThat(artifactIds(search(query = "?artifactId=fresh"))).containsExactly("fresh")
    }

    @Test
    fun `a deleted artifact is removed from the search after the outbox tick`() {
        deployFixture()
        assertThat(artifactIds(search(query = "?artifactId=app"))).containsExactly("app")

        val deleted = delete("$base/releases/com/example/app/1.0.0").basicAuth(manager.first, manager.second).asString()
        assertThat(deleted.isSuccess).isTrue
        drainOutbox()

        assertThat(artifactIds(search(query = "?artifactId=app"))).isEmpty()
    }

    @Test
    fun `a cached copy of a public proxy is found and marked with its source host`() {
        deployFixture()
        val upstreamHost = cacheThroughProxy("publiccache", "com/reposilite/cached/1.0.0/cached-1.0.0.jar")
        drainOutbox()

        val body = search(query = "?artifactId=cached")

        val item = body.get("items").single()
        assertThat(item.get("version").asText()).isEqualTo("1.0.0")
        assertThat(item.get("repositories").single().get("name").asText()).isEqualTo("publiccache")
        assertThat(item.get("repositories").single().get("origin").asText()).isEqualTo("MIRROR")
        val files = get("$base/api/maven/search/files?groupId=com.reposilite&artifactId=cached&version=1.0.0").asJacksonObject(JsonNode::class).body
        assertThat(files.get("items").single().get("origin").asText()).isEqualTo("MIRROR")
        assertThat(files.get("items").single().get("sourceHost").asText()).isEqualTo(upstreamHost)
    }

    @Test
    fun `a cached copy of a PRIVATE proxy is visible exactly like a hosted row of that repository`() {
        deployFixture()
        cacheThroughProxy("vaultproxy", "com/reposilite/cachedvault/1.0.0/cachedvault-1.0.0.jar", credentials = manager)
        drainOutbox()
        val vaultReader = principal("vaultreader", "ingot.repository.vaultproxy.read" to true)

        assertThat(artifactIds(search(vaultReader))).contains("cachedvault")
        assertThat(artifactIds(search())).doesNotContain("cachedvault")
    }

    /**
     * Fetches [gav] through the proxy [repository] from a stub upstream, so the copy is stored and recorded with the upstream
     * host, which is returned as the index must show it: the host, and the port when it is not the default (2.4). The request
     * is synchronous: when it returns, the copy exists. A PRIVATE proxy is read with [credentials].
     */
    private fun cacheThroughProxy(repository: String, gav: String, credentials: Pair<String, String>? = null): String {
        val content = "cached-content"
        val started = CountDownLatch(1)
        val upstream = Javalin.start { config ->
            config.jetty.port = 0
            config.events.serverStarted { started.countDown() }
            config.routes.head("/releases/$gav") { ctx ->
                ctx.contentType("application/java-archive").header("Content-Length", content.length.toString()).status(200)
            }
            config.routes.get("/releases/$gav") { ctx -> ctx.result(content) }
        }
        assertThat(started.await(UPSTREAM_START_SECONDS, TimeUnit.SECONDS)).isTrue
        useUpstreamPort(upstream.port())
        try {
            val response = withCredentials(get("$base/$repository/$gav"), credentials).asString()
            assertThat(response.isSuccess).describedAs("cached through $repository").isTrue
            return "localhost:${upstream.port()}"
        } finally {
            upstream.stop()
        }
    }

    private companion object {
        const val MAX_DRAIN_TICKS = 100
        const val UPSTREAM_START_SECONDS = 10L
    }

}
