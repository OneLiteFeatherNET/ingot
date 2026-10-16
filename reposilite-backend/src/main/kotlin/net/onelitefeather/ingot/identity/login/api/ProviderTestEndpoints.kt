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

package net.onelitefeather.ingot.identity.login.api

import com.reposilite.shared.errorResponse
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.ldap.LdapConnectionCheck
import net.onelitefeather.ingot.identity.login.ldap.LdapConnectionReport
import net.onelitefeather.ingot.identity.login.oidc.OidcConnectionCheck
import net.onelitefeather.ingot.identity.login.oidc.OidcConnectionReport
import panda.std.asSuccess
import panda.std.reactive.Reference

/** The body of the LDAP connection test. Both fields are optional; a password is used for one bind and never echoed. */
data class LdapTestRequest(
    val username: String? = null,
    val password: String? = null,
)

/**
 * The connection tests of the external login providers, for instance administrators. They read the current settings, reach
 * the provider or the directory, and answer with what they found. Nothing is stored, and no secret is ever part of an answer.
 */
internal class ProviderTestEndpoints(
    private val oidc: OidcConnectionCheck,
    private val loginSettings: Reference<LoginSettings>,
) : ReposiliteRoutes() {

    @OpenApi(
        path = "/api/auth/providers/oidc/{id}/test",
        methods = [HttpMethod.POST],
        summary = "Test an OpenID Connect provider",
        description = "Instance admins only. Reads the discovery document and the key set again and reports the endpoints",
        pathParams = [OpenApiParam(name = "id", description = "Id of the provider", required = true)],
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = OidcConnectionReport::class)]),
            OpenApiResponse(status = "404", description = "No enabled provider has this id"),
        ]
    )
    private val testOidc = ReposiliteRoute<OidcConnectionReport>("/api/auth/providers/oidc/{id}/test", Route.POST) {
        managerOnly {
            val id = requireParameter("id")
            response = oidc.check(id)?.asSuccess() ?: errorResponse(NOT_FOUND, "Unknown login provider")
        }
    }

    @OpenApi(
        path = "/api/auth/providers/ldap/test",
        methods = [HttpMethod.POST],
        summary = "Test the LDAP login",
        description = "Instance admins only. Binds with the service account and, when a user name is given, looks the user up, binds as the user with the password and reads the groups",
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = LdapTestRequest::class)]),
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = LdapConnectionReport::class)]),
        ]
    )
    private val testLdap = ReposiliteRoute<LdapConnectionReport>("/api/auth/providers/ldap/test", Route.POST) {
        managerOnly {
            val request = runCatching { body<LdapTestRequest>() }.getOrNull() ?: LdapTestRequest()
            response = LdapConnectionCheck(loginSettings.get().ldap)
                .check(request.username, request.password)
                .asSuccess()
        }
    }

    override val routes = routes(testOidc, testLdap)

}
