@file:Suppress("FunctionName")

package com.reposilite.configuration

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.auth.application.AuthenticationSettings
import com.reposilite.auth.application.LdapSettings
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.specification.SettingsIntegrationSpecification
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirrorCredentials
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import panda.std.reactive.ReferenceUtils

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class HiddenSecretsSettingsIntegrationTest : SettingsIntegrationSpecification() {

    private val placeholder = "********"

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        ReferenceUtils.setValue(localConfiguration.hideSettingsSecrets, true)
    }

    private val settingsFacade: SharedConfigurationFacade
        get() = useFacade()

    private fun readDomain(domain: String): ObjectNode {
        // given: a permitted token
        val (name, secret) = useDefaultManagementToken()

        // when: the domain is requested as the dashboard does
        val response = Unirest.get("$base/api/settings/domain/$domain")
            .basicAuth(name, secret)
            .asString()

        // then: the request succeeded and the body is an object
        assertSuccessResponse(OK, response)
        return DEFAULT_OBJECT_MAPPER.readTree(response.body) as ObjectNode
    }

    private fun writeDomain(domain: String, document: ObjectNode): HttpResponse<*> {
        // given: a permitted token
        val (name, secret) = useDefaultManagementToken()

        // when: the domain is written back as the dashboard does
        return Unirest.put("$base/api/settings/domain/$domain")
            .basicAuth(name, secret)
            .body(document.toString())
            .asEmpty()
    }

    private fun useMavenWithMirrors() {
        settingsFacade.updateSharedSettings(
            "maven",
            MavenSettings(
                repositories = listOf(
                    RepositorySettings(
                        id = "alpha",
                        proxied = listOf(
                            MirroredRepositorySettings(
                                reference = "https://alpha.example.test/releases",
                                authorization = MirrorCredentials(login = "alpha", password = "alpha-secret")
                            )
                        )
                    ),
                    RepositorySettings(
                        id = "beta",
                        proxied = listOf(
                            MirroredRepositorySettings(
                                reference = "https://beta.example.test/releases",
                                authorization = MirrorCredentials(login = "beta", password = "beta-secret")
                            )
                        )
                    )
                )
            )
        )
    }

    private fun ObjectNode.repositories(): ArrayNode =
        get("repositories") as ArrayNode

    private fun ObjectNode.repositoryById(id: String): ObjectNode =
        repositories().single { it.get("id").asText() == id } as ObjectNode

    private fun mirrorPassword(id: String): String? =
        settingsFacade.getDomainSettings<MavenSettings>().get().repositories
            .single { it.id == id }
            .proxied.single().authorization?.password

    @Test
    fun `should mask a non-empty secret on read`() {
        // given: a stored LDAP search password
        settingsFacade.updateSharedSettings("authentication", AuthenticationSettings(ldap = LdapSettings(searchUserPassword = "ldap-secret")))

        // when: the authentication domain is read
        val domain = readDomain("authentication")

        // then: the password is replaced with the placeholder
        assertThat(domain.get("ldap").get("searchUserPassword").asText())
            .describedAs("LDAP search password in the API response")
            .isEqualTo(placeholder)
    }

    @Test
    fun `should show an empty secret as empty on read`() {
        // given: an LDAP search password that is not set
        settingsFacade.updateSharedSettings("authentication", AuthenticationSettings(ldap = LdapSettings(searchUserPassword = "")))

        // when: the authentication domain is read
        val domain = readDomain("authentication")

        // then: the field stays empty so the dashboard can show that nothing is set
        assertThat(domain.get("ldap").get("searchUserPassword").asText())
            .describedAs("unset LDAP search password in the API response")
            .isEmpty()
    }

    @Test
    fun `should keep the stored secret when the placeholder is sent back`() {
        // given: a stored LDAP search password that the dashboard read as a placeholder
        settingsFacade.updateSharedSettings("authentication", AuthenticationSettings(ldap = LdapSettings(searchUserPassword = "ldap-secret")))
        val domain = readDomain("authentication")

        // when: the unchanged document is written back
        val response = writeDomain("authentication", domain)

        // then: the update succeeded and the stored password is intact
        assertSuccessResponse(OK, response)
        assertThat(settingsFacade.getDomainSettings<AuthenticationSettings>().get().ldap.searchUserPassword)
            .describedAs("stored LDAP search password after a round trip")
            .isEqualTo("ldap-secret")
    }

    @Test
    fun `should replace the stored secret when a new value is sent`() {
        // given: a stored LDAP search password and a document with a new value for it
        settingsFacade.updateSharedSettings("authentication", AuthenticationSettings(ldap = LdapSettings(searchUserPassword = "ldap-secret")))
        val domain = readDomain("authentication")
        (domain.get("ldap") as ObjectNode).put("searchUserPassword", "rotated-secret")

        // when: the document is written back
        val response = writeDomain("authentication", domain)

        // then: the new value is stored
        assertSuccessResponse(OK, response)
        assertThat(settingsFacade.getDomainSettings<AuthenticationSettings>().get().ldap.searchUserPassword)
            .describedAs("stored LDAP search password")
            .isEqualTo("rotated-secret")
    }

    @Test
    fun `should clear the stored secret when an empty value is sent`() {
        // given: a stored LDAP search password and a document that clears it
        settingsFacade.updateSharedSettings("authentication", AuthenticationSettings(ldap = LdapSettings(searchUserPassword = "ldap-secret")))
        val domain = readDomain("authentication")
        (domain.get("ldap") as ObjectNode).put("searchUserPassword", "")

        // when: the document is written back
        val response = writeDomain("authentication", domain)

        // then: the stored password is empty
        assertSuccessResponse(OK, response)
        assertThat(settingsFacade.getDomainSettings<AuthenticationSettings>().get().ldap.searchUserPassword)
            .describedAs("stored LDAP search password")
            .isEmpty()
    }

    @Test
    fun `should match mirror passwords by repository after the repositories are reordered`() {
        // given: two repositories with their own mirror passwords, read by the dashboard
        useMavenWithMirrors()
        val domain = readDomain("maven")

        // when: the repositories are sent back in reverse order, still carrying placeholders
        val reversed = DEFAULT_OBJECT_MAPPER.createArrayNode()
        domain.repositories().reversed().forEach { reversed.add(it) }
        domain.set<ObjectNode>("repositories", reversed)
        val response = writeDomain("maven", domain)

        // then: every mirror keeps the password of its own repository
        assertSuccessResponse(OK, response)
        assertThat(mirrorPassword("alpha")).describedAs("alpha mirror password").isEqualTo("alpha-secret")
        assertThat(mirrorPassword("beta")).describedAs("beta mirror password").isEqualTo("beta-secret")
    }

    @Test
    fun `should reject a placeholder for a repository that is not stored`() {
        // given: stored repositories and a new repository whose mirror password is sent as a placeholder
        useMavenWithMirrors()
        val domain = readDomain("maven")
        val gamma = DEFAULT_OBJECT_MAPPER.createObjectNode().put("id", "gamma")
        gamma.putArray("proxied")
            .addObject()
            .put("reference", "https://gamma.example.test/releases")
            .putObject("authorization")
            .put("login", "gamma")
            .put("password", placeholder)
        domain.repositories().add(gamma)

        // when: the document is written back
        val response = writeDomain("maven", domain)

        // then: the request is rejected and nothing new is stored
        assertErrorResponse(BAD_REQUEST, response)
        assertThat(settingsFacade.getDomainSettings<MavenSettings>().get().repositories.map { it.id })
            .describedAs("stored repository ids")
            .doesNotContain("gamma")
    }

    @Test
    fun `should reject a placeholder for a repository that was renamed`() {
        // given: a stored repository "alpha" whose id is changed while its mirror password stays a placeholder
        useMavenWithMirrors()
        val domain = readDomain("maven")
        domain.repositoryById("alpha").put("id", "alpha-renamed")

        // when: the document is written back
        val response = writeDomain("maven", domain)

        // then: the request is rejected and the stored password is untouched
        assertErrorResponse(BAD_REQUEST, response)
        assertThat(mirrorPassword("alpha")).describedAs("alpha mirror password").isEqualTo("alpha-secret")
    }

    @Test
    fun `should report that secrets are hidden on the frontend settings`() {
        // when: the public dashboard settings are requested
        val response = Unirest.get("$base/api/frontend/settings").asString()

        // then: the dashboard is told that secrets are hidden
        assertSuccessResponse(OK, response)
        assertThat(DEFAULT_OBJECT_MAPPER.readTree(response.body).get("secretsHidden").asBoolean())
            .describedAs("secretsHidden flag")
            .isTrue
    }

}
