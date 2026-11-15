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

package net.onelitefeather.ingot.storage.cache

import com.reposilite.storage.api.Location
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CacheWritePathTest {

    @TempDir
    lateinit var directory: Path

    private val release = "org/example/app/1.0/app-1.0.jar"
    private val releaseBytes = "0123456789abcdefghij".toByteArray()
    private val newBytes = "replacement-bytes-of-a-release".toByteArray()

    private val published = CopyOnWriteArrayList<Collection<String>>()
    private val reports = CopyOnWriteArrayList<String>()

    private fun cacheOver(
        delegate: RecordingDelegate,
        cacheOnUpload: Boolean = false,
    ) = CacheStorageProvider(
        delegate = delegate,
        directory = directory.resolve("cache"),
        cacheOnUpload = cacheOnUpload,
        publishInvalidation = { published.add(it) },
        onFailure = { reports.add(it) },
    )

    private fun CacheStorageProvider.read(key: String): ByteArray =
        getFile(Location.of(key)).get().use { it.readBytes() }

    private fun cachedFile(key: String): Path =
        directory.resolve("cache").resolve("data").resolve(key)

    @Test
    fun `an upload without upload caching leaves no copy in the cache`() {
        val delegate = RecordingDelegate()
        val cache = cacheOver(delegate, cacheOnUpload = false)

        cache.putFile(Location.of(release), newBytes.inputStream())

        assertThat(delegate.files[release]).describedAs("the delegate must store the upload").isEqualTo(newBytes)
        assertThat(cachedFile(release)).describedAs("without upload caching the cache holds no copy").doesNotExist()
    }

    @Test
    fun `an upload with upload caching commits the copy after the delegate accepted it`() {
        val delegate = RecordingDelegate()
        val cache = cacheOver(delegate, cacheOnUpload = true)

        cache.putFile(Location.of(release), newBytes.inputStream())

        assertThat(delegate.files[release]).describedAs("the delegate must store the upload").isEqualTo(newBytes)
        assertThat(cachedFile(release)).describedAs("the copy made during the upload must be committed").hasBinaryContent(newBytes)
    }

    @Test
    fun `a refused delegate write leaves no copy and no entry`() {
        val delegate = RecordingDelegate()
        delegate.failWrites = true
        val cache = cacheOver(delegate, cacheOnUpload = true)

        val result = cache.putFile(Location.of(release), newBytes.inputStream())

        assertThat(result.isOk).describedAs("the client must receive the delegate's refusal").isFalse()
        assertThat(cachedFile(release)).describedAs("a refused write must not leave a copy").doesNotExist()
    }

    @Test
    fun `a cache that cannot write its copy still lets the upload succeed`() {
        val delegate = RecordingDelegate()
        val cache = cacheOver(delegate, cacheOnUpload = true)
        Files.delete(directory.resolve("cache").resolve("fill"))

        val result = cache.putFile(Location.of(release), newBytes.inputStream())

        assertThat(result.isOk).describedAs("the upload must succeed at the delegate").isTrue()
        assertThat(delegate.files[release]).describedAs("the delegate must store the upload").isEqualTo(newBytes)
        assertThat(reports).describedAs("the cache failure must be reported").isNotEmpty()
    }

    @Test
    fun `a removal drops the local copy and publishes one invalidation`() {
        val delegate = RecordingDelegate(mapOf(release to releaseBytes))
        val cache = cacheOver(delegate)
        cache.read(release)

        cache.removeFile(Location.of(release))

        assertThat(cachedFile(release)).describedAs("the removed release must not be served from the cache").doesNotExist()
        assertThat(published).describedAs("exactly one invalidation must be published").containsExactly(listOf(release))
    }

    @Test
    fun `a refused removal publishes nothing`() {
        val delegate = RecordingDelegate(mapOf(release to releaseBytes))
        delegate.failRemovals = true
        val cache = cacheOver(delegate)

        cache.removeFile(Location.of(release))

        assertThat(published).describedAs("the delegate still holds the file, so nothing is invalidated").isEmpty()
    }

    @Test
    fun `an overwrite drops the local copy even without upload caching`() {
        val delegate = RecordingDelegate(mapOf(release to releaseBytes))
        val cache = cacheOver(delegate, cacheOnUpload = false)
        cache.read(release)

        cache.putFile(Location.of(release), newBytes.inputStream())

        assertThat(cachedFile(release)).describedAs("the overwritten release must leave the cache").doesNotExist()
        assertThat(published).describedAs("the overwrite must be published").containsExactly(listOf(release))
    }

    @Test
    fun `a fill that was overtaken by a removal does not commit its bytes`() {
        val delegate = RecordingDelegate(mapOf(release to releaseBytes))
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        delegate.onFullRead = {
            entered.countDown()
            gate.await()
        }
        val cache = cacheOver(delegate)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val read = executor.submit<Any> { cache.getFile(Location.of(release)) }
            assertThat(entered.await(5, TimeUnit.SECONDS)).describedAs("the fill must reach the delegate").isTrue()
            cache.removeFile(Location.of(release))
            delegate.onFullRead = {}
            gate.countDown()
            read.get(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        assertThat(cachedFile(release)).describedAs("bytes of a removed release must not be committed").doesNotExist()
    }

}
