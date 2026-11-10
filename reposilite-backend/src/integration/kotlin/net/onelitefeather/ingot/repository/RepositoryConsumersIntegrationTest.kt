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

@file:Suppress("FunctionName")

package net.onelitefeather.ingot.repository

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.RepositoryVisibility
import com.reposilite.maven.ResolutionCache
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.storage.api.toLocation
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import kong.unirest.core.Unirest.put
import net.onelitefeather.ingot.cluster.FacadeCacheHooks
import net.onelitefeather.ingot.search.application.SearchFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * One test per consumer that reads repositories through the registry (design D1, D2 and task 4.1). Each test boots its own
 * instance with the full plugin set, drives the consumer once through its public entry point, and pins what it answers
 * today. Waits are explicit: the search outbox is drained by ticks, never polled.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RepositoryConsumersIntegrationTest : ReposiliteSpecification() {

    private val releaseArtifact = "com/example/app/1.0/app-1.0.jar"

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + listOf(
                    RepositorySettings(id = "settings", visibility = RepositoryVisibility.PUBLIC),
                    RepositorySettings(id = "secrets", visibility = RepositoryVisibility.PRIVATE),
                ) + RepositorySettings(id = "cached", visibility = RepositoryVisibility.PUBLIC, resolutionCacheMaxEntries = 16)
            )
        }
    }

    private val search: SearchFacade
        get() = useFacade()

    @Test
    fun `identity access explanation reports the visibility of a public and a private repository`() {
        // given: a public repository and a private one, and a manager asking about them
        val (token, secret) = useDefaultManagementToken()

        // when: the access explanation is requested for each
        val publicAccess = get("$base/api/identity/repositories/releases/access").basicAuth(token, secret).asJacksonObject(JsonNode::class)
        val privateAccess = get("$base/api/identity/repositories/secrets/access").basicAuth(token, secret).asJacksonObject(JsonNode::class)

        // then: each one carries the visibility of its repository
        assertThat(publicAccess.body.get("visibility").asText())
            .describedAs("the visibility of a public repository read through the registry")
            .isEqualTo("PUBLIC")
        assertThat(privateAccess.body.get("visibility").asText())
            .describedAs("the visibility of a private repository read through the registry")
            .isEqualTo("PRIVATE")
    }

    @Test
    fun `shared dashboard path answers a repository named settings with its artifact`() {
        // given: an artifact stored in the repository named like a dashboard route
        useDocument("settings", "com/example/a/1.0", "a-1.0.pom", content = "<project/>", store = true)

        // when: a Maven client requests it
        val response = get("$base/settings/com/example/a/1.0/a-1.0.pom").asString()

        // then: the response is the artifact, not the dashboard
        assertThat(response.status).describedAs("status of the shared path").isEqualTo(200)
        assertThat(response.body).describedAs("body of the shared path").isEqualTo("<project/>")
    }

    @Test
    fun `anonymous search does not return an artifact of a private repository`() {
        // given: one artifact in a public repository and one in a private repository, both indexed
        search.admin!!.pollingPaused = true
        useDocument("releases", "com/example/app/1.0", "app-1.0.jar", store = true)
        useDocument("secrets", "com/example/vault/1.0", "vault-1.0.jar", store = true)
        drainOutbox()

        // when: an anonymous client searches
        val body = get("$base/api/maven/search").asJacksonObject(JsonNode::class).body

        // then: the public artifact is found and the private one is not
        val artifactIds = body.get("items").map { it.get("artifactId").asText() }
        assertThat(artifactIds)
            .describedAs("anonymous search through the registry must not reveal a private repository")
            .contains("app")
            .doesNotContain("vault")
    }

    @Test
    fun `cleanup dry run lists the superseded release of a repository`() {
        // given: a policy that keeps the newest of two releases, and both releases deployed
        val (token, secret) = useDefaultManagementToken()
        val policy = put("$base/api/settings/domain/cleanup").basicAuth(token, secret).body(
            """{"policies":[{"id":"keep-newest","enabled":false,"repositories":["releases"],"scope":"RELEASES","keepLatest":1}]}"""
        ).asEmpty()
        assertThat(policy.status).describedAs("policy update").isEqualTo(200)
        listOf("1.0", "1.1").forEach { version ->
            useDocument("releases", "com/example/app/$version", "app-$version.pom", "<project/>", store = true)
            useDocument("releases", "com/example/app/$version", "app-$version.jar", "jar-content", store = true)
        }

        // when: the policy is previewed
        val preview = post("$base/api/cleanup/policies/keep-newest/dry-run")
            .basicAuth(token, secret)
            .asJacksonObject(JsonNode::class)

        // then: the older release of the repository is listed
        assertThat(preview.body.get("deletions").map { it.get("path").asText() })
            .describedAs("the dry run must enumerate the repositories through the registry")
            .containsExactly("com/example/app/1.0")
    }

    @Test
    fun `remote resolution invalidation clears the cached resolution of its path`() {
        // given: a resolution cached for the path in the repository the cluster message names
        val repository = mavenFacade.getRepository("cached")!!
        val cache = checkNotNull(repository.resolutionCache) { "the cached repository must have a resolution cache" }
        val gav = releaseArtifact.toLocation()
        cache.record(gav.getParent(), false, ResolutionCache.Origin.Remote("upstream.example"))
        assertThat(cache.lookup(gav, false)).describedAs("the entry before the message").isNotNull()

        // when: the remote instance announces the invalidation, as the cluster bus delivers it
        val cluster = reposilite.extensions.getPlugins().getValue("cluster").plugin
        FacadeCacheHooks(cluster).resolutionInvalidated("cached", releaseArtifact)

        // then: the cached resolution of that path is gone
        assertThat(cache.lookup(gav, false))
            .describedAs("the resolution invalidation must clear the entry through the coordinator")
            .isNull()
    }

    /** Ticks the search outbox until a tick claims nothing. The bound turns a stuck entry into a failure instead of a hang. */
    private fun drainOutbox() {
        var ticks = 0
        while (search.admin!!.drainOutbox().claimed > 0) {
            check(++ticks < MAX_DRAIN_TICKS) { "the outbox did not drain in $MAX_DRAIN_TICKS ticks" }
        }
    }

    private companion object {
        const val MAX_DRAIN_TICKS = 100
    }

}
