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

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.storage.api.toLocation
import com.reposilite.token.RoutePermission.WRITE
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * An upload is authorized on the same path it is stored at. A spelling of a path that the server decodes to another
 * location (an escaped character, a dot segment, a doubled slash) must not be judged by its raw form while the decoded
 * form is written. Requests are sent with the raw URI as written, so no client normalizes them first.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RepositoryPathAuthorizationIntegrationTest : ReposiliteSpecification() {

    private val admin: Pair<String, String>
        get() = useDefaultManagementToken()

    private val client: HttpClient = HttpClient.newHttpClient()

    private fun principalWithNodes(name: String, vararg nodes: Pair<String, Boolean>): Pair<String, String> {
        post("$base/api/identity/principals").basicAuth(admin.first, admin.second).body(mapOf("kind" to "MACHINE", "name" to name)).asString()
        put("$base/api/identity/principals/$name/nodes").basicAuth(admin.first, admin.second)
            .body(mapOf("nodes" to nodes.map { (node, allowed) -> mapOf("node" to node, "allowed" to allowed) })).asString()
        val secret = "$name-secret"
        post("$base/api/identity/principals/$name/tokens").basicAuth(admin.first, admin.second)
            .body(mapOf("name" to name, "secret" to secret)).asString()
        return name to secret
    }

    /** Sends a PUT with [rawPath] exactly as written, and answers its status. */
    private fun rawPut(credentials: Pair<String, String>, rawPath: String): Int {
        val authorization = Base64.getEncoder().encodeToString("${credentials.first}:${credentials.second}".toByteArray())
        val request = HttpRequest.newBuilder(URI.create("$base$rawPath"))
            .header("Authorization", "Basic $authorization")
            .PUT(HttpRequest.BodyPublishers.ofString("content"))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    private fun stored(repository: String, gav: String): Boolean =
        mavenFacade.getRepository(repository)!!.storageProvider.exists(gav.toLocation())

    @Test
    fun `an escaped character does not deploy into an artifact the principal is denied`() {
        val alice = principalWithNodes(
            "alice",
            "ingot.repository.releases.deploy" to true,
            "ingot.artifact.net.onelitefeather:mycelium-bom.deploy" to false,
        )

        val status = rawPut(alice, "/releases/net/onelite%66eather/mycelium-bom/1.0/mycelium-bom-1.0.jar")

        assertThat(status).describedAs("status of the escaped spelling").isEqualTo(FORBIDDEN.code)
        assertThat(stored("releases", "net/onelitefeather/mycelium-bom/1.0/mycelium-bom-1.0.jar"))
            .describedAs("denied artifact written through an escaped spelling, status $status").isFalse
    }

    @Test
    fun `an escaped repository name does not deploy into a repository the principal is denied`() {
        val alice = principalWithNodes(
            "alice",
            "ingot.repository.*.deploy" to true,
            "ingot.repository.releases.deploy" to false,
        )

        val status = rawPut(alice, "/rel%65ases/com/acme/lib/1.0/lib-1.0.jar")

        assertThat(status).describedAs("status of the escaped name").isEqualTo(FORBIDDEN.code)
        assertThat(stored("releases", "com/acme/lib/1.0/lib-1.0.jar"))
            .describedAs("denied repository written through an escaped name, status $status").isFalse
    }

    @Test
    fun `a doubled slash does not deploy into an artifact the principal is denied`() {
        val alice = principalWithNodes(
            "alice",
            "ingot.repository.releases.deploy" to true,
            "ingot.artifact.net.onelitefeather:mycelium-bom.deploy" to false,
        )

        val status = rawPut(alice, "/releases/net//onelitefeather/mycelium-bom/1.0/mycelium-bom-1.0.jar")

        assertThat(stored("releases", "net/onelitefeather/mycelium-bom/1.0/mycelium-bom-1.0.jar"))
            .describedAs("denied artifact written through a doubled slash, status $status").isFalse
    }

    @Test
    fun `an escaped dot segment does not leave the route of a legacy token`() {
        val token = useAuth("acme", "acme-secret", routes = mapOf("/releases/com/acme" to WRITE))

        val status = rawPut(token, "/releases/com/acme/%2e%2e/other/lib/1.0/lib-1.0.jar")

        assertThat(stored("releases", "com/other/lib/1.0/lib-1.0.jar")).describedAs("written outside the route, status $status").isFalse
    }

    @Test
    fun `an escaped slash does not leave the route of a legacy token`() {
        val token = useAuth("acme", "acme-secret", routes = mapOf("/releases/com/acme" to WRITE))

        val status = rawPut(token, "/releases/com/acme%2F..%2Fother/lib/1.0/lib-1.0.jar")

        assertThat(stored("releases", "com/other/lib/1.0/lib-1.0.jar")).describedAs("written outside the route, status $status").isFalse
    }

    @Test
    fun `a doubly escaped slash does not leave the route of a legacy token`() {
        val token = useAuth("acme", "acme-secret", routes = mapOf("/releases/com/acme" to WRITE))

        val status = rawPut(token, "/releases/com/acme%252F..%252Fother/lib/1.0/lib-1.0.jar")

        assertThat(stored("releases", "com/other/lib/1.0/lib-1.0.jar")).describedAs("written outside the route, status $status").isFalse
    }

    @Test
    fun `a backslash does not leave the route of a legacy token`() {
        val token = useAuth("acme", "acme-secret", routes = mapOf("/releases/com/acme" to WRITE))

        val status = rawPut(token, "/releases/com/acme%5C..%5Cother/lib/1.0/lib-1.0.jar")

        assertThat(stored("releases", "com/other/lib/1.0/lib-1.0.jar")).describedAs("written outside the route, status $status").isFalse
    }

    @Test
    fun `an escaped repository name does not leave the route of a legacy token`() {
        val token = useAuth("acme", "acme-secret", routes = mapOf("/snapshots" to WRITE))

        val status = rawPut(token, "/snapshots%2F..%2Freleases/com/acme/lib/1.0/lib-1.0.jar")

        assertThat(stored("releases", "com/acme/lib/1.0/lib-1.0.jar")).describedAs("written into another repository, status $status").isFalse
    }

    @Test
    fun `an escaped spelling of an allowed path deploys to its decoded location`() {
        val token = useAuth("acme", "acme-secret", routes = mapOf("/releases/com/acme" to WRITE))

        val status = rawPut(token, "/releases/com/ac%6De/lib/1.0/lib-1.0.jar")

        assertThat(status).describedAs("status of the escaped spelling").isEqualTo(OK.code)
        assertThat(stored("releases", "com/acme/lib/1.0/lib-1.0.jar")).describedAs("written at the decoded location").isTrue
    }

    @Test
    fun `a plus sign in a version stays a plus sign`() {
        val token = useAuth("acme", "acme-secret", routes = mapOf("/releases/com/acme" to WRITE))

        val status = rawPut(token, "/releases/com/acme/lib/1.0+build/lib-1.0+build.jar")

        assertThat(status).describedAs("status of a version with a plus sign").isEqualTo(OK.code)
        assertThat(stored("releases", "com/acme/lib/1.0+build/lib-1.0+build.jar")).describedAs("written with the plus sign").isTrue
    }

}
