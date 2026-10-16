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
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.api.Metadata
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenIntegrationSpecification
import com.reposilite.storage.api.toLocation
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.put
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val PRUNING_REPOSITORY = "snapshots"
private const val PRESERVING_REPOSITORY = "preserved-snapshots"
private const val GROUP_PATH = "com/example/app"
private const val VERSION_DIRECTORY = "$GROUP_PATH/1.0-SNAPSHOT"
private const val METADATA = "$VERSION_DIRECTORY/maven-metadata.xml"

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalMavenSnapshotPruningIntegrationTest : MavenSnapshotPruningIntegrationTest()

@ExtendWith(RecommendedRemoteSpecificationJunitExtension::class)
internal class RemoteMavenSnapshotPruningIntegrationTest : MavenSnapshotPruningIntegrationTest()

/**
 * Deploys timestamped builds of 1.0-SNAPSHOT the way Maven does: every artifact file is followed by its checksums,
 * and the version-level maven-metadata.xml lists every build the client has seen so far, followed by its checksums.
 */
internal abstract class MavenSnapshotPruningIntegrationTest : MavenIntegrationSpecification() {

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + RepositorySettings(
                    id = PRESERVING_REPOSITORY,
                    redeployment = true,
                    preserveSnapshots = true,
                    storageProvider = _storageProvider!!,
                )
            )
        }
    }

    @Test
    fun `should keep only the newest build when snapshots are not preserved`() {
        // given: three builds of 1.0-SNAPSHOT deployed into a repository that does not preserve snapshots
        (1..3).forEach { deployBuild(PRUNING_REPOSITORY, it) }

        // then: the files of builds 1 and 2 are gone and the files of build 3 are served
        (1..2).forEach { build ->
            assertThat(get("$base/$PRUNING_REPOSITORY/${artifactPath(build, "jar")}").asString().status)
                .`as`("jar of build $build after pruning").isEqualTo(404)
            assertThat(get("$base/$PRUNING_REPOSITORY/${artifactPath(build, "pom")}").asString().status)
                .`as`("pom of build $build after pruning").isEqualTo(404)
        }
        assertThat(get("$base/$PRUNING_REPOSITORY/${artifactPath(3, "jar")}").asString().isSuccess)
            .`as`("jar of build 3").isTrue
        assertThat(get("$base/$PRUNING_REPOSITORY/${artifactPath(3, "pom")}").asString().isSuccess)
            .`as`("pom of build 3").isTrue

        // and the version-level metadata lists only the entries of build 3 and points at it
        val metadata = findVersionMetadata(PRUNING_REPOSITORY)
        assertThat(metadata.versioning?.snapshotVersions.orEmpty().map { it.extension to it.value })
            .`as`("snapshot versions after pruning")
            .containsExactlyInAnyOrder("jar" to buildValue(3), "pom" to buildValue(3))
        assertThat(metadata.versioning?.snapshot?.timestamp).`as`("snapshot timestamp").isEqualTo(buildTimestamp(3))
        assertThat(metadata.versioning?.snapshot?.buildNumber).`as`("snapshot build number").isEqualTo(3)

        // and every checksum file describes the metadata as it is served
        assertChecksumsMatchMetadata(PRUNING_REPOSITORY)
    }

    @Test
    fun `should keep every build and list all of them when snapshots are preserved`() {
        // given: three builds of 1.0-SNAPSHOT deployed into a repository that preserves snapshots
        (1..3).forEach { deployBuild(PRESERVING_REPOSITORY, it) }

        // then: the files of all three builds are served
        (1..3).forEach { build ->
            listOf("jar", "pom").forEach { extension ->
                assertThat(get("$base/$PRESERVING_REPOSITORY/${artifactPath(build, extension)}").asString().isSuccess)
                    .`as`("$extension of build $build").isTrue
            }
        }

        // and the version-level metadata lists the entries of all three builds
        val metadata = findVersionMetadata(PRESERVING_REPOSITORY)
        assertThat(metadata.versioning?.snapshotVersions.orEmpty().map { it.extension to it.value })
            .`as`("snapshot versions of preserved builds")
            .containsExactlyInAnyOrder(
                "jar" to buildValue(1), "pom" to buildValue(1),
                "jar" to buildValue(2), "pom" to buildValue(2),
                "jar" to buildValue(3), "pom" to buildValue(3),
            )
        assertThat(metadata.versioning?.snapshot?.buildNumber).`as`("snapshot build number").isEqualTo(3)
    }

    /**
     * Uploads one build like Maven does. The client sends the metadata it has merged, so it still lists the older builds.
     */
    private fun deployBuild(repository: String, build: Int) {
        listOf("jar", "pom").forEach { extension ->
            putWithChecksums(repository, artifactPath(build, extension), "$extension-content-$build")
        }
        putWithChecksums(repository, METADATA, metadataXml(upToBuild = build))
    }

    private fun putWithChecksums(repository: String, path: String, content: String) {
        val (name, secret) = useDefaultManagementToken()
        put("$base/$repository/$path").body(content).basicAuth(name, secret).asString().also {
            assertThat(it.isSuccess).`as`("deploy of $path").isTrue
        }
        // Maven sends the checksums of the content it uploaded, which is what the server has to cope with afterwards
        listOf("md5" to DigestUtils.md5Hex(content), "sha1" to DigestUtils.sha1Hex(content)).forEach { (extension, checksum) ->
            put("$base/$repository/$path.$extension").body(checksum).basicAuth(name, secret).asString().also {
                assertThat(it.isSuccess).`as`("deploy of $path.$extension").isTrue
            }
        }
    }

    private fun metadataXml(upToBuild: Int): String {
        val entries = (1..upToBuild).flatMap { build ->
            listOf("jar", "pom").map { extension ->
                "<snapshotVersion><extension>$extension</extension>" +
                    "<value>${buildValue(build)}</value>" +
                    "<updated>${buildTimestamp(build).replace(".", "")}</updated></snapshotVersion>"
            }
        }.joinToString("")

        // the declaration has to be the first bytes of the body, so the document is built without indentation
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<metadata modelVersion=\"1.1\">" +
            "<groupId>com.example</groupId><artifactId>app</artifactId><version>1.0-SNAPSHOT</version>" +
            "<versioning>" +
            "<snapshot><timestamp>${buildTimestamp(upToBuild)}</timestamp><buildNumber>$upToBuild</buildNumber></snapshot>" +
            "<lastUpdated>${buildTimestamp(upToBuild).replace(".", "")}</lastUpdated>" +
            "<snapshotVersions>$entries</snapshotVersions>" +
            "</versioning></metadata>"
    }

    private fun findVersionMetadata(repository: String): Metadata =
        mavenFacade.findMetadata(mavenFacade.getRepository(repository)!!, VERSION_DIRECTORY.toLocation()).get()

    /**
     * Every checksum file has to describe the version-level metadata as it is served right now.
     */
    private fun assertChecksumsMatchMetadata(repository: String) {
        val content = get("$base/$repository/$METADATA").asString().body
        assertThat(get("$base/$repository/$METADATA.md5").asString().body).`as`("md5").isEqualTo(DigestUtils.md5Hex(content))
        assertThat(get("$base/$repository/$METADATA.sha1").asString().body).`as`("sha1").isEqualTo(DigestUtils.sha1Hex(content))
        assertThat(get("$base/$repository/$METADATA.sha256").asString().body).`as`("sha256").isEqualTo(DigestUtils.sha256Hex(content))
        assertThat(get("$base/$repository/$METADATA.sha512").asString().body).`as`("sha512").isEqualTo(DigestUtils.sha512Hex(content))
    }

    private fun artifactPath(build: Int, extension: String): String =
        "$VERSION_DIRECTORY/app-1.0-${buildTimestamp(build)}-$build.$extension"

    private fun buildValue(build: Int): String =
        "1.0-${buildTimestamp(build)}-$build"

    private fun buildTimestamp(build: Int): String =
        "20261015.10000$build"

}
