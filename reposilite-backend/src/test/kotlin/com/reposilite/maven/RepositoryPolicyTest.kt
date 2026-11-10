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

import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.api.DeleteRequest
import com.reposilite.maven.api.DeployRequest
import com.reposilite.maven.api.GeneratePomRequest
import com.reposilite.maven.api.PomDetails
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertOk

internal class RepositoryPolicyTest : MavenSpecification() {

    override fun repositories() = listOf(
        // A repository that sets neither new field, as every existing installation has it
        RepositorySettings("plain", visibility = PUBLIC),
        RepositorySettings("release-only", visibility = PUBLIC, versionPolicy = VersionPolicy.RELEASE),
        RepositorySettings("snapshot-only", visibility = PUBLIC, versionPolicy = VersionPolicy.SNAPSHOT),
        RepositorySettings("frozen", visibility = PUBLIC, readOnly = true),
        RepositorySettings("typed-proxy", visibility = PUBLIC, type = RepositoryType.PROXY, proxied = mutableListOf(
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY),
        )),
        RepositorySettings("typed-group", visibility = PUBLIC, type = RepositoryType.GROUP, proxied = mutableListOf(
            MirroredRepositorySettings(reference = "plain"),
        )),
    )

    @Nested
    inner class Effective {

        @Test
        fun `should resolve an absent version policy to mixed`() {
            assertThat(mavenFacade.getRepository("plain")!!.versionPolicy)
                .describedAs("version policy of a repository that sets none")
                .isEqualTo(VersionPolicy.MIXED)
        }

        @Test
        fun `should resolve an absent read-only flag to false`() {
            assertThat(mavenFacade.getRepository("plain")!!.readOnly)
                .describedAs("read-only flag of a repository that sets none")
                .isFalse
        }

        @Test
        fun `should keep an explicit version policy of the repository`() {
            assertThat(mavenFacade.getRepository("release-only")!!.versionPolicy).isEqualTo(VersionPolicy.RELEASE)
            assertThat(mavenFacade.getRepository("snapshot-only")!!.versionPolicy).isEqualTo(VersionPolicy.SNAPSHOT)
        }

        @Test
        fun `should keep an explicit read-only flag of the repository`() {
            assertThat(mavenFacade.getRepository("frozen")!!.readOnly).isTrue
        }
    }

    @Nested
    inner class Enforcement {

        private val releaseArtifact = "com/example/app/1.0/app-1.0.jar"
        private val snapshotArtifact = "com/example/app/1.0-SNAPSHOT/app-1.0-20260101.jar"

        @Test
        fun `should refuse a snapshot deploy into a release repository and name the repository and the policy`() {
            // when: a snapshot is deployed into the release repository
            val result = mavenFacade.deployFile(deploy("release-only", snapshotArtifact))

            // then: the deploy is refused with 400 and the message names the repository, the policy and the version
            assertThat(result.isErr).describedAs("snapshot deploy into a release repository is refused").isTrue
            assertThat(result.error.status).isEqualTo(400)
            assertThat(result.error.message).isEqualTo("Repository 'release-only' has version policy RELEASE and does not accept the snapshot version 1.0-SNAPSHOT")
        }

        @Test
        fun `should store nothing when a snapshot deploy into a release repository is refused`() {
            // when: a snapshot is deployed into the release repository
            mavenFacade.deployFile(deploy("release-only", snapshotArtifact))

            // then: the file does not exist afterwards
            assertThat(repo("release-only").storageProvider.exists(snapshotArtifact.toLocation()))
                .describedAs("stored snapshot after a refused deploy")
                .isFalse
        }

        @Test
        fun `should accept a release deploy into a release repository`() {
            assertOk(mavenFacade.deployFile(deploy("release-only", releaseArtifact)))
        }

        @Test
        fun `should refuse a release deploy into a snapshot repository and name the repository and the policy`() {
            // when: a release is deployed into the snapshot repository
            val result = mavenFacade.deployFile(deploy("snapshot-only", releaseArtifact))

            // then: the deploy is refused with 400 and the message names the repository, the policy and the version
            assertThat(result.isErr).describedAs("release deploy into a snapshot repository is refused").isTrue
            assertThat(result.error.status).isEqualTo(400)
            assertThat(result.error.message).isEqualTo("Repository 'snapshot-only' has version policy SNAPSHOT and does not accept the release version 1.0")
        }

        @Test
        fun `should accept a snapshot deploy into a snapshot repository`() {
            assertOk(mavenFacade.deployFile(deploy("snapshot-only", snapshotArtifact)))
        }

        @Test
        fun `should accept artifact metadata and its checksums in a snapshot repository`() {
            // when: the metadata of the artifact is deployed with its checksums, which are not tied to a version
            val metadata = listOf("com/example/app/maven-metadata.xml", "com/example/app/maven-metadata.xml.sha1")

            // then: every deploy is accepted
            metadata.forEach { path ->
                assertOk(mavenFacade.deployFile(deploy("snapshot-only", path)))
            }
        }

        @Test
        fun `should accept both release and snapshot deploys into a repository that sets no policy`() {
            assertOk(mavenFacade.deployFile(deploy("plain", releaseArtifact)))
            assertOk(mavenFacade.deployFile(deploy("plain", snapshotArtifact)))
        }

        @Test
        fun `should keep serving a snapshot file that a release repository already holds`() {
            // given: a snapshot file that was stored before the policy was set
            addFileToRepository(FileSpec("release-only", snapshotArtifact, "snapshot"))

            // when: the file is read
            val response = mavenFacade.findFile(FileSpec("release-only", "/$snapshotArtifact", "snapshot").toLookupRequest(UNAUTHORIZED))

            // then: the read is served, the policy judges deploys only
            assertOk(response)
        }

        @Test
        fun `should keep deleting a snapshot file that a release repository already holds`() {
            // given: a snapshot file that was stored before the policy was set
            addFileToRepository(FileSpec("release-only", snapshotArtifact, "snapshot"))

            // when: a manager deletes it
            val result = mavenFacade.deleteFile(DeleteRequest(createManagerAccessToken("manager", "secret"), repo("release-only"), snapshotArtifact.toLocation(), "tester"))

            // then: the delete is served and the file is gone
            assertOk(result)
            assertThat(repo("release-only").storageProvider.exists(snapshotArtifact.toLocation())).isFalse
        }

        @Test
        fun `should refuse a deploy into a read-only repository with 405 and name the repository`() {
            // when: a release is deployed into the frozen repository
            val result = mavenFacade.deployFile(deploy("frozen", releaseArtifact))

            // then: the deploy is refused with 405, and the message names the repository and says it is read-only
            assertThat(result.isErr).describedAs("deploy into a read-only repository is refused").isTrue
            assertThat(result.error.status).isEqualTo(405)
            assertThat(result.error.message).isEqualTo("Repository 'frozen' is read-only and does not accept deployments or deletions")
            assertThat(repo("frozen").storageProvider.exists(releaseArtifact.toLocation())).isFalse
        }

        @Test
        fun `should refuse a delete from a read-only repository with 405 and keep the file`() {
            // given: a file in the frozen repository
            addFileToRepository(FileSpec("frozen", releaseArtifact, "release"))

            // when: a manager deletes it
            val result = mavenFacade.deleteFile(DeleteRequest(createManagerAccessToken("manager", "secret"), repo("frozen"), releaseArtifact.toLocation(), "tester"))

            // then: the delete is refused with 405 and the file is still there
            assertThat(result.isErr).describedAs("delete from a read-only repository is refused").isTrue
            assertThat(result.error.status).isEqualTo(405)
            assertThat(repo("frozen").storageProvider.exists(releaseArtifact.toLocation())).isTrue
        }

        @Test
        fun `should refuse pom generation in a read-only repository with 405`() {
            // when: a pom is generated in the frozen repository
            val result = mavenFacade.generatePom(
                GeneratePomRequest(
                    accessToken = createManagerAccessToken("manager", "secret"),
                    repository = repo("frozen"),
                    gav = "com/example/app/1.0".toLocation(),
                    pomDetails = PomDetails("com.example", "app", "1.0"),
                )
            )

            // then: the repository takes no writes, so nothing is written
            assertThat(result.isErr).describedAs("pom generation in a read-only repository is refused").isTrue
            assertThat(result.error.status).isEqualTo(405)
        }

        @Test
        fun `should keep serving a file of a read-only repository`() {
            // given: a file in the frozen repository
            addFileToRepository(FileSpec("frozen", releaseArtifact, "release"))

            // when: the file is read
            val response = mavenFacade.findFile(FileSpec("frozen", "/$releaseArtifact", "release").toLookupRequest(UNAUTHORIZED))

            // then: the read is served
            assertOk(response)
        }

        @Test
        fun `should keep answering 409 for a redeployment that the repository does not allow`() {
            // given: a release already deployed into a repository without redeployment
            assertOk(mavenFacade.deployFile(deploy("plain", releaseArtifact)))

            // when: the same file is deployed again
            val result = mavenFacade.deployFile(deploy("plain", releaseArtifact))

            // then: the redeploy is refused with 409, as before the policy existed
            assertThat(result.isErr).describedAs("redeployment is refused").isTrue
            assertThat(result.error.status).isEqualTo(409)
        }

        private fun deploy(repository: String, path: String) = DeployRequest(
            repository = repo(repository),
            gav = path.toLocation(),
            by = "tester",
            content = "content".byteInputStream(),
            generateChecksums = false,
        )

    }

    private fun repo(name: String) =
        mavenFacade.getRepository(name)!!

}
