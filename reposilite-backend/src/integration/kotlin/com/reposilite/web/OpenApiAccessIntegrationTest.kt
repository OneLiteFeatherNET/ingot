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

package com.reposilite.web

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Records how the server answers `GET /openapi` to a caller without credentials and to a caller with an access token.
 * The reference pages state the same answer, so a change here must change the pages too.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class OpenApiAccessIntegrationTest : ReposiliteSpecification() {

    @Test
    fun `answers GET openapi without credentials`() {
        val response = Unirest.get("$base/openapi").asString()

        assertThat(response.status)
            .describedAs("status of GET /openapi without credentials")
            .isEqualTo(OK.code)
    }

    @Test
    fun `answers GET openapi with an access token`() {
        val (name, secret) = useAuth("openapi-reader", "secret")

        val response = Unirest.get("$base/openapi").basicAuth(name, secret).asString()

        assertThat(response.status)
            .describedAs("status of GET /openapi with an access token")
            .isEqualTo(OK.code)
    }

}
