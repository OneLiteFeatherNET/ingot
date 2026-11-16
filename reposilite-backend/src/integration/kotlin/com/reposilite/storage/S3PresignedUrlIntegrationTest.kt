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

package com.reposilite.storage

import com.reposilite.IsolatedResources
import com.reposilite.SharedContainers
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.s3Endpoint
import com.reposilite.status.FailureFacade
import com.reposilite.storage.api.toLocation
import com.reposilite.storage.s3.S3PresignConfig
import com.reposilite.storage.s3.S3StorageProviderFactory
import com.reposilite.storage.s3.S3StorageProviderSettings
import kong.unirest.core.Unirest.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.ResultAssertions.assertOk
import java.io.File
import java.time.Duration

/** Runs against the shared Floci instance: the presigned URL is fetched over HTTP, so the signature is checked by the server. */
internal class S3PresignedUrlIntegrationTest {

    @TempDir
    lateinit var rootDirectory: File

    // JUnit creates a new instance per test method, so every test gets a bucket of its own on the shared Floci.
    private val bucket = IsolatedResources.newBucketName("presign-test")
    private val artifact = "/com/example/app/1.0/app-1.0.jar".toLocation()
    private val content = "the bytes of the release"

    private fun providerWith() = S3StorageProviderFactory(
        presign = S3PresignConfig(enabled = true, ttl = Duration.ofSeconds(60), endpoint = null),
    ).create(
        InMemoryLogger(),
        FailureFacade(InMemoryLogger()),
        rootDirectory.toPath(),
        "presign-repository",
        S3StorageProviderSettings(
            bucketName = bucket,
            endpoint = SharedContainers.floci.s3Endpoint(),
            accessKey = "test",
            secretKey = "test",
            region = "us-east-1",
        ),
    )

    @Test
    fun `should presign a url that serves the stored bytes to a client without credentials`() {
        // given: a stored release file and a provider with the redirects on
        val provider = providerWith()
        assertOk(provider.putFile(artifact, content.toByteArray().inputStream()))

        // when: the presigned url is fetched without any credentials
        val url = provider.presignedGetUrl(artifact, Duration.ofSeconds(60))

        // then: the bucket answers with the stored bytes
        assertThat(url).describedAs("presigned url of a stored file").isNotNull()
        assertThat(get(url).asString().body).describedAs("bytes behind the presigned url").isEqualTo(content)
    }

    @Test
    fun `should answer no url for a file that is not stored`() {
        // given: a provider with the redirects on and no stored file
        val provider = providerWith()

        // when / then: the instance answers the request, so the redirect is not made
        assertThat(provider.presignedGetUrl(artifact, Duration.ofSeconds(60)))
            .describedAs("presigned url of a missing file").isNull()
    }

}
