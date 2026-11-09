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

package com.reposilite.maven

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirrorCredentials
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenIntegrationSpecification
import com.reposilite.shared.extensions.maxAge
import com.reposilite.shared.http.AuthenticationMethod
import com.reposilite.token.RoutePermission.READ
import kong.unirest.core.HeaderNames.CACHE_CONTROL
import kong.unirest.core.HeaderNames.ETAG
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.head
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val PRIVATE_REPOSITORY = "cache-private"
private const val HIDDEN_REPOSITORY = "cache-hidden"
private const val PRIVATE_MEMBER = "cache-private-member"
private const val GROUP_REPOSITORY = "cache-group"
private const val PRIVATE_CONTENT = "private-content-for-range"
private const val MIRROR_LOGIN = "cache-mirror"
private const val MIRROR_SECRET = "cache-mirror-secret"

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class MavenCacheControlIntegrationTest : MavenIntegrationSpecification() {

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + listOf(
                    RepositorySettings(id = PRIVATE_REPOSITORY, visibility = RepositoryVisibility.PRIVATE),
                    RepositorySettings(id = HIDDEN_REPOSITORY, visibility = RepositoryVisibility.HIDDEN),
                    RepositorySettings(id = PRIVATE_MEMBER, visibility = RepositoryVisibility.PRIVATE),
                    RepositorySettings(
                        id = GROUP_REPOSITORY,
                        visibility = RepositoryVisibility.PUBLIC,
                        proxied = mutableListOf(
                            MirroredRepositorySettings(
                                reference = PRIVATE_MEMBER,
                                authorization = MirrorCredentials(
                                    method = AuthenticationMethod.LOOPBACK_LINK,
                                    login = MIRROR_LOGIN,
                                    password = MIRROR_SECRET,
                                ),
                            )
                        ),
                    ),
                )
            )
        }
    }

    @Test
    fun `should answer private to an authorized download of a private file`() {
        // given: a file in a private repository and a token that may read it
        val (repository, gav, file, content) = useDocument(PRIVATE_REPOSITORY, "gav", "artifact.jar", "content", true)
        val (name, secret) = useAuth("cache-reader", "cache-reader-secret", routes = mapOf("/$PRIVATE_REPOSITORY" to READ))

        // when: the file is downloaded with those credentials
        val response = get("$base/$repository/$gav/$file").basicAuth(name, secret).asString()

        // then: the answer is private, so a shared cache does not keep it
        assertThat(response.body).isEqualTo(content)
        assertThat(response.headers.getFirst(CACHE_CONTROL)).isEqualTo("private, max-age=$maxAge")
    }

    @Test
    fun `should refuse an anonymous download of a private file as not cacheable`() {
        // given: a file in a private repository
        val (repository, gav, file) = useDocument(PRIVATE_REPOSITORY, "gav", "artifact.jar", "content", true)

        // when: the file is downloaded without credentials
        val response = get("$base/$repository/$gav/$file").asString()

        // then: the answer is refused and marked as not cacheable
        assertThat(response.status).isEqualTo(401)
        assertThat(response.headers.getFirst(CACHE_CONTROL)).isEqualTo("no-cache, no-store, max-age=0")
    }

    @Test
    fun `should answer private to an anonymous download of a hidden file by its exact path`() {
        // given: a file in a hidden repository
        val (repository, gav, file, content) = useDocument(HIDDEN_REPOSITORY, "gav", "artifact.jar", "content", true)

        // when: the file is downloaded by its exact path without credentials
        val response = get("$base/$repository/$gav/$file").asString()

        // then: the file is served, and the answer is private
        assertThat(response.body).isEqualTo(content)
        assertThat(response.headers.getFirst(CACHE_CONTROL)).isEqualTo("private, max-age=$maxAge")
    }

    @Test
    fun `should keep the public value for a file in a public repository`() {
        // given: a file in a public repository that does not allow redeployment
        val (repository, gav, file, content) = useDocument("immutable", "gav", "artifact.jar", "content", true)

        // when: the file is downloaded without credentials
        val response = get("$base/$repository/$gav/$file").asString()

        // then: the answer stays public
        assertThat(response.body).isEqualTo(content)
        assertThat(response.headers.getFirst(CACHE_CONTROL)).isEqualTo("public, max-age=$maxAge")
    }

    @Test
    fun `should answer private to an anonymous download through a public group with a private member`() {
        // given: a file that only the private member holds, and a public group that reads it with its mirror identity
        val (_, gav, file, content) = useDocument(PRIVATE_MEMBER, "gav", "artifact.jar", "content", true)
        useAuth(MIRROR_LOGIN, MIRROR_SECRET, routes = mapOf("/$PRIVATE_MEMBER" to READ))

        // when: the file is downloaded through the group without credentials
        val response = get("$base/$GROUP_REPOSITORY/$gav/$file").asString()

        // then: the answer carries the private scope of the member
        assertThat(response.body).isEqualTo(content)
        assertThat(response.headers.getFirst(CACHE_CONTROL)).isEqualTo("private, max-age=$maxAge")
    }

    @Test
    fun `should answer private to range and conditional answers of a private file`() {
        // given: an authorized client and a private file with a known entity tag
        val (repository, gav, file) = useDocument(PRIVATE_REPOSITORY, "ranged", "artifact.jar", PRIVATE_CONTENT, true)
        val (name, secret) = useAuth("cache-range-reader", "cache-range-reader-secret", routes = mapOf("/$PRIVATE_REPOSITORY" to READ))
        val url = "$base/$repository/$gav/$file"
        val full = get(url).basicAuth(name, secret).asString()
        val etag = full.headers.getFirst(ETAG)

        // when: a range of the file and a revalidation are requested
        val partial = get(url).basicAuth(name, secret).header("Range", "bytes=0-9").asString()
        val notModified = get(url).basicAuth(name, secret).header("If-None-Match", etag).asString()
        val headResponse = head(url).basicAuth(name, secret).asEmpty()

        // then: each answer carries the same private value as the full answer
        assertThat(partial.status).isEqualTo(206)
        assertThat(partial.headers.getFirst(CACHE_CONTROL)).isEqualTo(full.headers.getFirst(CACHE_CONTROL))
        assertThat(notModified.status).isEqualTo(304)
        assertThat(notModified.headers.getFirst(CACHE_CONTROL)).isEqualTo(full.headers.getFirst(CACHE_CONTROL))
        assertThat(headResponse.headers.getFirst(CACHE_CONTROL)).isEqualTo(full.headers.getFirst(CACHE_CONTROL))
        assertThat(full.headers.getFirst(CACHE_CONTROL)).isEqualTo("private, max-age=$maxAge")
    }

    @Test
    fun `should keep no-cache on maven metadata in a public repository`() {
        // given: a maven-metadata.xml in a public repository
        val (repository, gav, file) = useDocument("releases", "gav", "maven-metadata.xml", "metadata", true)

        // when: the metadata is downloaded without credentials
        val response = get("$base/$repository/$gav/$file").asString()

        // then: the answer is not cacheable, as before
        assertThat(response.headers.getFirst(CACHE_CONTROL)).isEqualTo("no-cache, no-store, max-age=0")
    }

}
