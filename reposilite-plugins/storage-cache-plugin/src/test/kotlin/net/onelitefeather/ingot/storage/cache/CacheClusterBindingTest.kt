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

import com.reposilite.storage.api.Location
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.capability.ClusterBus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

class CacheClusterBindingTest {

    @TempDir
    lateinit var directory: Path

    private val release = "org/example/app/1.0/app-1.0.jar"
    private val releaseBytes = "release-bytes".toByteArray()

    private class RecordingBus : ClusterBus {
        val published = CopyOnWriteArrayList<Pair<String, List<String>>>()

        override fun publishInvalidation(repository: String, keys: Collection<String>) {
            published.add(repository to keys.toList())
        }
    }

    private fun boundCache(delegate: RecordingDelegate, bus: ClusterBus): CacheStorageProvider =
        CacheStorageProvider(
            delegate = delegate,
            directory = directory.resolve("cache"),
            repositoryName = "releases",
        ).also { it.bind(bus, Duration.ofSeconds(300)) }

    private fun cachedFile(key: String): Path =
        directory.resolve("cache").resolve("data").resolve(key)

    @Test
    fun `a bound cache publishes its removal under the name of its repository`() {
        val bus = RecordingBus()
        val cache = boundCache(RecordingDelegate(mapOf(release to releaseBytes)), bus)

        cache.removeFile(Location.of(release))

        assertThat(bus.published)
            .describedAs("the removal must reach the other replicas under the repository name")
            .containsExactly("releases" to listOf(release))
    }

    @Test
    fun `an invalidation from another replica drops the copy and is not published again`() {
        val bus = RecordingBus()
        val cache = boundCache(RecordingDelegate(mapOf(release to releaseBytes)), bus)
        cache.getFile(Location.of(release)).get().use { it.readBytes() }

        cache.invalidate(listOf(release))

        assertThat(cachedFile(release)).describedAs("the received invalidation must drop the local copy").doesNotExist()
        assertThat(bus.published).describedAs("applying a received invalidation must not publish it").isEmpty()
    }

}
