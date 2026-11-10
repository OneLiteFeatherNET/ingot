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
import com.reposilite.storage.s3.S3StorageProviderSettings
import com.reposilite.token.AccessTokenPermission.MANAGER
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.put
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val S3_CHECKED_REPOSITORY = "checked-s3"

/**
 * The same refusal on a repository whose storage is S3. The check reads the stored artifact from the bucket, so the
 * refusal has to hold there as well: no checksum object is written.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class UploadChecksumS3IntegrationTest : ReposiliteSpecification() {

    private val bucket = IsolatedResources.newBucketName("upload-checksum")
    private val artifactPath = "com/example/lib/1.0.0/lib-1.0.0.jar"
    private val artifactContent = "the bytes of the library in S3"

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + RepositorySettings(
                    id = S3_CHECKED_REPOSITORY,
                    uploadChecksumPolicy = UploadChecksumPolicy.REJECT,
                    storageProvider = S3StorageProviderSettings(
                        bucketName = bucket,
                        endpoint = SharedContainers.floci.s3Endpoint(),
                        accessKey = "test",
                        secretKey = "test",
                        region = "us-east-1",
                    ),
                )
            )
        }
    }

    @Test
    fun `should refuse a mismatching checksum file on S3 storage and write no checksum object`() {
        // given: a deployer and an artifact that is stored in the bucket
        val (name, secret) = useAuth("deployer", "deployer-secret", listOf(MANAGER))
        assertThat(put("$base/$S3_CHECKED_REPOSITORY/$artifactPath").basicAuth(name, secret).body(artifactContent).asString().status)
            .describedAs("status of the artifact upload to S3").isEqualTo(200)

        // when: a sha1 file with the digest of other bytes is uploaded
        val refused = put("$base/$S3_CHECKED_REPOSITORY/$artifactPath.sha1").basicAuth(name, secret)
            .body(DigestUtils.sha1Hex("bytes of another file")).asString()

        // then: the answer is 400, no checksum object is served, and the artifact is unchanged
        assertThat(refused.status).describedAs("status of the refused upload to S3").isEqualTo(400)
        assertThat(get("$base/$S3_CHECKED_REPOSITORY/$artifactPath.sha1").asString().status)
            .describedAs("status of the refused checksum object").isEqualTo(404)
        assertThat(get("$base/$S3_CHECKED_REPOSITORY/$artifactPath").asString().body)
            .describedAs("artifact in S3 after the refusal").isEqualTo(artifactContent)
    }

}
