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

package com.reposilite.identity

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.GetRequest
import kong.unirest.core.HttpRequestWithBody
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.api.CreatedTokenDto
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.api.LoginResponse
import net.onelitefeather.ingot.identity.login.oidc.OidcProviderSettings
import net.onelitefeather.ingot.identity.login.oidc.OidcTestIdentityProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock

private const val FETCH_METADATA_PROPERTY = "ingot.fetchMetadata"
private const val PASSWORD = "correct horse battery"

/**
 * A browser session of a page on another site must not reach the api with the cookies of the person. The same
 * requests from the dashboard's own origin, token clients and the redirect of an identity provider keep working.
 */
internal abstract class FetchMetadataSpecification : ReposiliteSpecification() {

    private var previousProperty: String? = null
    private var propertySet = false

    /** A client without cookie management, so the test decides which cookies a request carries. */
    protected val http = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }

    /** Sets the system property read when the instance starts, and restores the previous value after the test. */
    protected fun useFetchMetadataProperty(value: String) {
        previousProperty = System.getProperty(FETCH_METADATA_PROPERTY)
        propertySet = true
        System.setProperty(FETCH_METADATA_PROPERTY, value)
    }

    @AfterEach
    fun restoreFetchMetadataProperty() {
        if (propertySet) {
            if (previousProperty == null) System.clearProperty(FETCH_METADATA_PROPERTY) else System.setProperty(FETCH_METADATA_PROPERTY, previousProperty)
            propertySet = false
        }
    }

    /** The cookie values of one browser session, as the login response sets them. */
    protected data class Session(val session: String, val refresh: String, val csrf: String)

    protected fun loginSession(username: String): Session {
        useFacade<LoginFacade>().createUser(username, username, PASSWORD)
        val headers = http.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body("""{"provider":"local","username":"$username","password":"$PASSWORD"}""")
            .asJacksonObject(LoginResponse::class)
            .headers
            .get("Set-Cookie")
        return Session(
            session = cookieValue(headers, "ingot_session"),
            refresh = cookieValue(headers, "ingot_refresh"),
            csrf = cookieValue(headers, "ingot_csrf"),
        )
    }

    private fun cookieValue(headers: List<String>, name: String): String =
        headers.single { it.startsWith("$name=") }.substringAfter("=").substringBefore(";")

    /** The cookies of the session, as a browser sends them to the api. */
    protected fun sessionCookies(session: Session): String =
        "ingot_session=${session.session}; ingot_csrf=${session.csrf}"

    /** Headers a browser attaches to a `fetch` from [site]: the fetch metadata, and the cookies when given. */
    protected fun fetchHeaders(
        site: String?,
        mode: String? = "cors",
        dest: String? = "empty",
        cookie: String? = null,
        csrf: String? = null,
    ): Map<String, String> = buildMap {
        site?.let { put("Sec-Fetch-Site", it) }
        mode?.let { put("Sec-Fetch-Mode", it) }
        dest?.let { put("Sec-Fetch-Dest", it) }
        cookie?.let { put("Cookie", it) }
        csrf?.let { put("X-CSRF-Token", it) }
    }

    protected fun GetRequest.withHeaders(headers: Map<String, String>): GetRequest =
        apply { headers.forEach { (name, value) -> header(name, value) } }

    protected fun HttpRequestWithBody.withHeaders(headers: Map<String, String>): HttpRequestWithBody =
        apply { headers.forEach { (name, value) -> header(name, value) } }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class FetchMetadataIntegrationTest : FetchMetadataSpecification() {

    private val browser: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    @Test
    fun `refuses a cross-site read of the api that carries the session cookie`() {
        // given: a signed-in user
        val session = loginSession("alice-fetch")

        // when: a page on another site fetches the current user with the cookie of the person
        val response = http.get("$base/api/auth/me")
            .withHeaders(fetchHeaders("cross-site", cookie = "ingot_session=${session.session}"))
            .asString()

        // then: the request is refused with the error body of the api, and no user is served
        assertThat(response.status).describedAs("status, body: ${response.body}").isEqualTo(FORBIDDEN.code)
        assertThat(response.body).describedAs("error body").contains("\"status\":403").doesNotContain("alice-fetch")
    }

    @Test
    fun `serves the same read from the dashboard on its own origin`() {
        // given: a signed-in user
        val session = loginSession("bob-fetch")

        // when: the dashboard fetches the current user from its own origin
        val response = http.get("$base/api/auth/me")
            .withHeaders(fetchHeaders("same-origin", cookie = "ingot_session=${session.session}"))
            .asString()

        // then: the request is served as that user
        assertThat(response.status).describedAs("status").isEqualTo(OK.code)
        assertThat(response.body).contains("bob-fetch")
    }

    @Test
    fun `refuses a change from a sibling subdomain even with a matching csrf header`() {
        // given: a signed-in user, and a sibling subdomain that knows the csrf value
        val session = loginSession("carol-fetch")
        val sibling = fetchHeaders(
            "same-site",
            cookie = sessionCookies(session),
            csrf = session.csrf,
        ) + ("Content-Type" to "application/json")

        // when: the sibling subdomain creates a token for the person
        val response = http.post("$base/api/identity/me/tokens")
            .withHeaders(sibling)
            .body("""{"name":"sibling-token"}""")
            .asString()

        // then: the request is refused, and the token does not exist
        assertThat(response.status).describedAs("status, body: ${response.body}").isEqualTo(FORBIDDEN.code)
        assertThat(response.body).describedAs("error body").contains("\"status\":403")
        val tokens = http.get("$base/api/identity/me/tokens")
            .withHeaders(fetchHeaders("same-origin", cookie = sessionCookies(session)))
            .asString()
        assertThat(tokens.body).describedAs("token list").doesNotContain("sibling-token")
    }

    @Test
    fun `does not refuse a bearer token request from another site`() {
        // given: a token of a signed-in user
        val session = loginSession("dave-fetch")
        val secret = http.post("$base/api/identity/me/tokens")
            .withHeaders(fetchHeaders("same-origin", cookie = sessionCookies(session), csrf = session.csrf) + ("Content-Type" to "application/json"))
            .body("""{"name":"ci"}""")
            .asJacksonObject(CreatedTokenDto::class)
            .body
            .secret

        // when: a client on another site sends the token, and the session cookie is attached as well
        val response = http.get("$base/api/auth/me")
            .withHeaders(fetchHeaders("cross-site", cookie = "ingot_session=${session.session}") + ("Authorization" to "Bearer $secret"))
            .asString()

        // then: the token authenticates the request, which is not refused by the fetch metadata rule
        assertThat(response.status).describedAs("bearer body: ${response.body}").isEqualTo(OK.code)
        assertThat(response.body).contains("dave-fetch")
    }

    @Test
    fun `lets an identity provider send the browser back to the callback as a top-level navigation`() {
        // given: an identity provider, a corporate login configured for it, and a person who is signed in already
        val provider = OidcTestIdentityProvider(Clock.systemUTC()).start()
        try {
            useFacade<SharedConfigurationFacade>()
                .getDomainSettings<LoginSettings>()
                .update { old ->
                    old.copy(
                        oidcProviders = listOf(
                            OidcProviderSettings(
                                id = "corp",
                                enabled = true,
                                label = "Corporate SSO",
                                issuerUrl = provider.issuer,
                                clientId = OidcTestIdentityProvider.CLIENT_ID,
                                clientSecret = OidcTestIdentityProvider.CLIENT_SECRET,
                            ),
                        ),
                    )
                }
            val session = loginSession("erin-fetch")

            // when: the login starts, the provider answers, and the browser is navigated back with the session cookie
            val start = browser.send(
                HttpRequest.newBuilder(URI("$base/api/auth/oidc/corp/login?redirect=${URLEncoder.encode("/repositories", "UTF-8")}")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            val query = URI(start.headers().firstValue("Location").orElseThrow()).rawQuery.split('&').associate {
                it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }
            provider.nonce = query["nonce"]
            val stateCookie = start.headers().allValues("Set-Cookie").first { it.startsWith("ingot_oidc=") }
                .substringAfter('=').substringBefore(';')
            val callback = browser.send(
                HttpRequest.newBuilder(URI("$base/api/auth/oidc/corp/callback?code=authorization-code&state=${query["state"]}"))
                    .header("Cookie", "ingot_oidc=$stateCookie; ingot_session=${session.session}")
                    .header("Sec-Fetch-Site", "cross-site")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Dest", "document")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

            // then: the callback is handled and the browser is sent on, and the request is not refused
            assertThat(callback.statusCode()).describedAs("callback status").isEqualTo(302)
            assertThat(callback.headers().firstValue("Location").orElse(null)).describedAs("callback location").isEqualTo("/repositories")
        } finally {
            provider.close()
        }
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class FetchMetadataSwitchedOffIntegrationTest : FetchMetadataSpecification() {

    override fun overrideParameters(parameters: ReposiliteParameters) {
        useFetchMetadataProperty("off")
    }

    @Test
    fun `lets a cross-site read with the session cookie through when the operator switches the rule off`() {
        // given: a signed-in user and the switch set to off
        val session = loginSession("frank-fetch")

        // when: a page on another site fetches the current user with the cookie of the person
        val response = http.get("$base/api/auth/me")
            .withHeaders(fetchHeaders("cross-site", cookie = "ingot_session=${session.session}"))
            .asString()

        // then: the request is served as before the rule existed
        assertThat(response.status).describedAs("status").isEqualTo(OK.code)
        assertThat(response.body).contains("frank-fetch")
    }

}
