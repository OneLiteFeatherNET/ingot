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
import com.reposilite.ReposiliteObjectMapper
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.token.AccessTokenPermission.MANAGER
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.put
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val CHECKED_REPOSITORY = "checked"

/**
 * The deploy endpoint of a running server with a repository under the reject policy. The uploads go over HTTP in the order
 * Maven uses: the artifact first, then its checksum file.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class UploadChecksumIntegrationTest : ReposiliteSpecification() {

    private val artifactPath = "com/example/lib/1.0.0/lib-1.0.0.jar"
    private val artifactContent = "the bytes of the library"

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + RepositorySettings(
                    id = CHECKED_REPOSITORY,
                    uploadChecksumPolicy = UploadChecksumPolicy.REJECT,
                )
            )
        }
    }

    @Test
    fun `should store a matching checksum file after its artifact over HTTP`() {
        // given: a deployer that may write to the repository
        val (name, secret) = useAuth("deployer", "deployer-secret", listOf(MANAGER))

        // when: the artifact and its matching sha1 file are uploaded in that order
        assertThat(put("$base/$CHECKED_REPOSITORY/$artifactPath").basicAuth(name, secret).body(artifactContent).asString().status)
            .describedAs("status of the artifact upload").isEqualTo(200)
        val checksum = put("$base/$CHECKED_REPOSITORY/$artifactPath.sha1").basicAuth(name, secret)
            .body(DigestUtils.sha1Hex(artifactContent)).asString()

        // then: the checksum file is stored
        assertThat(checksum.status).describedAs("status of the matching checksum upload").isEqualTo(200)
        assertThat(get("$base/$CHECKED_REPOSITORY/$artifactPath.sha1").asString().body)
            .describedAs("served checksum file")
            .isEqualTo(DigestUtils.sha1Hex(artifactContent))
    }

    @Test
    fun `should answer 400 with the problem type and store nothing for a mismatching checksum file over HTTP`() {
        // given: a deployer that may write to the repository and an artifact that is stored
        val (name, secret) = useAuth("deployer", "deployer-secret", listOf(MANAGER))
        put("$base/$CHECKED_REPOSITORY/$artifactPath").basicAuth(name, secret).body(artifactContent).asString()

        // when: a sha1 file with the digest of other bytes is uploaded
        val refused = put("$base/$CHECKED_REPOSITORY/$artifactPath.sha1").basicAuth(name, secret)
            .body(DigestUtils.sha1Hex("bytes of another file")).asString()

        // then: the answer is 400 with the problem type of a checksum refusal
        assertThat(refused.status).describedAs("status of the refused checksum upload").isEqualTo(400)
        val problem = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(refused.body)
        assertThat(problem.get("type").asText()).describedAs("problem type of the refusal")
            .isEqualTo(UPLOAD_CHECKSUM_MISMATCH_PROBLEM_TYPE)

        // and: no checksum file is served, and the artifact is still the one that was uploaded
        assertThat(get("$base/$CHECKED_REPOSITORY/$artifactPath.sha1").asString().status)
            .describedAs("status of the refused checksum file").isEqualTo(404)
        assertThat(get("$base/$CHECKED_REPOSITORY/$artifactPath").asString().body)
            .describedAs("artifact after the refusal").isEqualTo(artifactContent)
    }

}
