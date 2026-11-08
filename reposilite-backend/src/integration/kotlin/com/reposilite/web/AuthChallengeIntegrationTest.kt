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

package com.reposilite.web

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import io.javalin.http.HttpStatus.UNAUTHORIZED
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The challenge a 401 carries. Browsers show a login popup for Basic only, so the API answers with Bearer, and the
 * repositories keep Basic for Maven and Gradle clients.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class AuthChallengeIntegrationTest : ReposiliteSpecification() {

    @Test
    fun `should challenge an unauthenticated api request with bearer and not with basic`() {
        // when: an anonymous request reaches a route that needs a manager
        val response = Unirest.get("$base/api/settings/domain/maven").asEmpty()

        // then: the 401 names the bearer scheme, so no browser shows a login popup
        assertThat(response.status).isEqualTo(UNAUTHORIZED.code)
        assertThat(response.headers.getFirst("WWW-Authenticate")).isEqualTo("Bearer realm=\"Ingot\"")
    }

    @Test
    fun `should challenge an unauthenticated repository upload with basic`() {
        // when: an anonymous upload reaches a repository
        val response = Unirest.put("$base/releases/com/example/app/1.0/app-1.0.jar")
            .body("content")
            .asEmpty()

        // then: the repository keeps the basic challenge that Maven and Gradle clients answer
        assertThat(response.status).isEqualTo(UNAUTHORIZED.code)
        assertThat(response.headers.getFirst("WWW-Authenticate")).isEqualTo("Basic realm=\"Ingot\", charset=\"UTF-8\"")
    }

}
