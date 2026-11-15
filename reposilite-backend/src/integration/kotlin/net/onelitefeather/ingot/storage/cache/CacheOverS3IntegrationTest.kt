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

@file:OptIn(ExperimentalIngotStorageApi::class)

package net.onelitefeather.ingot.storage.cache

import com.reposilite.IsolatedResources
import com.reposilite.SharedContainers
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.s3Endpoint
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.StorageProviderSettings
import com.reposilite.storage.api.Location
import com.reposilite.storage.s3.S3StorageProviderFactory
import com.reposilite.storage.s3.S3StorageProviderSettings
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.StorageProviderContext
import net.onelitefeather.ingot.storage.capability.ClusterBus
import net.onelitefeather.ingot.storage.capability.ClusterCacheAware
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.Duration

/**
 * Two replicas of one repository over the same S3 bucket, each with its own cache directory. The cluster bus is an in-process fake that
 * delivers an invalidation to every replica except the one that published it, synchronously, so no test waits on time.
 */
internal class CacheOverS3IntegrationTest {

    @TempDir
    lateinit var root: File

    // Every test gets a bucket of its own on the shared Floci, as the other S3 integration tests do.
    private val bucket = IsolatedResources.newBucketName("cache-test")

    private val release = "org/example/app/1.0/app-1.0.jar"
    private val metadata = "org/example/app/maven-metadata.xml"
    private val releaseBytes = "release-bytes-of-the-first-version".toByteArray()

    /** Delivers an invalidation to every replica that is not the origin. */
    private class Hub {
        private val replicas = mutableListOf<Pair<Int, ClusterCacheAware>>()

        fun join(id: Int, replica: ClusterCacheAware) {
            replicas += id to replica
        }

        fun deliver(origin: Int, keys: Collection<String>) {
            replicas.filter { it.first != origin }.forEach { it.second.invalidate(keys) }
        }
    }

    private class ReplicaBus(private val origin: Int, private val hub: Hub) : ClusterBus {
        override fun publishInvalidation(repository: String, keys: Collection<String>) {
            hub.deliver(origin, keys)
        }
    }

    private val hub = Hub()

    private val logger: Journalist = InMemoryLogger()

    private fun s3Settings(): S3StorageProviderSettings = S3StorageProviderSettings(
        bucketName = bucket,
        endpoint = SharedContainers.floci.s3Endpoint(),
        accessKey = "test",
        secretKey = "test",
        region = "us-east-1",
    )

    /** Builds a replica of the repository: a cache over an S3 delegate, bound to the fake bus. */
    private fun replica(id: Int): StorageProvider {
        val workingDirectory = root.toPath().resolve("replica-$id")
        val failureFacade = FailureFacade(logger)
        val context = object : StorageProviderContext {
            override val journalist: Journalist = logger
            override val failureFacade: FailureFacade = failureFacade
            override val workingDirectory: Path = workingDirectory
            override val repositoryName: String = "releases"

            override fun createProvider(settings: StorageProviderSettings): StorageProvider =
                S3StorageProviderFactory().create(logger, failureFacade, workingDirectory, repositoryName, settings as S3StorageProviderSettings)
        }
        val settings = CacheStorageProviderSettings(delegate = s3Settings())
        val provider = CacheStorageProviderFactory().create(context, settings)
        (provider as ClusterCacheAware).bind(ReplicaBus(id, hub), Duration.ofSeconds(300))
        hub.join(id, provider)
        return provider
    }

    private fun cachedFile(replica: Int, key: String): Path =
        root.toPath().resolve("replica-$replica").resolve("cache").resolve("releases").resolve("data").resolve(key)

    private fun StorageProvider.readBytes(key: String): ByteArray =
        getFile(Location.of(key)).get().use { it.readBytes() }

    @Test
    fun `a read fills the copy of the first replica`() {
        val first = replica(1)
        first.putFile(Location.of(release), releaseBytes.inputStream())

        val bytes = first.readBytes(release)

        assertThat(bytes).describedAs("the replica must serve the stored release").isEqualTo(releaseBytes)
        assertThat(cachedFile(1, release)).describedAs("the first read must leave a copy on the first replica").exists()
    }

    @Test
    fun `a removal through the first replica makes the next read of the second replica go to the store`() {
        val first = replica(1)
        val second = replica(2)
        first.putFile(Location.of(release), releaseBytes.inputStream())
        second.readBytes(release)
        assertThat(cachedFile(2, release)).describedAs("the second replica must hold a copy before the removal").exists()

        first.removeFile(Location.of(release))

        assertThat(cachedFile(2, release))
            .describedAs("the removal on the first replica must drop the copy of the second replica")
            .doesNotExist()
        assertThat(second.getFile(Location.of(release)).isOk)
            .describedAs("the next read on the second replica must go to the store, which no longer holds the release")
            .isFalse()
    }

    @Test
    fun `a metadata file is never stored in the cache`() {
        val first = replica(1)
        first.putFile(Location.of(metadata), "<metadata/>".toByteArray().inputStream())

        first.readBytes(metadata)

        assertThat(root.toPath().resolve("replica-1").resolve("cache").resolve("releases").resolve("data").resolve(metadata))
            .describedAs("mutable metadata must stay at the store")
            .doesNotExist()
    }

}
