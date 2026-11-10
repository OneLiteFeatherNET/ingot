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

import com.reposilite.journalist.Journalist
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.api.GeneratePomRequest
import com.reposilite.maven.api.METADATA_FILE
import com.reposilite.maven.api.PomDetails
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.shared.ErrorResponse
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageFacade
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.StorageProviderSettings
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.RoutePermission.WRITE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import panda.std.Result
import panda.std.ResultAssertions.assertOk
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

private const val RACE_TIMEOUT_SECONDS = 10L

/**
 * Holds the first read of a version-level metadata file of the artifact until the test releases it. The bytes are read
 * before the hold, so the holder writes back exactly what it saw, as a deploy does after it has read the metadata.
 */
private class HeldMetadataRead {

    private val armed = AtomicBoolean(true)
    private val read = CountDownLatch(1)
    private val released = CountDownLatch(1)

    fun pass(location: Location, result: Result<InputStream, ErrorResponse>): Result<InputStream, ErrorResponse> {
        if (location.getSimpleName() != METADATA_FILE || !armed.getAndSet(false)) {
            return result
        }
        val content = result.map { stream -> stream.use { it.readBytes() } }
        read.countDown()
        check(released.await(RACE_TIMEOUT_SECONDS, SECONDS)) { "the held read of $location was never released" }
        return content.map { it.inputStream() }
    }

    fun awaitRead() {
        check(read.await(RACE_TIMEOUT_SECONDS, SECONDS)) { "no deploy read maven-metadata.xml" }
    }

    fun release() {
        released.countDown()
    }

}

private class HeldReadStorageProvider(
    private val delegate: StorageProvider,
    private val hold: HeldMetadataRead,
) : StorageProvider by delegate {

    override fun getFile(location: Location): Result<InputStream, ErrorResponse> =
        hold.pass(location, delegate.getFile(location))

}

private class HeldReadStorageFacade(private val hold: HeldMetadataRead) : StorageFacade() {

    override fun createStorageProvider(
        journalist: Journalist,
        failureFacade: FailureFacade,
        workingDirectory: Path,
        repository: String,
        storageSettings: StorageProviderSettings,
    ): StorageProvider? =
        super.createStorageProvider(journalist, failureFacade, workingDirectory, repository, storageSettings)
            ?.let { HeldReadStorageProvider(it, hold) }

}

/**
 * Two deploys of different versions of one artifact, read-modify-write of the artifact-level maven-metadata.xml. The first
 * deploy is held after it has read the file, the second one runs in the meantime, and then the first one writes.
 */
internal class MetadataConcurrentDeployTest : MavenSpecification() {

    private val hold = HeldMetadataRead()
    private val deploys: ExecutorService = Executors.newFixedThreadPool(2)
    private val artifact = "com/example/app".toLocation()

    override fun repositories(): List<RepositorySettings> =
        listOf(RepositorySettings("releases", visibility = PUBLIC))

    override fun storageFacade(): StorageFacade =
        HeldReadStorageFacade(hold)

    @AfterEach
    fun shutdownDeploys() {
        hold.release()
        deploys.shutdownNow()
    }

    @Test
    fun `should list both versions when two deploys of different versions update the metadata at the same time`() {
        // given: a deploy of 3.0.1 that has read the artifact metadata and is held before it writes
        val repository = mavenFacade.getRepository("releases")!!
        val token = createAccessToken("deployer", "secret", "releases", "/com/example/app", WRITE)
        val first = CompletableFuture.supplyAsync({ generatePom(token, "3.0.1") }, deploys)
        hold.awaitRead()

        // when: a deploy of 3.0.2 runs while the first one is held, and then the first one is released
        val second = CompletableFuture.supplyAsync({ generatePom(token, "3.0.2") }, deploys)
        try {
            second.get(2, SECONDS)
        } catch (_: TimeoutException) {
            // the second deploy waits for the lock of the first one, and finishes after the release below
        }
        hold.release()
        assertOk(first.get(RACE_TIMEOUT_SECONDS, SECONDS))
        assertOk(second.get(RACE_TIMEOUT_SECONDS, SECONDS))

        // then: the artifact metadata lists both versions
        val versions = mavenFacade.findMetadata(repository, artifact).get().versioning?.versions.orEmpty()
        assertThat(versions)
            .withFailMessage("3.0.2 is missing from maven-metadata.xml after two deploys, the listing has %s", versions)
            .contains("3.0.1", "3.0.2")
    }

    private fun generatePom(token: AccessTokenIdentifier, version: String) =
        mavenFacade.generatePom(
            GeneratePomRequest(
                accessToken = token,
                repository = mavenFacade.getRepository("releases")!!,
                gav = artifact.resolve("$version/app-$version.pom"),
                pomDetails = PomDetails(groupId = "com.example", artifactId = "app", version = version),
            )
        )

}
