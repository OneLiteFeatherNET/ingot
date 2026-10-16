/*
 * Copyright (c) 2023 dzikoysk
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
import com.reposilite.maven.api.DeleteEvent
import com.reposilite.maven.api.DeleteRequest
import com.reposilite.maven.api.DeployEvent
import com.reposilite.maven.api.DeployRequest
import com.reposilite.maven.api.GeneratePomRequest
import com.reposilite.maven.api.Metadata
import com.reposilite.maven.api.PomDetails
import com.reposilite.maven.api.SaveMetadataRequest
import com.reposilite.maven.api.Snapshot
import com.reposilite.maven.api.SnapshotVersion
import com.reposilite.maven.api.Versioning
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.maven.specification.RecordingArtifactIndexSink.IndexCall
import com.reposilite.storage.api.toLocation
import com.reposilite.token.RoutePermission.WRITE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertOk

/**
 * Pins the write paths that bypass the deploy and delete events. The events are unchanged; what each path tells the
 * artifact index (the sink, artifact search spec 3.5) is asserted next to them.
 */
internal class WritePathEventCharacterisationTest : MavenSpecification() {

    private val hosted = "hosted"
    private val mirror = "mirror"
    private val snapshots = "snapshots"
    private val by = "dzikoysk@127.0.0.1"

    private val baseJar = "com/example/app/1.0.0/app-1.0.0.jar"
    private val generatedPom = "com/example/app/1.0.0/app-1.0.0.pom"
    private val versionDirectory = "com/example/app/1.0.0"

    override fun repositories() = listOf(
        RepositorySettings(hosted, visibility = PUBLIC),
        RepositorySettings(
            mirror,
            visibility = PUBLIC,
            proxied = mutableListOf(MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = true, authorization = REMOTE_AUTH))
        ),
        RepositorySettings(snapshots, preserveSnapshots = false),
    )

    private fun recordDeployEvents(): MutableList<DeployEvent> {
        val deployed = mutableListOf<DeployEvent>()
        extensions.registerEvent { event: DeployEvent -> deployed += event }
        return deployed
    }

    private fun recordDeleteEvents(): MutableList<DeleteEvent> {
        val deleted = mutableListOf<DeleteEvent>()
        extensions.registerEvent { event: DeleteEvent -> deleted += event }
        return deleted
    }

    @Test
    fun `should emit one deploy event for the base file and none for its checksum sidecars`() {
        // given: a hosted repository and listeners for deploy and delete events
        val deployed = recordDeployEvents()
        val deleted = recordDeleteEvents()

        // when: a file is deployed with checksum generation requested
        val result = mavenFacade.deployFile(
            DeployRequest(mavenFacade.getRepository(hosted)!!, baseJar.toLocation(), by, content("content"), generateChecksums = true)
        )

        // then: the sidecars exist, yet only the base file is announced
        assertOk(result)
        assertThat(deployed.map { it.gav })
            .`as`("deploy events after deploying with checksums")
            .containsExactly(baseJar.toLocation())
        assertThat(deleted).`as`("delete events after deploying").isEmpty()
    }

    @Test
    fun `should emit the deploy event before the checksum sidecars are written`() {
        // given: a deploy listener that records whether the sha1 sidecar exists at the moment of the event
        val repository = mavenFacade.getRepository(hosted)!!
        val sidecarExistsAtEvent = mutableListOf<Boolean>()
        extensions.registerEvent { _: DeployEvent ->
            sidecarExistsAtEvent += repository.storageProvider.exists(baseJar.toLocation().resolveSibling("app-1.0.0.jar.sha1"))
        }

        // when: a file is deployed with checksum generation requested
        assertOk(mavenFacade.deployFile(DeployRequest(repository, baseJar.toLocation(), by, content("content"), generateChecksums = true)))

        // then: the event fired before the sidecar was written
        assertThat(sidecarExistsAtEvent).`as`("sha1 sidecar present when the deploy event fires").containsExactly(false)
        assertThat(repository.storageProvider.exists(baseJar.toLocation().resolveSibling("app-1.0.0.jar.sha1"))).isTrue()
    }

    @Test
    fun `should queue the base file once, after its checksum sidecars are written`() {
        // given: a hosted repository and a sink that records whether the sha1 sidecar exists at the moment of the call
        val repository = mavenFacade.getRepository(hosted)!!
        val sha1 = baseJar.toLocation().resolveSibling("app-1.0.0.jar.sha1")
        val sidecarExistsAtQueue = mutableListOf<Boolean>()
        indexSink.onCall = { if (it.operation == "changed") sidecarExistsAtQueue += repository.storageProvider.exists(sha1) }

        // when: a file is deployed with checksum generation requested
        assertOk(mavenFacade.deployFile(DeployRequest(repository, baseJar.toLocation(), by, content("content"), generateChecksums = true)))

        // then: the base file is queued exactly once, and only when its sidecar is already stored
        assertThat(indexSink.callsOf("changed"))
            .`as`("index calls after deploying with checksums")
            .containsExactly(IndexCall("changed", hosted, baseJar))
        assertThat(sidecarExistsAtQueue).`as`("sha1 sidecar present when the base file is queued").containsExactly(true)
    }

    @Test
    fun `should emit no deploy event when the generated pom is written`() {
        // given: a write token for the artifact directory and listeners for deploy and delete events
        val repository = mavenFacade.getRepository(hosted)!!
        val deployed = recordDeployEvents()
        val deleted = recordDeleteEvents()
        val token = createAccessToken("dzikoysk", "secret", hosted, "com/example/app", WRITE)
        val pom = "com/example/app/1.0.0/app-1.0.0.pom".toLocation()

        // when: the pom is generated for the version
        assertOk(
            mavenFacade.generatePom(
                GeneratePomRequest(
                    accessToken = token,
                    repository = repository,
                    gav = pom,
                    pomDetails = PomDetails(groupId = "com.example", artifactId = "app", version = "1.0.0"),
                )
            )
        )

        // then: the pom is stored, and no event announces it
        assertThat(repository.storageProvider.exists(pom)).`as`("generated pom is stored").isTrue()
        assertThat(deployed).`as`("deploy events for the generated pom").isEmpty()
        assertThat(deleted).`as`("delete events for the generated pom").isEmpty()
    }

    @Test
    fun `should queue the generated pom for the index`() {
        // given: a write token for the artifact directory
        val repository = mavenFacade.getRepository(hosted)!!
        val token = createAccessToken("dzikoysk", "secret", hosted, "com/example/app", WRITE)

        // when: the pom is generated for the version
        assertOk(
            mavenFacade.generatePom(
                GeneratePomRequest(
                    accessToken = token,
                    repository = repository,
                    gav = generatedPom.toLocation(),
                    pomDetails = PomDetails(groupId = "com.example", artifactId = "app", version = "1.0.0"),
                )
            )
        )

        // then: the pom is queued for the index
        assertThat(indexSink.callsOf("changed"))
            .`as`("index calls after generating the pom")
            .containsExactly(IndexCall("changed", hosted, generatedPom))
    }

    @Test
    fun `should emit no deploy event when a mirror copy is stored`() {
        // given: a deploy listener and a file that exists only on the remote host
        val deployed = recordDeployEvents()

        // when: the file is requested through the mirror repository and fetched from the remote host
        assertOk(mavenFacade.findFile(FileSpec(mirror, "/$baseJar", REMOTE_CONTENT).toLookupRequest(UNAUTHORIZED)))

        // then: the copy is stored without a deploy event
        assertThat(deployed).`as`("deploy events for a mirror copy").isEmpty()
    }

    @Test
    fun `should queue a stored mirror copy once its version directory is in the ledger`() {
        // given: a file that exists only on the remote host, and a sink that records the ledger at the moment of the call
        val ledgerAtQueue = mutableListOf<Set<String>>()
        indexSink.onCall = { if (it.operation == "changed") ledgerAtQueue += mavenFacade.mirrorCacheEntries(mirror).keys }

        // when: the file is requested through the mirror repository
        assertOk(mavenFacade.findFile(FileSpec(mirror, "/$baseJar", REMOTE_CONTENT).toLookupRequest(UNAUTHORIZED)))

        // then: the copy is queued, cached copies included, and the ledger already names its directory
        assertThat(indexSink.callsOf("changed"))
            .`as`("index calls after storing a mirror copy")
            .containsExactly(IndexCall("changed", mirror, baseJar))
        assertThat(ledgerAtQueue).`as`("ledger keys at the moment of the call").containsExactly(setOf(versionDirectory))
    }

    @Test
    fun `should record the version directory of a stored mirror copy in the ledger`() {
        // given: a file that exists only on the remote host
        // when: the file is requested through the mirror repository
        assertOk(mavenFacade.findFile(FileSpec(mirror, "/$baseJar", REMOTE_CONTENT).toLookupRequest(UNAUTHORIZED)))

        // then: the ledger holds the directory of the copy, keyed without a leading slash
        assertThat(mavenFacade.mirrorCacheEntries(mirror).keys)
            .`as`("ledger keys after fetching a mirror copy")
            .containsExactly(versionDirectory)
    }

    @Test
    fun `should reorigin the version directory of a deploy into a mirror copy`() {
        // given: a cached mirror copy in the version directory
        assertOk(mavenFacade.findFile(FileSpec(mirror, "/$baseJar", REMOTE_CONTENT).toLookupRequest(UNAUTHORIZED)))

        // when: a file is deployed into the same directory
        assertOk(mavenFacade.deployFile(DeployRequest(mavenFacade.getRepository(mirror)!!, "$versionDirectory/hosted.jar".toLocation(), by, content("hosted"), generateChecksums = false)))

        // then: the directory is reoriginated, so its rows no longer carry the mirror origin
        assertThat(indexSink.callsOf("reorigin"))
            .`as`("reorigin calls after deploying into the copy directory")
            .containsExactly(IndexCall("reorigin", mirror, versionDirectory))
    }

    @Test
    fun `should remove the mirror record of a directory when a file is deployed into it`() {
        // given: a cached mirror copy in the version directory, recorded in the ledger
        assertOk(mavenFacade.findFile(FileSpec(mirror, "/$baseJar", REMOTE_CONTENT).toLookupRequest(UNAUTHORIZED)))
        assertThat(mavenFacade.mirrorCacheEntries(mirror).keys).containsExactly(versionDirectory)
        val deployed = recordDeployEvents()
        val deleted = recordDeleteEvents()

        // when: a file is deployed into the same directory
        val hostedFile = "$versionDirectory/hosted.jar"
        assertOk(mavenFacade.deployFile(DeployRequest(mavenFacade.getRepository(mirror)!!, hostedFile.toLocation(), by, content("hosted"), generateChecksums = false)))

        // then: the ledger record is gone, and only the deploy event is emitted
        assertThat(mavenFacade.mirrorCacheEntries(mirror)).`as`("ledger after deploying into the copy directory").isEmpty()
        assertThat(deployed.map { it.gav }).containsExactly(hostedFile.toLocation())
        assertThat(deleted).isEmpty()
    }

    @Test
    fun `should queue each pruned build for removal from the index`() {
        // given: a snapshot version with an old build that is no longer listed, and the pruning listener registered
        val repository = mavenFacade.getRepository(snapshots)!!
        val artifactId = "app"
        val versionId = "com/example/$artifactId/1.0.0-SNAPSHOT".toLocation()
        val prefix = "com/example/$artifactId/1.0.0-SNAPSHOT/$artifactId-1.0.0"
        val oldJar = "$prefix-20220101.213700-1.jar"
        addFileToRepository(FileSpec(snapshots, oldJar, "one"))
        addFileToRepository(FileSpec(snapshots, "$prefix-20220101.213702-2.jar", "two"))
        assertOk(
            mavenFacade.saveMetadata(
                SaveMetadataRequest(
                    repository = repository,
                    gav = versionId,
                    metadata = Metadata(
                        artifactId = artifactId,
                        versioning = Versioning(
                            snapshot = Snapshot(timestamp = "20220101.213702", buildNumber = 2),
                            _snapshotVersions = listOf(SnapshotVersion(value = "1.0.0-20220101.213702-2", updated = "20220101213702")),
                        ),
                    ),
                )
            )
        )
        extensions.registerEvent(PreservedBuildsListener(mavenFacade, indexSink))

        // when: the metadata of the snapshot is deployed
        extensions.emitEvent(DeployEvent(repository, "$versionId/maven-metadata.xml".toLocation(), by))

        // then: the old build is queued for removal, and only that build
        assertThat(indexSink.callsOf("removed"))
            .`as`("index removals caused by pruning")
            .containsExactly(IndexCall("removed", snapshots, oldJar))
    }

    @Test
    fun `should emit no delete event when snapshot pruning removes old builds`() {
        // given: a snapshot version with an old build that is no longer listed, and the pruning listener registered
        val repository = mavenFacade.getRepository(snapshots)!!
        val artifactId = "app"
        val versionId = "com/example/$artifactId/1.0.0-SNAPSHOT".toLocation()
        val prefix = "com/example/$artifactId/1.0.0-SNAPSHOT/$artifactId-1.0.0"
        val oldJar = "$prefix-20220101.213700-1.jar"
        addFileToRepository(FileSpec(snapshots, oldJar, "one"))
        addFileToRepository(FileSpec(snapshots, "$prefix-20220101.213702-2.jar", "two"))
        assertOk(
            mavenFacade.saveMetadata(
                SaveMetadataRequest(
                    repository = repository,
                    gav = versionId,
                    metadata = Metadata(
                        artifactId = artifactId,
                        versioning = Versioning(
                            snapshot = Snapshot(timestamp = "20220101.213702", buildNumber = 2),
                            _snapshotVersions = listOf(SnapshotVersion(value = "1.0.0-20220101.213702-2", updated = "20220101213702")),
                        ),
                    ),
                )
            )
        )
        extensions.registerEvent(PreservedBuildsListener(mavenFacade, indexSink))
        val deleted = recordDeleteEvents()

        // when: the metadata of the snapshot is deployed
        extensions.emitEvent(DeployEvent(repository, "$versionId/maven-metadata.xml".toLocation(), by))

        // then: the old build is removed from storage, and no delete event announces it
        assertThat(repository.storageProvider.exists(oldJar.toLocation()))
            .`as`("old build removed by pruning")
            .isFalse()
        assertThat(deleted).`as`("delete events emitted by snapshot pruning").isEmpty()
    }

    @Test
    fun `should emit one delete event for a version directory removed without a token by a cleanup run`() {
        // given: a version directory with two files and a delete listener
        addFileToRepository(FileSpec(hosted, baseJar, "jar"))
        addFileToRepository(FileSpec(hosted, "$versionDirectory/app-1.0.0.pom", "pom"))
        val deleted = recordDeleteEvents()

        // when: the cleanup run removes the directory through the unchecked path
        assertOk(mavenFacade.deleteUnchecked(mavenFacade.getRepository(hosted)!!, versionDirectory.toLocation(), "cleanup", cleanupRunId = "run-1"))

        // then: one event names the directory and carries the run id
        assertThat(deleted).hasSize(1)
        assertThat(deleted.single().gav).isEqualTo(versionDirectory.toLocation())
        assertThat(deleted.single().cleanupRunId).isEqualTo("run-1")
    }

    @Test
    fun `should emit one delete event for a version directory removed with a token by a cleanup run`() {
        // given: a version directory, a write token for it and a delete listener
        addFileToRepository(FileSpec(hosted, baseJar, "jar"))
        val token = createAccessToken("manager", "secret", hosted, "com/example/app", WRITE)
        val deleted = recordDeleteEvents()

        // when: the cleanup run removes the directory with the token of its actor
        assertOk(
            mavenFacade.deleteFile(
                DeleteRequest(token, mavenFacade.getRepository(hosted)!!, versionDirectory.toLocation(), "manager", cleanupRunId = "run-2")
            )
        )

        // then: one event names the directory, the actor and the run
        assertThat(deleted).hasSize(1)
        assertThat(deleted.single().gav).isEqualTo(versionDirectory.toLocation())
        assertThat(deleted.single().by).isEqualTo("manager")
        assertThat(deleted.single().cleanupRunId).isEqualTo("run-2")
    }

    @Test
    fun `should drop the mirror record of a cleaned copy directory and emit one delete event`() {
        // given: a cached mirror copy in the version directory and a delete listener
        assertOk(mavenFacade.findFile(FileSpec(mirror, "/$baseJar", REMOTE_CONTENT).toLookupRequest(UNAUTHORIZED)))
        val deleted = recordDeleteEvents()

        // when: the cleanup run removes the cached copy directory
        assertOk(mavenFacade.deleteUnchecked(mavenFacade.getRepository(mirror)!!, versionDirectory.toLocation(), "cleanup", cleanupRunId = "run-3"))

        // then: the ledger record is gone and one event announces the removal
        assertThat(mavenFacade.mirrorCacheEntries(mirror)).`as`("ledger after cleaning the copy").isEmpty()
        assertThat(deleted).hasSize(1)
        assertThat(deleted.single().cleanupRunId).isEqualTo("run-3")
    }

    private fun content(text: String) = text.byteInputStream()

}
