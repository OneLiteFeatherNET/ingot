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

package com.reposilite.login

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class AnonymousSessionCookieIntegrationTest : ReposiliteSpecification() {

    /** A client without cookie management, so that no cookie of an earlier response is sent along. */
    private val http = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }

    @Test
    fun `an anonymous request to the login providers sets no session cookie`() {
        // when: the login providers are requested without any cookie
        val response = http.get("$base/api/auth/providers").asString()

        // then: the request is served, and no session, refresh or csrf cookie is set
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.headers.get("Set-Cookie")).describedAs("no cookie before a successful login").noneMatch { it.startsWith("ingot_") }
    }

    @Test
    fun `an anonymous request for the current session sets no session cookie`() {
        // when: the current session is requested without any cookie
        val response = http.get("$base/api/auth/me").asString()

        // then: the request is refused, and no session, refresh or csrf cookie is set
        assertThat(response.headers.get("Set-Cookie")).describedAs("no cookie for an anonymous request").noneMatch { it.startsWith("ingot_") }
    }

}
