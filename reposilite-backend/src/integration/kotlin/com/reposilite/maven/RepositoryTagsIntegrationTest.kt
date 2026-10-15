@file:Suppress("FunctionName")

package com.reposilite.maven

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.ReposiliteSpecification
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.shared.ErrorResponse
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RepositoryTagsIntegrationTest : ReposiliteSpecification() {

    @Test
    fun `should reject an update with an invalid tag and name the repository and the tag`() {
        // given: a permitted token and a maven block with a tag that is not lowercase
        val (name, secret) = useDefaultManagementToken()
        val settings = MavenSettings(repositories = listOf(RepositorySettings(id = "releases", tags = listOf("Team"))))

        // when: the maven settings are updated
        val response = Unirest.put("$base/api/settings/domain/maven")
            .basicAuth(name, secret)
            .body(settings)
            .asJacksonObject(ErrorResponse::class)

        // then: the update is rejected with a message naming the repository and the tag
        assertErrorResponse(BAD_REQUEST, response)
        assertThat(response.body.message)
            .contains("'releases'")
            .contains("'Team'")
    }

    @Test
    fun `should keep the stored tags when an update with an invalid tag is rejected`() {
        // given: a permitted token and an update that carries a duplicated tag
        val (name, secret) = useDefaultManagementToken()
        val settings = MavenSettings(repositories = listOf(RepositorySettings(id = "releases", tags = listOf("team", "team"))))
        val rejected = Unirest.put("$base/api/settings/domain/maven")
            .basicAuth(name, secret)
            .body(settings)
            .asJacksonObject(ErrorResponse::class)
        assertErrorResponse(BAD_REQUEST, rejected)

        // when: the stored maven settings are requested
        val stored = Unirest.get("$base/api/settings/domain/maven")
            .basicAuth(name, secret)
            .asString()
            .also { assertThat(it.status).isEqualTo(OK.code) }
            .body
            .let { DEFAULT_OBJECT_MAPPER.readTree(it) }

        // then: the rejected tags were not written
        val releases = stored.at("/repositories").first { it.at("/id").asText() == "releases" }
        assertThat(releases.at("/tags").map { it.asText() }).describedAs("stored tags of 'releases'").isEmpty()
    }

}
