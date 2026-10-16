@file:Suppress("FunctionName")

package com.reposilite.identity

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
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
import java.util.Base64

/**
 * The connection tests of the external providers over HTTP: they answer the instance administrator, and nobody else.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ProviderTestIntegrationTest : ReposiliteSpecification() {

    private lateinit var directory: InMemoryDirectory
    private val client: HttpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun startDirectory() {
        directory = InMemoryDirectory()
        useFacade<SharedConfigurationFacade>()
            .getDomainSettings<LoginSettings>()
            .update { old ->
                old.copy(
                    ldap = LdapLoginSettings(
                        enabled = true,
                        url = directory.url,
                        bindDn = InMemoryDirectory.SERVICE_DN,
                        bindPassword = InMemoryDirectory.SERVICE_PASSWORD,
                        userBaseDn = "ou=people,${InMemoryDirectory.BASE_DN}",
                    ),
                )
            }
    }

    @AfterEach
    fun stopDirectory() {
        directory.close()
    }

    private fun postLdapTest(authorization: String?, body: String = "{}"): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("$base/api/auth/providers/ldap/test"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        authorization?.let { request.header("Authorization", it) }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun basic(name: String, secret: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$name:$secret".toByteArray())

    @Test
    fun `an anonymous caller cannot run the ldap test`() {
        val response = postLdapTest(authorization = null)

        assertThat(response.statusCode()).describedAs("status").isIn(401, 403)
        assertThat(response.body()).doesNotContain("\"ok\":true")
    }

    @Test
    fun `a token without the manager permission cannot run the ldap test`() {
        useAuth("reader", "reader-secret")

        val response = postLdapTest(basic("reader", "reader-secret"))

        assertThat(response.statusCode()).describedAs("status").isEqualTo(403)
    }

    @Test
    fun `the instance administrator runs the ldap test and gets the bind result`() {
        val (name, secret) = useDefaultManagementToken()

        val response = postLdapTest(basic(name, secret))

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains("\"ok\":true")
    }

    @Test
    fun `an unknown oidc provider is not found for the administrator`() {
        val (name, secret) = useDefaultManagementToken()

        val response = client.send(
            HttpRequest.newBuilder(URI("$base/api/auth/providers/oidc/missing/test"))
                .header("Authorization", basic(name, secret))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertThat(response.statusCode()).isEqualTo(404)
    }
}
