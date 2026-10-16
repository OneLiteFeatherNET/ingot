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

package com.reposilite.maven

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.RecommendedRemoteSpecificationJunitExtension
import com.reposilite.maven.api.Metadata
import com.reposilite.maven.api.SaveMetadataRequest
import com.reposilite.maven.api.Snapshot
import com.reposilite.maven.api.SnapshotVersion
import com.reposilite.maven.api.Versioning
import com.reposilite.maven.specification.MavenIntegrationSpecification
import com.reposilite.storage.api.toLocation
import io.javalin.http.HttpStatus.NOT_FOUND
import kong.unirest.core.Unirest.delete
import kong.unirest.core.Unirest.get
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalMavenMetadataDeleteIntegrationTest : MavenMetadataDeleteIntegrationTest()

@ExtendWith(RecommendedRemoteSpecificationJunitExtension::class)
internal class RemoteMavenMetadataDeleteIntegrationTest : MavenMetadataDeleteIntegrationTest()

internal abstract class MavenMetadataDeleteIntegrationTest : MavenIntegrationSpecification() {

    private val repository = "releases"
    private val artifact = "com/example/app"
    private val artifactMetadata = "$artifact/maven-metadata.xml"

    @Test
    fun `should drop deleted version from artifact metadata and recompute latest and release`() {
        // given: versions 1.0, 1.1 and 2.0-SNAPSHOT with metadata listing all of them, release 1.1
        deployVersion("1.0", "app-1.0.jar")
        deployVersion("1.1", "app-1.1.jar")
        deployVersion("2.0-SNAPSHOT", "app-2.0-SNAPSHOT.jar")
        saveArtifactMetadata(versions = listOf("1.0", "1.1", "2.0-SNAPSHOT"), latest = "2.0-SNAPSHOT", release = "1.1")
        val (name, secret) = useDefaultManagementToken()

        // when: the directory of version 1.1 is deleted through the API
        val response = delete("$base/$repository/$artifact/1.1").basicAuth(name, secret).asString()

        // then: the request succeeds and the metadata lists 1.0 and 2.0-SNAPSHOT only
        assertThat(response.isSuccess).`as`("delete response of version directory 1.1").isTrue
        val metadata = findArtifactMetadata()
        assertThat(metadata.versioning?.versions).`as`("versions after deleting 1.1").containsExactly("1.0", "2.0-SNAPSHOT")
        assertThat(metadata.versioning?.latest).`as`("latest after deleting 1.1").isEqualTo("2.0-SNAPSHOT")
        assertThat(metadata.versioning?.release).`as`("release after deleting 1.1").isEqualTo("1.0")
        assertChecksumsMatchArtifactMetadata()
    }

    @Test
    fun `should delete metadata and its checksums once the last version is deleted`() {
        // given: versions 1.0 and 2.0-SNAPSHOT with metadata listing both
        deployVersion("1.0", "app-1.0.jar")
        deployVersion("2.0-SNAPSHOT", "app-2.0-SNAPSHOT.jar")
        saveArtifactMetadata(versions = listOf("1.0", "2.0-SNAPSHOT"), latest = "2.0-SNAPSHOT", release = "1.0")
        val (name, secret) = useDefaultManagementToken()

        // when: both version directories are deleted
        delete("$base/$repository/$artifact/1.0").basicAuth(name, secret).asString()
        delete("$base/$repository/$artifact/2.0-SNAPSHOT").basicAuth(name, secret).asString()

        // then: neither the metadata file nor any of its checksums remain
        assertThat(get("$base/$repository/$artifactMetadata").asString().status)
            .`as`("status of maven-metadata.xml without versions").isEqualTo(NOT_FOUND.code)
        listOf("md5", "sha1", "sha256", "sha512").forEach { extension ->
            assertThat(get("$base/$repository/$artifactMetadata.$extension").asString().status)
                .`as`("status of maven-metadata.xml.$extension without versions").isEqualTo(NOT_FOUND.code)
        }

        // and the artifact directory that only held the metadata is removed as well
        assertThat(mavenFacade.getRepository(repository)!!.storageProvider.exists(artifact.toLocation()))
            .`as`("artifact directory without any version").isFalse
    }

    @Test
    fun `should drop a version whose last artifact file is deleted`() {
        // given: versions 1.0 and 1.1, each with a single jar, metadata with 1.1 as latest and release
        deployVersion("1.0", "app-1.0.jar")
        deployVersion("1.1", "app-1.1.jar")
        saveArtifactMetadata(versions = listOf("1.0", "1.1"), latest = "1.1", release = "1.1")
        val (name, secret) = useDefaultManagementToken()

        // when: the only file of version 1.1 is deleted
        val response = delete("$base/$repository/$artifact/1.1/app-1.1.jar").basicAuth(name, secret).asString()

        // then: the version directory is empty, so 1.1 is gone from the artifact metadata
        assertThat(response.isSuccess).`as`("delete response of the last file of 1.1").isTrue
        val metadata = findArtifactMetadata()
        assertThat(metadata.versioning?.versions).`as`("versions after deleting the last file of 1.1").containsExactly("1.0")
        assertThat(metadata.versioning?.latest).isEqualTo("1.0")
        assertThat(metadata.versioning?.release).isEqualTo("1.0")
    }

    @Test
    fun `should keep artifact metadata unchanged when a version still holds other artifacts`() {
        // given: version 1.0 with a jar and a pom, listed in the artifact metadata
        deployVersion("1.0", "app-1.0.jar")
        deployVersion("1.0", "app-1.0.pom")
        saveArtifactMetadata(versions = listOf("1.0"), latest = "1.0", release = "1.0")
        val metadataBefore = get("$base/$repository/$artifactMetadata").asString().body
        val (name, secret) = useDefaultManagementToken()

        // when: only the jar of version 1.0 is deleted
        val response = delete("$base/$repository/$artifact/1.0/app-1.0.jar").basicAuth(name, secret).asString()

        // then: the request succeeds and the artifact metadata is not rewritten
        assertThat(response.isSuccess).`as`("delete response of the jar of 1.0").isTrue
        assertThat(get("$base/$repository/$artifactMetadata").asString().body)
            .`as`("artifact metadata after deleting one of two artifacts of 1.0")
            .isEqualTo(metadataBefore)
    }

    @Test
    fun `should drop snapshot entries of a deleted timestamped snapshot file`() {
        // given: a timestamped build 2 of 2.0-SNAPSHOT with a jar and a pom, described by the version metadata
        val versionDirectory = "$artifact/2.0-SNAPSHOT"
        useDocument(repository, versionDirectory, "app-2.0-20261015.111111-2.jar", "jar-content", store = true)
        useDocument(repository, versionDirectory, "app-2.0-20261015.111111-2.pom", "pom-content", store = true)
        val pomEntry = SnapshotVersion(extension = "pom", value = "2.0-20261015.111111-2", updated = "20261015111111")
        val jarEntry = SnapshotVersion(extension = "jar", value = "2.0-20261015.111111-2", updated = "20261015111111")
        mavenFacade.saveMetadata(
            SaveMetadataRequest(
                repository = mavenFacade.getRepository(repository)!!,
                gav = versionDirectory.toLocation(),
                metadata = Metadata(
                    groupId = "com.example",
                    artifactId = "app",
                    version = "2.0-SNAPSHOT",
                    versioning = Versioning(
                        snapshot = Snapshot(timestamp = "20261015.111111", buildNumber = 2),
                        _snapshotVersions = listOf(jarEntry, pomEntry),
                        lastUpdated = "20261015111111",
                    )
                )
            )
        )
        val (name, secret) = useDefaultManagementToken()

        // when: the timestamped jar of build 2 is deleted through the API
        val response = delete("$base/$repository/$versionDirectory/app-2.0-20261015.111111-2.jar").basicAuth(name, secret).asString()

        // then: the request succeeds, the jar entry is gone and the pom entry of the same build remains
        assertThat(response.isSuccess).`as`("delete response of the timestamped jar").isTrue
        val metadata = mavenFacade.findMetadata(mavenFacade.getRepository(repository)!!, versionDirectory.toLocation()).get()
        assertThat(metadata.versioning?.snapshotVersions.orEmpty().map { it.extension to it.value })
            .`as`("snapshot versions after deleting the jar")
            .containsExactly("pom" to "2.0-20261015.111111-2")
        assertThat(metadata.versioning?.snapshot).`as`("snapshot element while the pom of build 2 remains")
            .isEqualTo(Snapshot(timestamp = "20261015.111111", buildNumber = 2))
        assertThat(get("$base/$repository/$versionDirectory/app-2.0-20261015.111111-2.pom").asString().isSuccess)
            .`as`("pom of the same build").isTrue
    }

    private fun deployVersion(version: String, file: String) {
        useDocument(repository, "$artifact/$version", file, "content-$version", store = true)
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

    private fun findArtifactMetadata(): Metadata =
        mavenFacade.findMetadata(mavenFacade.getRepository(repository)!!, artifact.toLocation()).get()

    /**
     * Every checksum file has to describe the metadata as it is served right now.
     */
    private fun assertChecksumsMatchArtifactMetadata() {
        val content = get("$base/$repository/$artifactMetadata").asString().body
        assertThat(get("$base/$repository/$artifactMetadata.md5").asString().body).`as`("md5").isEqualTo(DigestUtils.md5Hex(content))
        assertThat(get("$base/$repository/$artifactMetadata.sha1").asString().body).`as`("sha1").isEqualTo(DigestUtils.sha1Hex(content))
        assertThat(get("$base/$repository/$artifactMetadata.sha256").asString().body).`as`("sha256").isEqualTo(DigestUtils.sha256Hex(content))
        assertThat(get("$base/$repository/$artifactMetadata.sha512").asString().body).`as`("sha512").isEqualTo(DigestUtils.sha512Hex(content))
    }

}
