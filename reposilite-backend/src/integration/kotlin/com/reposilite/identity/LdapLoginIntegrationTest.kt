@file:Suppress("FunctionName")

package com.reposilite.identity

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.ldap.InMemoryDirectory
import net.onelitefeather.ingot.identity.login.ldap.LdapLoginSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * The LDAP password login over HTTP: `POST /api/auth/login` with `provider: "ldap"` against an in-process directory, the
 * session it starts, the refusals, and the lock after repeated failures.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LdapLoginIntegrationTest : ReposiliteSpecification() {

    private lateinit var directory: InMemoryDirectory
    private val client: HttpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun startDirectory() {
        directory = InMemoryDirectory()
    }

    @AfterEach
    fun stopDirectory() {
        directory.close()
    }

    private fun configureDirectory(enabled: Boolean = true) {
        useFacade<SharedConfigurationFacade>()
            .getDomainSettings<LoginSettings>()
            .update { old ->
                old.copy(
                    ldap = LdapLoginSettings(
                        enabled = enabled,
                        url = directory.url,
                        bindDn = InMemoryDirectory.SERVICE_DN,
                        bindPassword = InMemoryDirectory.SERVICE_PASSWORD,
                        userBaseDn = "ou=people,${InMemoryDirectory.BASE_DN}",
                    ),
                )
            }
    }

    private fun login(username: String, password: String): HttpResponse<String> {
        val body = """{"provider":"ldap","username":"$username","password":"$password"}"""
        return client.send(
            HttpRequest.newBuilder(URI("$base/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun createLocalAdministrator() {
        useFacade<LoginFacade>().createUser("admin", "Administrator", "correct horse battery")
    }

    @Test
    fun `a directory user logs in and receives the session cookie`() {
        configureDirectory()
        createLocalAdministrator()

        val response = login("alice", "alice-secret")

        assertThat(response.statusCode()).describedAs("status").isEqualTo(200)
        assertThat(response.headers().allValues("Set-Cookie").joinToString(";")).contains("ingot_session=")
    }

    @Test
    fun `a wrong password is refused with unauthorized`() {
        configureDirectory()
        createLocalAdministrator()

        assertThat(login("alice", "guess").statusCode()).isEqualTo(401)
    }

    @Test
    fun `an empty password is a missing field, as for every provider`() {
        configureDirectory()
        createLocalAdministrator()

        assertThat(login("alice", "").statusCode()).isEqualTo(400)
    }

    @Test
    fun `repeated failures lock the user name with too many requests`() {
        configureDirectory()
        createLocalAdministrator()
        // the default limit is five failed attempts per user name and address
        repeat(5) { assertThat(login("alice", "guess").statusCode()).isEqualTo(401) }

        val locked = login("alice", "alice-secret")

        assertThat(locked.statusCode()).describedAs("locked attempt").isEqualTo(429)
        assertThat(locked.headers().firstValue("Retry-After")).isPresent
    }

    @Test
    fun `a disabled directory is not announced and refuses the provider`() {
        configureDirectory(enabled = false)
        createLocalAdministrator()

        assertThat(get("/api/auth/providers").body()).doesNotContain("\"ldap\"")
        assertThat(login("alice", "alice-secret").statusCode()).isEqualTo(400)
    }

    private fun get(path: String): HttpResponse<String> =
        client.send(HttpRequest.newBuilder(URI("$base$path")).GET().build(), HttpResponse.BodyHandlers.ofString())
}
