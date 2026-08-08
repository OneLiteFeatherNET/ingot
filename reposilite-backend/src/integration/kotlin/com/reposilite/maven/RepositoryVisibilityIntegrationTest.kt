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

@file:Suppress("FunctionName")

package com.reposilite.maven

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.RoutePermission.READ
import kong.unirest.core.JsonNode
import kong.unirest.core.Unirest.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val HIDDEN_REPOSITORY = "hidden"

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RepositoryVisibilityIntegrationTest : ReposiliteSpecification() {

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + RepositorySettings(
                    id = HIDDEN_REPOSITORY,
                    visibility = RepositoryVisibility.HIDDEN
                )
            )
        }
    }

    @Test
    fun `should describe only accessible repositories and their visibility`() {
        // when: the root listing is requested without any credentials
        val anonymousResponse = get("$base/api/maven/details").asJson()

        // then: only public repositories are returned, and each of them carries its visibility
        assertThat(anonymousResponse.isSuccess).isTrue
        assertThat(anonymousResponse.body.visibilities()).isEqualTo(
            mapOf(
                "releases" to "PUBLIC",
                "snapshots" to "PUBLIC",
                "proxied" to "PUBLIC",
                "proxied-stored" to "PUBLIC",
                "immutable" to "PUBLIC"
            )
        )

        // given: a manager token
        val (managerName, managerSecret) = useAuth("manager-token", "manager-token-secret", listOf(MANAGER))

        // when: the root listing is requested with that token
        val managerResponse = get("$base/api/maven/details")
            .basicAuth(managerName, managerSecret)
            .asJson()

        // then: hidden and private repositories show up with their own visibility
        assertThat(managerResponse.isSuccess).isTrue
        assertThat(managerResponse.body.visibilities()).isEqualTo(
            mapOf(
                "releases" to "PUBLIC",
                "snapshots" to "PUBLIC",
                "private" to "PRIVATE",
                "proxied" to "PUBLIC",
                "proxied-stored" to "PUBLIC",
                "immutable" to "PUBLIC",
                HIDDEN_REPOSITORY to "HIDDEN"
            )
        )
    }

    @Test
    fun `should keep the directory shape of the listing untouched`() {
        // when: the root listing is requested without any credentials
        val response = get("$base/api/maven/details").asJson()

        // then: the entries a client written against the previous payload reads are still there
        val root = response.body.`object`
        assertThat(root.getString("name")).isEqualTo("/")
        assertThat(root.getString("type")).isEqualTo("DIRECTORY")

        val releases = root.getJSONArray("files")
            .let { files -> (0 until files.length()).map { files.getJSONObject(it) } }
            .first { it.getString("name") == "releases" }

        assertThat(releases.getString("type")).isEqualTo("DIRECTORY")
        assertThat(releases.getString("visibility")).isEqualTo("PUBLIC")
    }

    @Test
    fun `should not reveal a hidden repository to a token that cannot see it`() {
        // given: a token scoped to an unrelated repository
        val (name, secret) = useAuth("scoped", "scoped-secret", routes = mapOf("/releases" to READ))

        // when: the root listing is requested with that token
        val response = get("$base/api/maven/details")
            .basicAuth(name, secret)
            .asJson()

        // then: neither the hidden nor the private repository is part of the response
        assertThat(response.isSuccess).isTrue
        assertThat(response.body.visibilities().keys).doesNotContain(HIDDEN_REPOSITORY, "private")
    }

    private fun JsonNode.visibilities(): Map<String, String> =
        `object`.getJSONArray("files")
            .let { files -> (0 until files.length()).map { files.getJSONObject(it) } }
            .associate { it.getString("name") to it.getString("visibility") }

}
