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
import com.reposilite.maven.api.DeployRequest
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.maven.specification.RecordingArtifactIndexSink
import com.reposilite.maven.specification.RecordingArtifactIndexSink.IndexCall
import com.reposilite.storage.api.toLocation
import net.onelitefeather.ingot.search.application.DeleteIndexListener
import net.onelitefeather.ingot.search.application.DeployIndexListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertOk

/** The index listeners of search (artifact search spec, 3.5), driven by real deploys and deletes. */
internal class IndexEventListenersTest : MavenSpecification() {

    private val hosted = "hosted"
    private val by = "dzikoysk@127.0.0.1"
    private val baseJar = "com/example/app/1.0.0/app-1.0.0.jar"
    private val versionDirectory = "com/example/app/1.0.0"

    override fun repositories() = listOf(RepositorySettings(hosted, visibility = PUBLIC))

    @Test
    fun `should queue the deployed file for the index`() {
        // given: a deploy listener over a sink that records
        val sink = RecordingArtifactIndexSink()
        extensions.registerEvent(DeployIndexListener(sink) {})

        // when: a file is deployed without checksums
        assertOk(mavenFacade.deployFile(DeployRequest(repository(hosted), baseJar.toLocation(), by, content("jar"), generateChecksums = false)))

        // then: the deployed path is queued
        assertThat(sink.recorded).containsExactly(IndexCall("changed", hosted, baseJar))
    }

    @Test
    fun `should queue the base file when a checksum is deployed`() {
        // given: a deploy listener over a sink that records
        val sink = RecordingArtifactIndexSink()
        extensions.registerEvent(DeployIndexListener(sink) {})

        // when: a client uploads the sha1 sidecar of the jar itself
        assertOk(mavenFacade.deployFile(DeployRequest(repository(hosted), "$baseJar.sha1".toLocation(), by, content("checksum"), generateChecksums = false)))

        // then: the base file is queued, not the sidecar
        assertThat(sink.recorded).containsExactly(IndexCall("changed", hosted, baseJar))
    }

    @Test
    fun `should not queue maven metadata or its checksums`() {
        // given: a deploy listener over a sink that records
        val sink = RecordingArtifactIndexSink()
        extensions.registerEvent(DeployIndexListener(sink) {})

        // when: the version metadata and its sha1 sidecar are deployed
        assertOk(mavenFacade.deployFile(DeployRequest(repository(hosted), "$versionDirectory/maven-metadata.xml".toLocation(), by, content("xml"), generateChecksums = false)))
        assertOk(mavenFacade.deployFile(DeployRequest(repository(hosted), "$versionDirectory/maven-metadata.xml.sha1".toLocation(), by, content("sha"), generateChecksums = false)))

        // then: nothing is queued, because metadata is not an indexed row
        assertThat(sink.recorded).`as`("index calls for maven metadata").isEmpty()
    }

    @Test
    fun `should queue the removed file when a file is deleted`() {
        // given: a stored file and a delete listener over a sink that records
        addFileToRepository(FileSpec(hosted, baseJar, "jar"))
        val sink = RecordingArtifactIndexSink()
        extensions.registerEvent(DeleteIndexListener(sink) {})

        // when: the file is removed by a cleanup run
        assertOk(mavenFacade.deleteUnchecked(repository(hosted), baseJar.toLocation(), "cleanup", cleanupRunId = "run-1"))

        // then: the removed path is queued
        assertThat(sink.recorded).containsExactly(IndexCall("removed", hosted, baseJar))
    }

    @Test
    fun `should queue the base file when its checksum sidecar is deleted`() {
        // given: a stored jar with its sha1 sidecar, and a delete listener over a sink that records
        addFileToRepository(FileSpec(hosted, baseJar, "jar"))
        addFileToRepository(FileSpec(hosted, "$baseJar.sha1", "checksum"))
        val sink = RecordingArtifactIndexSink()
        extensions.registerEvent(DeleteIndexListener(sink) {})

        // when: the sidecar is removed on its own
        assertOk(mavenFacade.deleteUnchecked(repository(hosted), "$baseJar.sha1".toLocation(), "cleanup", cleanupRunId = "run-4"))

        // then: the base file is queued for a re-read, so its sha1 is cleared, and the sidecar path is not removed
        assertThat(sink.recorded).containsExactly(IndexCall("changed", hosted, baseJar))
    }

    @Test
    fun `should queue nothing when the checksum of maven metadata is deleted`() {
        // given: a stored version metadata checksum and a delete listener over a sink that records
        addFileToRepository(FileSpec(hosted, "$versionDirectory/maven-metadata.xml.sha1", "checksum"))
        val sink = RecordingArtifactIndexSink()
        extensions.registerEvent(DeleteIndexListener(sink) {})

        // when: the checksum is removed
        assertOk(mavenFacade.deleteUnchecked(repository(hosted), "$versionDirectory/maven-metadata.xml.sha1".toLocation(), "cleanup", cleanupRunId = "run-5"))

        // then: nothing is queued, because metadata is not an indexed row
        assertThat(sink.recorded).`as`("index calls for maven metadata checksums").isEmpty()
    }

    @Test
    fun `should queue one removal per directory of a cleanup run`() {
        // given: a version directory with two files and a delete listener over a sink that records
        addFileToRepository(FileSpec(hosted, baseJar, "jar"))
        addFileToRepository(FileSpec(hosted, "$versionDirectory/app-1.0.0.pom", "pom"))
        val sink = RecordingArtifactIndexSink()
        extensions.registerEvent(DeleteIndexListener(sink) {})

        // when: the cleanup run removes the directory, which emits a single delete event
        assertOk(mavenFacade.deleteUnchecked(repository(hosted), versionDirectory.toLocation(), "cleanup", cleanupRunId = "run-2"))

        // then: one removal of the directory is queued
        assertThat(sink.recorded).containsExactly(IndexCall("removed", hosted, versionDirectory))
    }

    @Test
    fun `should complete the deploy when the index sink throws`() {
        // given: a deploy listener whose sink fails as a broken database would, and a reported failure
        val failure = IllegalStateException("database is locked")
        val reported = mutableListOf<Throwable>()
        extensions.registerEvent(DeployIndexListener(RecordingArtifactIndexSink(failure = failure)) { reported += it })

        // when: a file is deployed
        val result = mavenFacade.deployFile(DeployRequest(repository(hosted), baseJar.toLocation(), by, content("jar"), generateChecksums = false))

        // then: the deploy succeeds, the file is stored and the failure is reported
        assertOk(result)
        assertThat(repository(hosted).storageProvider.exists(baseJar.toLocation())).`as`("deployed file").isTrue()
        assertThat(reported).`as`("failures reported by the listener").containsExactly(failure)
    }

    @Test
    fun `should complete the delete when the index sink throws`() {
        // given: a stored file and a delete listener whose sink fails
        addFileToRepository(FileSpec(hosted, baseJar, "jar"))
        val failure = IllegalStateException("database is locked")
        val reported = mutableListOf<Throwable>()
        extensions.registerEvent(DeleteIndexListener(RecordingArtifactIndexSink(failure = failure)) { reported += it })

        // when: the file is removed
        val result = mavenFacade.deleteUnchecked(repository(hosted), baseJar.toLocation(), "cleanup", cleanupRunId = "run-3")

        // then: the delete succeeds, the file is gone and the failure is reported
        assertOk(result)
        assertThat(repository(hosted).storageProvider.exists(baseJar.toLocation())).`as`("deleted file").isFalse()
        assertThat(reported).`as`("failures reported by the listener").containsExactly(failure)
    }

    private fun repository(name: String) =
        mavenFacade.getRepository(name)!!

    private fun content(text: String) = text.byteInputStream()

}
