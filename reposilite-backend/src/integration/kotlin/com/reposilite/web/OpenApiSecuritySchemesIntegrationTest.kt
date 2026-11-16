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

package com.reposilite.web

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The served OpenAPI document declares the credential forms the API accepts: an access token as HTTP Basic, an access
 * token secret as a bearer value, and the browser session cookie. A route that needs a login lists them as alternatives,
 * so that a generated client knows which credentials to send. The bearer scheme describes an access token, never a session.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class OpenApiSecuritySchemesIntegrationTest : ReposiliteSpecification() {

    private fun openApiDocument(): JsonNode =
        Unirest.get("$base/openapi").asJacksonObject(JsonNode::class).body

    @Test
    fun `declares HTTP Basic and the bearer access token as security schemes`() {
        val schemes = openApiDocument().path("components").path("securitySchemes")

        assertThat(schemes.path("tokenBasic").path("type").asText())
            .describedAs("type of the tokenBasic security scheme")
            .isEqualTo("http")
        assertThat(schemes.path("tokenBasic").path("scheme").asText())
            .describedAs("scheme of the tokenBasic security scheme")
            .isEqualTo("basic")
        assertThat(schemes.path("sessionBearer").path("type").asText())
            .describedAs("type of the sessionBearer security scheme")
            .isEqualTo("http")
        assertThat(schemes.path("sessionBearer").path("scheme").asText())
            .describedAs("scheme of the sessionBearer security scheme")
            .isEqualTo("bearer")
    }

    @Test
    fun `declares the session cookie as an apiKey security scheme in the cookie ingot_session`() {
        val cookie = openApiDocument().path("components").path("securitySchemes").path("sessionCookie")

        assertThat(cookie.path("type").asText())
            .describedAs("type of the sessionCookie security scheme")
            .isEqualTo("apiKey")
        assertThat(cookie.path("in").asText())
            .describedAs("location of the sessionCookie security scheme")
            .isEqualTo("cookie")
        assertThat(cookie.path("name").asText())
            .describedAs("cookie name of the sessionCookie security scheme")
            .isEqualTo("ingot_session")
    }

    @Test
    fun `the bearer scheme describes an access token secret and not a session`() {
        val description = openApiDocument().path("components").path("securitySchemes").path("sessionBearer")
            .path("description").asText()

        assertThat(description)
            .describedAs("description of the sessionBearer security scheme")
            .contains("access token")
            .doesNotContainIgnoringCase("session")
    }

    @Test
    fun `a route that needs a login lists the token, bearer and cookie alternatives`() {
        // GET /api/identity/me/tokens answers only an authenticated caller
        val referenced = authenticatedRoutes(openApiDocument())["GET /api/identity/me/tokens"]

        assertThat(referenced)
            .describedAs("security schemes listed for GET /api/identity/me/tokens")
            .containsExactlyInAnyOrder("tokenBasic", "sessionBearer", "sessionCookie")
    }

    @Test
    fun `the security status route lists the token, bearer and cookie alternatives`() {
        // GET /api/identity/me/security is a read for a session or a Basic or Bearer caller, like the token list
        val referenced = authenticatedRoutes(openApiDocument())["GET /api/identity/me/security"]

        assertThat(referenced)
            .describedAs("security schemes listed for GET /api/identity/me/security")
            .containsExactlyInAnyOrder("tokenBasic", "sessionBearer", "sessionCookie")
    }

    @Test
    fun `every route that needs a login lists the session cookie`() {
        val routesWithoutCookie = authenticatedRoutes(openApiDocument()).filterValues { "sessionCookie" !in it }.keys

        assertThat(routesWithoutCookie)
            .describedAs("routes that need a login but do not list the sessionCookie scheme")
            .isEmpty()
    }

    @Test
    fun `every route that needs a login still lists the bearer access token`() {
        val routesWithoutBearer = authenticatedRoutes(openApiDocument())
            .filterKeys { it !in SESSION_ONLY_ROUTES }
            .filterValues { "sessionBearer" !in it }.keys

        assertThat(routesWithoutBearer)
            .describedAs("routes that need a login but no longer list the sessionBearer scheme")
            .isEmpty()
    }

    @Test
    fun `the session-only factor routes list the session cookie and no other scheme`() {
        val routes = authenticatedRoutes(openApiDocument())

        assertThat(routes.keys)
            .describedAs("session-only factor routes in the served document")
            .containsAll(SESSION_ONLY_ROUTES)
        SESSION_ONLY_ROUTES.forEach { route ->
            assertThat(routes[route])
                .describedAs("security schemes listed for $route")
                .containsExactly("sessionCookie")
        }
    }

    /** Every operation that declares a security requirement, with the names of the schemes it lists. */
    private fun authenticatedRoutes(document: JsonNode): Map<String, Set<String>> =
        document.path("paths").fields().asSequence().flatMap { (path, item) ->
            item.fields().asSequence()
                .filter { (method, _) -> method in HTTP_METHODS }
                .map { (method, operation) -> "${method.uppercase()} $path" to securitySchemesOf(operation) }
        }
            .filter { (_, schemes) -> schemes.isNotEmpty() }
            .toMap()

    private fun securitySchemesOf(operation: JsonNode): Set<String> =
        operation.path("security")
            .flatMap { requirement -> requirement.fieldNames().asSequence().toList() }
            .toSet()

    private companion object {
        val HTTP_METHODS = setOf("get", "put", "post", "delete", "patch", "head", "options")

        /**
         * The second-factor routes manage the caller's own factors and accept only the browser session, on purpose.
         * The handler refuses an access token even of the same user, so these routes are exempt from the bearer check.
         * SecondFactorIntegrationTest asserts that refusal. They must list the session cookie and nothing else, which
         * the second test checks, so the exemption cannot hide a route that forgot its schemes.
         */
        val SESSION_ONLY_ROUTES = setOf(
            "GET /api/identity/me/factors",
            "POST /api/identity/me/factors/totp",
            "POST /api/identity/me/factors/totp/confirm",
            "PATCH /api/identity/me/factors/{id}",
            "DELETE /api/identity/me/factors/{id}",
            "POST /api/identity/me/recovery-codes",
            "POST /api/identity/me/factors/passkey/options",
            "POST /api/identity/me/factors/passkey",
            "POST /api/identity/me/step-up",
        )
    }

}
