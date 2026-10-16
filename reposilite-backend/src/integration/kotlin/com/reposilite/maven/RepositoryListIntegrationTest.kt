@file:Suppress("FunctionName")

package com.reposilite.maven

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.RoutePermission.READ
import com.fasterxml.jackson.databind.JsonNode
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val NEEDLE = "catalogue needle"

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RepositoryListIntegrationTest : ReposiliteSpecification() {

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + listOf(
                    RepositorySettings(id = "catalog-open", visibility = RepositoryVisibility.PUBLIC, description = "Public $NEEDLE", tags = listOf("team-a", "stable")),
                    RepositorySettings(id = "catalog-private", visibility = RepositoryVisibility.PRIVATE, description = "Private $NEEDLE", tags = listOf("team-a")),
                    RepositorySettings(id = "catalog-hidden", visibility = RepositoryVisibility.HIDDEN, description = "Hidden $NEEDLE", tags = listOf("staging")),
                )
            )
        }
    }

    @Test
    fun `should answer anonymous callers with the public repositories in the documented shape`() {
        // when: the list is requested without credentials
        val body = listRepositories()

        // then: the response has exactly one top-level field
        assertThat(body.fieldNames().asSequence().toList()).containsExactly("repositories")

        // and: every entry has exactly the documented fields
        val entries = body.at("/repositories")
        entries.forEach { entry ->
            assertThat(entry.fieldNames().asSequence().toList())
                .describedAs("fields of '${entry.at("/id").asText()}'")
                .containsExactlyInAnyOrder("id", "visibility", "description", "tags")
        }

        // and: the public repository carries its description and tags, the private and hidden ones are absent
        val open = entryOf(body, "catalog-open")
        assertThat(open.at("/visibility").asText()).isEqualTo("PUBLIC")
        assertThat(open.at("/description").asText()).isEqualTo("Public $NEEDLE")
        assertThat(open.at("/tags").map { it.asText() }).containsExactly("team-a", "stable")
        assertThat(ids(body)).doesNotContain("catalog-private", "catalog-hidden")
    }

    @Test
    fun `should list a private repository to a token with a route to it`() {
        // given: a token with read access to the private repository only
        val (name, secret) = useAuth("catalog-reader", "catalog-reader-secret", routes = mapOf("/catalog-private" to READ))

        // when: the list is requested with that token
        val body = listRepositories(basicAuth = name to secret)

        // then: the private repository is listed, the hidden one is not
        assertThat(ids(body)).contains("catalog-private").doesNotContain("catalog-hidden")
    }

    @Test
    fun `should not list a hidden repository to a token with a route to it`() {
        // given: a token with read access to the hidden repository
        val (name, secret) = useAuth("catalog-hidden-reader", "catalog-hidden-reader-secret", routes = mapOf("/catalog-hidden" to READ))

        // when: the list is requested with that token
        val body = listRepositories(basicAuth = name to secret)

        // then: the hidden repository is not listed
        assertThat(ids(body)).doesNotContain("catalog-hidden")
    }

    @Test
    fun `should list hidden and private repositories to a manager`() {
        // given: a manager token
        val (name, secret) = useAuth("catalog-manager", "catalog-manager-secret", permissions = listOf(MANAGER))

        // when: the list is requested with that token
        val body = listRepositories(basicAuth = name to secret)

        // then: both non-public repositories are listed with their visibility
        assertThat(entryOf(body, "catalog-hidden").at("/visibility").asText()).isEqualTo("HIDDEN")
        assertThat(entryOf(body, "catalog-private").at("/visibility").asText()).isEqualTo("PRIVATE")
    }

    @Test
    fun `should sort the entries by id`() {
        // when: the list is requested
        val body = listRepositories()

        // then: the ids are in ascending order
        assertThat(ids(body)).isSorted()
    }

    @Test
    fun `should filter by the query parameter case-insensitively`() {
        // when: the query is requested in upper case
        val body = listRepositories(query = "CATALOGUE NEEDLE")

        // then: only the visible repository whose description matches is listed
        assertThat(ids(body)).containsExactly("catalog-open")
    }

    @Test
    fun `should require every repeated tag parameter`() {
        // when: two tags the public repository carries are requested
        val matching = listRepositories(tags = listOf("team-a", "stable"))
        // and: one tag the repository does not carry is added
        val missing = listRepositories(tags = listOf("team-a", "staging"))

        // then: the repository is listed only when every tag matches
        assertThat(ids(matching)).containsExactly("catalog-open")
        assertThat(ids(missing)).isEmpty()
    }

    @Test
    fun `should combine the query and the tags`() {
        // when: a query and a tag both match the public repository
        val body = listRepositories(query = "public", tags = listOf("stable"))

        // then: the repository is listed
        assertThat(ids(body)).containsExactly("catalog-open")
    }

    private fun listRepositories(
        basicAuth: Pair<String, String>? = null,
        query: String? = null,
        tags: List<String> = emptyList(),
    ): JsonNode {
        val parameters = buildList {
            query?.let { add("q=" + it.replace(" ", "%20")) }
            tags.forEach { add("tag=$it") }
        }
        val url = if (parameters.isEmpty()) "$base/api/maven/repositories" else "$base/api/maven/repositories?${parameters.joinToString("&")}"

        val request = Unirest.get(url)
        basicAuth?.let { request.basicAuth(it.first, it.second) }

        return request.asString()
            .also { assertThat(it.status).describedAs("status of $url").isEqualTo(OK.code) }
            .body
            .let { DEFAULT_OBJECT_MAPPER.readTree(it) }
    }

    private fun ids(body: JsonNode): List<String> =
        body.at("/repositories").map { it.at("/id").asText() }

    private fun entryOf(body: JsonNode, id: String): JsonNode =
        body.at("/repositories").first { it.at("/id").asText() == id }

}
