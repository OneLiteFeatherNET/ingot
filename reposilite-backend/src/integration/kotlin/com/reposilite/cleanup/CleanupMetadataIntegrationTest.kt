/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.cleanup

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.RecommendedRemoteSpecificationJunitExtension
import com.reposilite.audit.api.AuditAction.ARTIFACT_DELETED
import com.reposilite.audit.api.AuditAction.CLEANUP_RUN
import com.reposilite.audit.api.AuditPageDto
import com.reposilite.maven.api.Metadata
import com.reposilite.maven.api.SaveMetadataRequest
import com.reposilite.maven.api.Versioning
import com.reposilite.maven.specification.MavenIntegrationSpecification
import com.reposilite.storage.api.toLocation
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalCleanupMetadataIntegrationTest : CleanupMetadataIntegrationTest()

@ExtendWith(RecommendedRemoteSpecificationJunitExtension::class)
internal class RemoteCleanupMetadataIntegrationTest : CleanupMetadataIntegrationTest()

internal abstract class CleanupMetadataIntegrationTest : MavenIntegrationSpecification() {

    private val repository = "releases"
    private val artifact = "com/example/app"
    private val artifactMetadata = "$artifact/maven-metadata.xml"

    @Test
    fun `should keep artifact metadata consistent after a manual run deletes old versions`() {
        // given: versions 1.0, 1.1 and 1.2 listed in the artifact metadata, and a policy that keeps only the newest release
        deployVersions("1.0", "1.1", "1.2")
        saveArtifactMetadata(versions = listOf("1.0", "1.1", "1.2"), latest = "1.2", release = "1.2")
        configureKeepNewestPolicy()
        val (token, secret) = useDefaultManagementToken()

        // when: the policy is run by hand
        val run = Unirest.post("$base/api/cleanup/policies/keep-newest/run").basicAuth(token, secret).asEmpty()

        // then: the run succeeded and the metadata describes the one version that is left
        assertThat(run.status).`as`("manual run of keep-newest").isEqualTo(OK.code)
        assertMetadataDescribes(versions = listOf("1.2"), latest = "1.2", release = "1.2")
    }

    @Test
    fun `should keep artifact metadata consistent when the scheduler deletes a version without a token`() {
        // given: versions 1.0 and 1.1 listed in the artifact metadata
        deployVersions("1.0", "1.1")
        saveArtifactMetadata(versions = listOf("1.0", "1.1"), latest = "1.1", release = "1.1")

        // when: the scheduled run deletes the older version through the unchecked path, which has no access token
        val deleted = mavenFacade.deleteUnchecked(mavenFacade.getRepository(repository)!!, "$artifact/1.0".toLocation(), "cleanup-scheduler")

        // then: the deletion succeeded and the metadata describes the version that is left
        assertThat(deleted.isOk).`as`("unchecked delete of 1.0").isTrue
        assertMetadataDescribes(versions = listOf("1.1"), latest = "1.1", release = "1.1")
    }

    @Test
    fun `should record a manual run as one cleanup run event instead of one event per deleted version`() {
        // given: versions 1.0, 1.1 and 1.2 of the artifact, and a policy that keeps only the newest release
        deployVersions("1.0", "1.1", "1.2")
        configureKeepNewestPolicy()
        val (token, secret) = useDefaultManagementToken()

        // when: the policy is run by hand, which deletes the two older versions
        val run = Unirest.post("$base/api/cleanup/policies/keep-newest/run").basicAuth(token, secret).asEmpty()
        assertThat(run.status).`as`("manual run of keep-newest").isEqualTo(OK.code)

        // then: the audit log holds one cleanup run event with the policy and its totals
        val page = Unirest.get("$base/api/audit?action=CLEANUP_RUN").basicAuth(token, secret).asJacksonObject(AuditPageDto::class).body
        assertThat(page.events).`as`("cleanup run events").hasSize(1)
        val event = page.events.single()
        assertThat(event.action).isEqualTo(CLEANUP_RUN)
        assertThat(event.actor).isEqualTo("manager")
        assertThat(event.success).isTrue
        assertThat(event.details.toString()).contains("keep-newest").contains("\"deletedCount\":2")

        // and: the deletions of the run are not written as artifact deletions
        val deletions = Unirest.get("$base/api/audit?action=ARTIFACT_DELETED").basicAuth(token, secret).asJacksonObject(AuditPageDto::class).body
        assertThat(deletions.events).`as`("artifact deletion events of the run").isEmpty()
    }

    private fun deployVersions(vararg versions: String) {
        versions.forEach { version ->
            useDocument(repository, "$artifact/$version", "app-$version.pom", "<project/>", store = true)
            useDocument(repository, "$artifact/$version", "app-$version.jar", "jar-content-$version", store = true)
        }
    }

    private fun configureKeepNewestPolicy() {
        val (token, secret) = useDefaultManagementToken()
        val body = """{"policies":[{"id":"keep-newest","enabled":false,"repositories":["$repository"],"scope":"RELEASES","keepLatest":1}]}"""
        val response = Unirest.put("$base/api/settings/domain/cleanup").basicAuth(token, secret).body(body).asEmpty()
        assertThat(response.status).`as`("policy update").isEqualTo(OK.code)
    }

    private fun saveArtifactMetadata(versions: List<String>, latest: String, release: String?) {
        mavenFacade.saveMetadata(
            SaveMetadataRequest(
                repository = mavenFacade.getRepository(repository)!!,
                gav = artifact.toLocation(),
                metadata = Metadata(
                    groupId = "com.example",
                    artifactId = "app",
                    versioning = Versioning(latest = latest, release = release, _versions = versions, lastUpdated = "20260101000000")
                )
            )
        )
    }

    private fun assertMetadataDescribes(versions: List<String>, latest: String, release: String) {
        val metadata = mavenFacade.findMetadata(mavenFacade.getRepository(repository)!!, artifact.toLocation()).get()
        assertThat(metadata.versioning?.versions).`as`("versions in maven-metadata.xml").containsExactlyElementsOf(versions)
        assertThat(metadata.versioning?.latest).`as`("latest in maven-metadata.xml").isEqualTo(latest)
        assertThat(metadata.versioning?.release).`as`("release in maven-metadata.xml").isEqualTo(release)

        val content = Unirest.get("$base/$repository/$artifactMetadata").asString().body
        assertThat(Unirest.get("$base/$repository/$artifactMetadata.md5").asString().body).`as`("md5").isEqualTo(DigestUtils.md5Hex(content))
        assertThat(Unirest.get("$base/$repository/$artifactMetadata.sha1").asString().body).`as`("sha1").isEqualTo(DigestUtils.sha1Hex(content))
        assertThat(Unirest.get("$base/$repository/$artifactMetadata.sha256").asString().body).`as`("sha256").isEqualTo(DigestUtils.sha256Hex(content))
        assertThat(Unirest.get("$base/$repository/$artifactMetadata.sha512").asString().body).`as`("sha512").isEqualTo(DigestUtils.sha512Hex(content))
    }

}
