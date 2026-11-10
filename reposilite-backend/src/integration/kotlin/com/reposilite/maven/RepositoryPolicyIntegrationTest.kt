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

package com.reposilite.maven

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.shared.ErrorResponse
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.METHOD_NOT_ALLOWED
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The version policy and the read-only flag as an HTTP client sees them. Every test uses its own paths, so the
 * repositories of this instance are only written by the test that owns them.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RepositoryPolicyIntegrationTest : ReposiliteSpecification() {

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + listOf(
                    RepositorySettings(id = "policy-release", versionPolicy = VersionPolicy.RELEASE),
                    RepositorySettings(id = "policy-frozen", readOnly = true),
                )
            )
        }
    }

    @Test
    fun `should answer 400 with the policy to a snapshot deploy into a release repository`() {
        // given: a manager
        val (name, secret) = useDefaultManagementToken()

        // when: a snapshot is uploaded into the release repository
        val response = Unirest.put("$base/policy-release/com/example/app/1.0-SNAPSHOT/app-1.0-20260101.jar")
            .basicAuth(name, secret)
            .body("content")
            .asJacksonObject(ErrorResponse::class)

        // then: the upload is refused with 400 and the message names the repository and the policy
        assertErrorResponse(BAD_REQUEST, response)
        assertThat(response.body.message).isEqualTo("Repository 'policy-release' has version policy RELEASE and does not accept the snapshot version 1.0-SNAPSHOT")
    }

    @Test
    fun `should accept a release upload into the release repository`() {
        // given: a manager
        val (name, secret) = useDefaultManagementToken()

        // when: a release is uploaded
        val response = Unirest.put("$base/policy-release/com/example/app/2.0/app-2.0.jar")
            .basicAuth(name, secret)
            .body("content")
            .asEmpty()

        // then: the upload is accepted
        assertThat(response.status).isEqualTo(OK.code)
    }

    @Test
    fun `should answer 405 with the read-only message to an upload into a read-only repository`() {
        // given: a manager
        val (name, secret) = useDefaultManagementToken()

        // when: a file is uploaded into the read-only repository
        val response = Unirest.put("$base/policy-frozen/com/example/app/1.0/app-1.0.jar")
            .basicAuth(name, secret)
            .body("content")
            .asJacksonObject(ErrorResponse::class)

        // then: the upload is refused with 405 and the message names the repository
        assertErrorResponse(METHOD_NOT_ALLOWED, response)
        assertThat(response.body.message).isEqualTo("Repository 'policy-frozen' is read-only and does not accept deployments or deletions")
    }

}
