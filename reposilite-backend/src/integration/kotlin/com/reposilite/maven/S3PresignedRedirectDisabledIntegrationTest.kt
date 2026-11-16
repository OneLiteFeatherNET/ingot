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

import com.reposilite.IsolatedResources
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.token.AccessTokenPermission.MANAGER
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** The same S3 repositories with the switch left off, which is the default: the download is served by Ingot, as before. */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class S3PresignedRedirectDisabledIntegrationTest : ReposiliteSpecification() {

    private val bucket = IsolatedResources.newBucketName("presigned-off")

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(repositories = settings.repositories + s3RepositorySettings(bucket))
        }
    }

    @Test
    fun `should serve an anonymous download of a public file from the instance when the switch is off`() {
        // given: a public release stored in the bucket
        val (name, secret) = useAuth("deployer", "deployer-secret", listOf(MANAGER))
        assertThat(put("$base/$S3_PUBLIC_REPOSITORY/$S3_ARTIFACT").basicAuth(name, secret).body(S3_CONTENT).asString().status)
            .describedAs("status of the upload").isEqualTo(200)

        // when: the file is downloaded without credentials
        val response = noRedirectClient().send(
            HttpRequest.newBuilder(URI.create("$base/$S3_PUBLIC_REPOSITORY/$S3_ARTIFACT")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        // then: the instance answers with the bytes and no redirect
        assertThat(response.statusCode()).describedAs("status of the download with the switch off").isEqualTo(200)
        assertThat(response.body()).describedAs("body of the download with the switch off").isEqualTo(S3_CONTENT)
        assertThat(response.headers().firstValue("Location").isPresent).describedAs("Location with the switch off").isFalse
    }

}
