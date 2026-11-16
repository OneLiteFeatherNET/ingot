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

package com.reposilite.storage.s3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.time.Duration

/** Presigning is a local signature, so these tests need no S3 server and read no clock: the signing date is never asserted. */
internal class S3PresignedUrlTest {

    private val presigner = S3Presigner.builder()
        .region(Region.US_EAST_1)
        .endpointOverride(URI.create("https://storage.example.test"))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("access", "secret")))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()

    @Test
    fun `should presign a get url that names the endpoint, the bucket and the key`() {
        val url = URI.create(presigner.presignedGetUrl(bucket = "releases", key = "com/example/app/1.0/app-1.0.jar", ttl = Duration.ofSeconds(300)))

        assertThat(url.scheme).describedAs("scheme of the presigned url").isEqualTo("https")
        assertThat(url.host).describedAs("host of the presigned url").isEqualTo("storage.example.test")
        assertThat(url.path).describedAs("path of the presigned url").isEqualTo("/releases/com/example/app/1.0/app-1.0.jar")
    }

    @Test
    fun `should presign a url that expires after the configured lifetime in seconds`() {
        val url = URI.create(presigner.presignedGetUrl(bucket = "releases", key = "app.jar", ttl = Duration.ofSeconds(300)))

        assertThat(url.query).describedAs("query of the presigned url").contains("X-Amz-Expires=300")
    }

}
