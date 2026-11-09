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
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenIntegrationSpecification
import com.reposilite.shared.extensions.maxAge
import kong.unirest.core.HeaderNames.CACHE_CONTROL
import kong.unirest.core.HeaderNames.CONTENT_LENGTH
import kong.unirest.core.HeaderNames.CONTENT_TYPE
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class MavenErrorCachingIntegrationTest : MavenIntegrationSpecification() {

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + RepositorySettings(id = PRIVATE_REPOSITORY, visibility = RepositoryVisibility.PRIVATE)
            )
        }
    }

    @Test
    fun `should vary a refused anonymous upload on accept`() {
        // when: an anonymous upload reaches a repository that refuses it
        val response = put("$base/releases/com/example/app/1.0/app-1.0.jar").body("content").asEmpty()

        // then: the refusal is negotiated on Accept, so the answer varies on it
        assertThat(response.status).isEqualTo(401)
        assertThat(response.headers.getFirst("Vary")).describedAs("Vary of a refused upload").contains("Accept")
    }

    @Test
    fun `should answer a refused anonymous upload as not cacheable and keep its basic challenge`() {
        // when: an anonymous upload reaches a repository that refuses it
        val response = put("$base/releases/com/example/app/1.0/app-1.0.jar").body("content").asEmpty()

        // then: a shared cache revalidates the refusal, and the challenge that Maven clients answer is still sent
        assertThat(response.status).isEqualTo(401)
        assertThat(response.headers.getFirst(CACHE_CONTROL)).describedAs("Cache-Control of a refused upload").isEqualTo("no-cache")
        assertThat(response.headers.getFirst("WWW-Authenticate")).isEqualTo("Basic realm=\"Ingot\", charset=\"UTF-8\"")
    }

    @Test
    fun `should answer a refused anonymous download of a private file as not cacheable and keep its basic challenge`() {
        // given: a file in a private repository
        val (repository, gav, file) = useDocument(PRIVATE_REPOSITORY, "gav", "artifact.jar", "content", true)

        // when: the file is downloaded without credentials
        val response = get("$base/$repository/$gav/$file").asString()

        // then: the refusal is not cacheable and still carries the basic challenge
        assertThat(response.status).isEqualTo(401)
        assertThat(response.headers.getFirst(CACHE_CONTROL)).describedAs("Cache-Control of a refused download").contains("no-cache")
        assertThat(response.headers.getFirst("WWW-Authenticate")).isEqualTo("Basic realm=\"Ingot\", charset=\"UTF-8\"")
    }

    @Test
    fun `should answer a missing repository file as not cacheable html`() {
        // when: a file that the repository does not contain is requested without credentials
        val response = get("$base/releases/com/example/missing/1.0/missing-1.0.jar").asString()

        // then: the html page is answered as before, and a shared cache revalidates the miss
        assertThat(response.status).isEqualTo(404)
        assertThat(response.headers.getFirst(CONTENT_TYPE)).startsWith("text/html")
        assertThat(response.headers.getFirst(CACHE_CONTROL)).describedAs("Cache-Control of a missing file").isEqualTo("no-cache")
    }

    @Test
    fun `should keep the cache header, type and length of a successful download`() {
        // given: a file in a public repository that does not allow redeployment
        val (repository, gav, file, content) = useDocument("immutable", "gav", "artifact.jar", "content", true)

        // when: the file is downloaded without credentials
        val response = get("$base/$repository/$gav/$file").asString()

        // then: the answer is served with the same headers as before the error caching change
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo(content)
        assertThat(response.headers.getFirst(CACHE_CONTROL)).isEqualTo("public, max-age=$maxAge")
        assertThat(response.headers.getFirst(CONTENT_LENGTH)).isEqualTo(content.length.toString())
    }

    private companion object {
        const val PRIVATE_REPOSITORY = "error-cache-private"
    }

}
