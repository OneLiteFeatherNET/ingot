@file:Suppress("FunctionName")

package com.reposilite.identity

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.OK
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.oidc.OidcProviderSettings
import net.onelitefeather.ingot.identity.login.oidc.OidcTestIdentityProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock

/**
 * The OpenID Connect login over HTTP: the redirect to the provider, the callback with an ID token from an in-process
 * identity provider, the browser session it starts, and the redirects that refuse an unsafe target. Every test runs on a
 * fresh instance with a fresh database, and the provider runs on a loopback port of this process.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ExternalLoginIntegrationTest : ReposiliteSpecification() {

    private val provider = OidcTestIdentityProvider(Clock.systemUTC())
    private val browser: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    @BeforeEach
    fun startProvider() {
        provider.start()
    }

    @AfterEach
    fun stopProvider() {
        provider.close()
    }

    private fun configureCorporateProvider(enabled: Boolean = true) {
        useFacade<SharedConfigurationFacade>()
            .getDomainSettings<LoginSettings>()
            .update { old ->
                old.copy(
                    oidcProviders = listOf(
                        OidcProviderSettings(
                            id = "corp",
                            enabled = enabled,
                            label = "Corporate SSO",
                            issuerUrl = provider.issuer,
                            clientId = OidcTestIdentityProvider.CLIENT_ID,
                            clientSecret = OidcTestIdentityProvider.CLIENT_SECRET,
                        ),
                    ),
                )
            }
    }

    private fun createLocalAdministrator() {
        useFacade<LoginFacade>().createUser("admin", "Administrator", "correct horse battery")
    }

    private fun get(path: String, cookie: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("$base$path")).GET()
        cookie?.let { request.header("Cookie", it) }
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun HttpResponse<*>.location(): String =
        headers().firstValue("Location").orElseThrow { AssertionError("no Location header in ${statusCode()}") }

    private fun HttpResponse<*>.cookieValue(name: String): String? =
        headers().allValues("Set-Cookie")
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.substringBefore(';')

    private fun HttpResponse<*>.query(): Map<String, String> =
        URI(location()).rawQuery.split('&').associate {
            it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }

    /** Starts a login at the instance and echoes the nonce the browser was sent to the provider with. */
    private fun startLogin(redirect: String = "/repositories"): HttpResponse<String> {
        val start = get("/api/auth/oidc/corp/login?redirect=${java.net.URLEncoder.encode(redirect, "UTF-8")}")
        provider.nonce = start.query()["nonce"]
        return start
    }

    @Test
    fun `a disabled provider is not announced and its login route does not exist`() {
        // given: the provider is configured but disabled
        configureCorporateProvider(enabled = false)

        // when
        val providers = get("/api/auth/providers").body()
        val login = get("/api/auth/oidc/corp/login")

        // then
        assertThat(providers).describedAs("announced providers").doesNotContain("\"corp\"")
        assertThat(login.statusCode()).describedAs("login of a disabled provider").isEqualTo(NOT_FOUND.code)
    }

    @Test
    fun `an enabled provider is announced with its type and its login url`() {
        // given
        configureCorporateProvider()
        createLocalAdministrator()

        // when
        val providers = get("/api/auth/providers").body()

        // then
        assertThat(providers).contains(""""id":"corp"""", """"type":"OIDC"""", """"loginUrl":"/api/auth/oidc/corp/login"""")
    }

    @Test
    fun `a login through the provider starts a session for the new user`() {
        // given
        configureCorporateProvider()
        createLocalAdministrator()

        // when: the browser starts the login, the provider answers, and the callback is called back
        val start = startLogin(redirect = "/repositories")
        assertThat(start.statusCode()).describedAs("start status").isEqualTo(302)
        val stateCookie = start.cookieValue("ingot_oidc")
        assertThat(stateCookie).describedAs("state cookie").isNotNull()
        val callback = get(
            "/api/auth/oidc/corp/callback?code=authorization-code&state=${start.query()["state"]}",
            cookie = "ingot_oidc=$stateCookie",
        )

        // then: the browser lands on the redirect target with the session cookies
        assertThat(callback.statusCode()).describedAs("callback status").isEqualTo(302)
        assertThat(callback.location()).isEqualTo("/repositories")
        val sessionCookie = callback.cookieValue("ingot_session")
        assertThat(sessionCookie).describedAs("session cookie").isNotNull()

        // and the session works
        val me = get("/api/auth/me", cookie = "ingot_session=$sessionCookie")
        assertThat(me.statusCode()).describedAs("me with the session").isEqualTo(OK.code)
        assertThat(me.body()).contains("\"alice\"")
    }

    @Test
    fun `a state that does not match the cookie sends the browser back with an error`() {
        configureCorporateProvider()
        createLocalAdministrator()
        val start = startLogin()

        val callback = get(
            "/api/auth/oidc/corp/callback?code=authorization-code&state=forged",
            cookie = "ingot_oidc=${start.cookieValue("ingot_oidc")}",
        )

        assertThat(callback.location()).isEqualTo("/?loginError=invalid_state")
        assertThat(callback.cookieValue("ingot_session")).describedAs("no session").isNull()
    }

    @Test
    fun `a provider that denies the login sends the browser back with the code`() {
        configureCorporateProvider()
        createLocalAdministrator()
        val start = startLogin()

        val callback = get(
            "/api/auth/oidc/corp/callback?error=access_denied&state=${start.query()["state"]}",
            cookie = "ingot_oidc=${start.cookieValue("ingot_oidc")}",
        )

        assertThat(callback.location()).isEqualTo("/?loginError=access_denied")
    }

    @Test
    fun `a redirect to another origin is replaced by the root after the login`() {
        configureCorporateProvider()
        createLocalAdministrator()
        val start = startLogin(redirect = "https://evil.example/steal")

        val callback = get(
            "/api/auth/oidc/corp/callback?code=authorization-code&state=${start.query()["state"]}",
            cookie = "ingot_oidc=${start.cookieValue("ingot_oidc")}",
        )

        assertThat(callback.location()).describedAs("redirect after login").isEqualTo("/")
    }
}
