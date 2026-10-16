@file:Suppress("FunctionName")

package com.reposilite.maven

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.token.RoutePermission.READ
import com.fasterxml.jackson.databind.JsonNode
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RepositoryMembersIntegrationTest : ReposiliteSpecification() {

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + listOf(
                    RepositorySettings(id = "members-open", visibility = RepositoryVisibility.PUBLIC),
                    RepositorySettings(id = "members-private", visibility = RepositoryVisibility.PRIVATE),
                    RepositorySettings(
                        id = "members-group",
                        visibility = RepositoryVisibility.PUBLIC,
                        proxied = mutableListOf(
                            MirroredRepositorySettings(reference = "members-private"),
                            MirroredRepositorySettings(reference = "https://repo.example.invalid/maven"),
                            MirroredRepositorySettings(reference = "members-open"),
                        )
                    ),
                )
            )
        }
    }

    @Test
    fun `should answer anonymous callers with the public references in the documented shape`() {
        // when: the members are requested without credentials
        val (status, body) = members("members-group")

        // then: the response is OK and has exactly one top-level field
        assertThat(status).isEqualTo(OK.code)
        assertThat(body.fieldNames().asSequence().toList()).containsExactly("members")

        // and: the private reference and the external proxy are left out
        assertThat(body.at("/members").map { it.asText() }).containsExactly("members-open")
    }

    @Test
    fun `should list a private reference to a token with a route to it, in configuration order`() {
        // given: a token with read access to the private reference
        val (name, secret) = useAuth("members-reader", "members-reader-secret", routes = mapOf("/members-private" to READ))

        // when: the members are requested with that token
        val (_, body) = members("members-group", basicAuth = name to secret)

        // then: the private reference is listed before the public one, as configured
        assertThat(body.at("/members").map { it.asText() }).containsExactly("members-private", "members-open")
    }

    @Test
    fun `should answer a repository without loopback references with an empty list`() {
        // when: the members of a plain hosted repository are requested
        val (status, body) = members("members-open")

        // then: the request succeeds and nothing is listed
        assertThat(status).isEqualTo(OK.code)
        assertThat(body.at("/members").toList()).isEmpty()
    }

    @Test
    fun `should answer an unknown repository with not found`() {
        // when: the members of a repository that does not exist are requested
        val (status, _) = members("members-missing")

        // then: the response is not found
        assertThat(status).isEqualTo(NOT_FOUND.code)
    }

    private fun members(repository: String, basicAuth: Pair<String, String>? = null): Pair<Int, JsonNode> {
        val request = Unirest.get("$base/api/maven/repositories/$repository/members")
        basicAuth?.let { request.basicAuth(it.first, it.second) }

        val response = request.asString()
        val body = if (response.body.isNullOrBlank()) DEFAULT_OBJECT_MAPPER.createObjectNode() else DEFAULT_OBJECT_MAPPER.readTree(response.body)
        return response.status to body
    }

}
