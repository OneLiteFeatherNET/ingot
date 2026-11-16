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
import com.reposilite.SharedContainers
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.s3Endpoint
import com.reposilite.storage.s3.S3PresignConfig
import com.reposilite.storage.s3.S3StorageProviderSettings
import com.reposilite.token.AccessTokenPermission.MANAGER
import kong.unirest.core.Unirest.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

internal const val S3_PUBLIC_REPOSITORY = "presigned-public"
internal const val S3_PRIVATE_REPOSITORY = "presigned-private"
internal const val S3_ARTIFACT = "com/example/app/1.0/app-1.0.jar"
internal const val S3_CONTENT = "the bytes of the release in the bucket"

/** Settings of the two S3 repositories, one bucket of its own per test class instance. */
internal fun s3RepositorySettings(bucket: String): List<RepositorySettings> = listOf(
    RepositorySettings(id = S3_PUBLIC_REPOSITORY, visibility = RepositoryVisibility.PUBLIC, storageProvider = s3Storage(bucket, "public")),
    RepositorySettings(id = S3_PRIVATE_REPOSITORY, visibility = RepositoryVisibility.PRIVATE, storageProvider = s3Storage(bucket, "private")),
)

/** Two repositories share the bucket, so each has a key prefix of its own: the server refuses overlapping key spaces. */
private fun s3Storage(bucket: String, prefix: String) = S3StorageProviderSettings(
    bucketName = bucket,
    prefix = prefix,
    endpoint = SharedContainers.floci.s3Endpoint(),
    accessKey = "test",
    secretKey = "test",
    region = "us-east-1",
)

/** A client that reports a redirect instead of following it, so the test sees the answer of Ingot itself. */
internal fun noRedirectClient(): HttpClient =
    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

internal fun basicAuthorization(name: String, secret: String): String =
    "Basic " + Base64.getEncoder().encodeToString("$name:$secret".toByteArray())

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class S3PresignedRedirectIntegrationTest : ReposiliteSpecification() {

    private val bucket = IsolatedResources.newBucketName("presigned-redirect")

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(repositories = settings.repositories + s3RepositorySettings(bucket))
        }
    }

    override fun presignConfig(): S3PresignConfig =
        S3PresignConfig(enabled = true, ttl = Duration.ofSeconds(300), endpoint = null)

    @Test
    fun `should redirect an anonymous download of a public file to a presigned url on the bucket`() {
        // given: a public release stored in the bucket
        val (name, secret) = useAuth("deployer", "deployer-secret", listOf(MANAGER))
        assertThat(put("$base/$S3_PUBLIC_REPOSITORY/$S3_ARTIFACT").basicAuth(name, secret).body(S3_CONTENT).asString().status)
            .describedAs("status of the upload").isEqualTo(200)

        // when: the file is downloaded without credentials
        val response = noRedirectClient().send(
            HttpRequest.newBuilder(URI.create("$base/$S3_PUBLIC_REPOSITORY/$S3_ARTIFACT")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        // then: the answer is a 302 whose Location is on the bucket endpoint, and that URL serves the bytes
        assertThat(response.statusCode()).describedAs("status of the download with the switch on").isEqualTo(302)
        val location = response.headers().firstValue("Location").orElseThrow { AssertionError("no Location header") }
        assertThat(location)
            .describedAs("Location of the redirect")
            .contains(URI.create(SharedContainers.floci.s3Endpoint()).authority)
        assertThat(noRedirectClient().send(HttpRequest.newBuilder(URI.create(location)).GET().build(), HttpResponse.BodyHandlers.ofString()).body())
            .describedAs("bytes behind the presigned URL")
            .isEqualTo(S3_CONTENT)
    }

    @Test
    fun `should answer an authorized download of a private file with the bytes and no redirect`() {
        // given: a private release stored in the bucket
        val (name, secret) = useAuth("deployer", "deployer-secret", listOf(MANAGER))
        assertThat(put("$base/$S3_PRIVATE_REPOSITORY/$S3_ARTIFACT").basicAuth(name, secret).body(S3_CONTENT).asString().status)
            .describedAs("status of the upload").isEqualTo(200)

        // when: the file is downloaded with credentials that may read it
        val response = noRedirectClient().send(
            HttpRequest.newBuilder(URI.create("$base/$S3_PRIVATE_REPOSITORY/$S3_ARTIFACT"))
                .header("Authorization", basicAuthorization(name, secret))
                .GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        // then: the instance serves the bytes, and no presigned URL is given
        assertThat(response.statusCode()).describedAs("status of the authorized private download").isEqualTo(200)
        assertThat(response.body()).describedAs("body of the authorized private download").isEqualTo(S3_CONTENT)
        assertThat(response.headers().firstValue("Location").isPresent).describedAs("Location of the private download").isFalse
    }

    @Test
    fun `should refuse an anonymous download of a private file without a presigned url`() {
        // given: a private release stored in the bucket
        val (name, secret) = useAuth("deployer", "deployer-secret", listOf(MANAGER))
        assertThat(put("$base/$S3_PRIVATE_REPOSITORY/$S3_ARTIFACT").basicAuth(name, secret).body(S3_CONTENT).asString().status)
            .describedAs("status of the upload").isEqualTo(200)

        // when: the file is downloaded without credentials
        val response = noRedirectClient().send(
            HttpRequest.newBuilder(URI.create("$base/$S3_PRIVATE_REPOSITORY/$S3_ARTIFACT")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        // then: the request is refused, and no presigned URL is given
        assertThat(response.statusCode()).describedAs("status of the anonymous private download").isEqualTo(401)
        assertThat(response.headers().firstValue("Location").isPresent).describedAs("Location of the refused download").isFalse
    }

}
