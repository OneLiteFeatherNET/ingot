/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.maven

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenIntegrationSpecification
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.api.toLocation
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val PLACEHOLDER_UPSTREAM = "https://placeholder.invalid/releases"
private const val TYPED_GROUP_MEMBER_EXTERNAL = "https://example.com/releases"

/**
 * Typed and untyped repositories as the API serves and enforces them. The group tests never move the upstream port,
 * because that rewrites every proxied reference of every repository, loopback references included.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RepositoryTypesIntegrationTest : ReposiliteSpecification() {

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + listOf(
                    RepositorySettings(id = "typed-hosted", type = RepositoryType.HOSTED),
                    RepositorySettings(id = "typed-proxy", type = RepositoryType.PROXY, proxied = listOf(MirroredRepositorySettings(reference = PLACEHOLDER_UPSTREAM, store = true))),
                    RepositorySettings(id = "typed-group", type = RepositoryType.GROUP, proxied = listOf(
                        MirroredRepositorySettings(reference = "typed-hosted"),
                        MirroredRepositorySettings(reference = "releases"),
                    )),
                    RepositorySettings(id = "legacy-group", proxied = listOf(
                        MirroredRepositorySettings(reference = "typed-hosted"),
                        MirroredRepositorySettings(reference = ""),
                    )),
                )
            )
        }
    }

    @Test
    fun `should still accept an upload into an untyped group that lists an empty entry`() {
        // given: a manager and the untyped group of the production shape
        val (name, secret) = useDefaultManagementToken()

        // when: a file is uploaded to the group
        val response = Unirest.put("$base/legacy-group/com/example/app/1.0/app-1.0.jar")
            .basicAuth(name, secret)
            .body("content")
            .asString()

        // then: the untyped repository keeps its behaviour
        assertThat(response.isSuccess).describedAs("upload to an untyped group (status ${response.status})").isTrue
    }

    @Test
    fun `should reject a typed group that proxies an external url and name the repository and the url`() {
        // given: a manager and a typed group with an external member
        val (name, secret) = useDefaultManagementToken()
        val settings = MavenSettings(repositories = listOf(
            RepositorySettings(id = "bad-group", type = RepositoryType.GROUP, proxied = listOf(MirroredRepositorySettings(reference = TYPED_GROUP_MEMBER_EXTERNAL)))
        ))

        // when: the maven settings are updated
        val response = Unirest.put("$base/api/settings/domain/maven")
            .basicAuth(name, secret)
            .body(settings)
            .asJacksonObject(ErrorResponse::class)

        // then: the update is rejected with the rule and the offending url
        assertErrorResponse(BAD_REQUEST, response)
        assertThat(response.body.message)
            .contains("'bad-group'")
            .contains("GROUP")
            .contains(TYPED_GROUP_MEMBER_EXTERNAL)
    }

    @Test
    fun `should reject a hosted repository that proxies and keep the stored settings`() {
        // given: a manager and a hosted repository that proxies
        val (name, secret) = useDefaultManagementToken()
        val settings = MavenSettings(repositories = listOf(
            RepositorySettings(id = "bad-hosted", type = RepositoryType.HOSTED, proxied = listOf(MirroredRepositorySettings(reference = TYPED_GROUP_MEMBER_EXTERNAL)))
        ))
        val rejected = Unirest.put("$base/api/settings/domain/maven")
            .basicAuth(name, secret)
            .body(settings)
            .asJacksonObject(ErrorResponse::class)
        assertErrorResponse(BAD_REQUEST, rejected)
        assertThat(rejected.body.message).contains("'bad-hosted'").contains("HOSTED")

        // when: the stored maven settings are requested
        val stored = getJson("/api/settings/domain/maven", name to secret)

        // then: the rejected repository was not written
        val ids = stored.at("/repositories").map { it.at("/id").asText() }
        assertThat(ids).doesNotContain("bad-hosted")
    }

    @Test
    fun `should list the type and the explicit flag of every repository`() {
        // when: the repositories are listed anonymously
        val entries = getJson("/api/maven/repositories").at("/repositories")
            .associate { it.at("/id").asText() to (it.at("/type").asText() to it.at("/typeExplicit").asBoolean()) }

        // then: typed repositories are explicit, untyped ones are derived from their proxied repositories
        assertThat(entries["typed-hosted"]).isEqualTo("hosted" to true)
        assertThat(entries["typed-proxy"]).isEqualTo("proxy" to true)
        assertThat(entries["typed-group"]).isEqualTo("group" to true)
        assertThat(entries["legacy-group"]).isEqualTo("group" to false)
        assertThat(entries["releases"]).isEqualTo("hosted" to false)
    }

    @Test
    fun `should list the local members of a typed group`() {
        // when: the members of the typed group are requested anonymously
        val members = getJson("/api/maven/repositories/typed-group/members").at("/members").map { it.asText() }

        // then: the local references are listed in configuration order
        assertThat(members).containsExactly("typed-hosted", "releases")
    }

    @Test
    fun `should describe the type in the maven settings schema as an optional enum`() {
        // when: the maven settings schema is requested by a manager
        val schema = getJson("/api/settings/schema/maven", useDefaultManagementToken())

        // then: the type of a repository is an enum of the three types and is not required
        val typeSchema = schema.at("/properties/repositories/items/properties/type")
        assertThat(typeSchema.at("/anyOf/0/type").asText()).describedAs("null is accepted, the type is optional").isEqualTo("null")
        assertThat(typeSchema.findValues("enum").single().map { it.asText() }).containsExactly("HOSTED", "PROXY", "GROUP")
        assertThat(schema.at("/properties/repositories/items/required").map { it.asText() }).doesNotContain("type")
    }

    private fun getJson(path: String, credentials: Pair<String, String>? = null): JsonNode {
        val request = Unirest.get("$base$path")
        val authorized = credentials?.let { request.basicAuth(it.first, it.second) } ?: request
        val response = authorized.asString()
        assertThat(response.status).describedAs("status of $path").isEqualTo(OK.code)
        return DEFAULT_OBJECT_MAPPER.readTree(response.body)
    }

}

/**
 * The typed proxy keeps caching what it fetches, although it takes no uploads. Kept apart from the group tests, because
 * the upstream port of a test moves every proxied reference of the instance.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RepositoryTypesProxyIntegrationTest : MavenIntegrationSpecification() {

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + RepositorySettings(
                    id = "typed-proxy",
                    type = RepositoryType.PROXY,
                    proxied = listOf(MirroredRepositorySettings(reference = PLACEHOLDER_UPSTREAM, store = true)),
                )
            )
        }
    }

    @Test
    fun `should keep caching the files a typed proxy fetches from its upstream`() = runBlocking {
        useProxiedHost("releases", "com/example/cached/1.0/cached-1.0.jar", "cached-content") { gav, content ->
            // when: a file that exists upstream is requested through the typed proxy
            val response = Unirest.get("$base/typed-proxy/$gav").asString()

            // then: the proxy answers with the upstream content and stores its copy
            assertThat(response.status).isEqualTo(OK.code)
            assertThat(response.body).isEqualTo(content)
            assertThat(mavenFacade.getRepository("typed-proxy")!!.storageProvider.exists(gav.toLocation())).isTrue
        }
    }

}
