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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CacheReadPathTest {

    @TempDir
    lateinit var directory: Path

    private val release = "org/example/app/1.0/app-1.0.jar"
    private val releaseBytes = "0123456789abcdefghij".toByteArray()

    private fun delegateWithRelease(): RecordingDelegate =
        RecordingDelegate(mapOf(release to releaseBytes))

    private fun cacheOver(delegate: RecordingDelegate, maxFileSize: Long? = null) =
        CacheStorageProvider(delegate = delegate, directory = directory.resolve("cache"), maxFileSize = maxFileSize)

    private fun CacheStorageProvider.read(key: String): ByteArray =
        getFile(Location.of(key)).get().use { it.readBytes() }

    private fun cachedFile(key: String): Path =
        directory.resolve("cache").resolve("data").resolve(key)

    @Test
    fun `a hit serves the bytes without a delegate read`() {
        val delegate = delegateWithRelease()
        val cache = cacheOver(delegate)
        cache.read(release)

        val bytes = cache.read(release)

        assertThat(bytes).describedAs("the second read must return the released bytes").isEqualTo(releaseBytes)
        assertThat(delegate.fullReads.get()).describedAs("only the first read may reach the delegate").isEqualTo(1)
    }

    @Test
    fun `a miss fills the cache and serves the file`() {
        val cache = cacheOver(delegateWithRelease())

        val bytes = cache.read(release)

        assertThat(bytes).describedAs("the miss must serve the delegate's bytes").isEqualTo(releaseBytes)
        assertThat(cachedFile(release)).describedAs("the miss must leave a copy in the data folder").exists()
    }

    @Test
    fun `concurrent misses on one key share one delegate read`() {
        val delegate = delegateWithRelease()
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        delegate.onFullRead = {
            entered.countDown()
            gate.await()
        }
        val cache = cacheOver(delegate)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<ByteArray> { cache.read(release) }
            assertThat(entered.await(5, TimeUnit.SECONDS)).describedAs("the first request must reach the delegate").isTrue()
            val second = executor.submit<ByteArray> { cache.read(release) }
            gate.countDown()

            assertThat(first.get(5, TimeUnit.SECONDS)).describedAs("the first request must get the bytes").isEqualTo(releaseBytes)
            assertThat(second.get(5, TimeUnit.SECONDS)).describedAs("the waiting request must get the same bytes").isEqualTo(releaseBytes)
        } finally {
            executor.shutdownNow()
        }
        assertThat(delegate.fullReads.get()).describedAs("two concurrent misses must cause one delegate read").isEqualTo(1)
    }

    @Test
    fun `a size mismatch deletes the copy and serves from a new delegate stream`() {
        val delegate = delegateWithRelease()
        delegate.reportedSizeOverride = releaseBytes.size + 1L
        val cache = cacheOver(delegate)

        val bytes = cache.read(release)

        assertThat(bytes).describedAs("the request must be served from the delegate").isEqualTo(releaseBytes)
        assertThat(cachedFile(release)).describedAs("a copy with the wrong size must not be committed").doesNotExist()
        assertThat(delegate.fullReads.get()).describedAs("the mismatch costs one more delegate stream").isEqualTo(2)
    }

    @Test
    fun `a range on a hit returns the requested bytes`() {
        val cache = cacheOver(delegateWithRelease())
        cache.read(release)

        val range = cache.getFile(Location.of(release), 2, 3).get().use { it.readBytes() }

        assertThat(range).describedAs("bytes 2 to 4 of the release").isEqualTo("234".toByteArray())
    }

    @Test
    fun `a range on a hit does not reach the delegate`() {
        val delegate = delegateWithRelease()
        val cache = cacheOver(delegate)
        cache.read(release)

        cache.getFile(Location.of(release), 2, 3).get().use { it.readBytes() }

        assertThat(delegate.rangedReads.get()).describedAs("a range on a hit is served locally").isZero()
        assertThat(delegate.fullReads.get()).describedAs("a range on a hit is served locally").isEqualTo(1)
    }

    @Test
    fun `a range miss is passed to the delegate and creates no file`() {
        val delegate = delegateWithRelease()
        val cache = cacheOver(delegate)

        val range = cache.getFile(Location.of(release), 2, 3).get().use { it.readBytes() }

        assertThat(range).describedAs("the delegate's ranged read answers the request").isEqualTo("234".toByteArray())
        assertThat(delegate.rangedReads.get()).describedAs("the range must go to the delegate's ranged read").isEqualTo(1)
        assertThat(cachedFile(release)).describedAs("a ranged miss must not start a fill").doesNotExist()
    }

    @Test
    fun `a file above the file size limit is never cached`() {
        val delegate = delegateWithRelease()
        val cache = cacheOver(delegate, maxFileSize = releaseBytes.size - 1L)

        cache.read(release)

        assertThat(cachedFile(release)).describedAs("the file exceeds maxFileSize and must stay at the delegate").doesNotExist()
    }

    @Test
    fun `a mutable key bypasses the cache`() {
        val metadata = "org/example/app/maven-metadata.xml"
        val delegate = RecordingDelegate(mapOf(metadata to "<metadata/>".toByteArray()))
        val cache = cacheOver(delegate)

        cache.read(metadata)
        cache.read(metadata)

        assertThat(delegate.fullReads.get()).describedAs("every read of mutable metadata goes to the delegate").isEqualTo(2)
        assertThat(cachedFile(metadata)).describedAs("mutable metadata is never stored").doesNotExist()
    }

    @Test
    fun `a cached file that vanished is served from the delegate`() {
        val delegate = delegateWithRelease()
        val cache = cacheOver(delegate)
        cache.read(release)
        Files.delete(cachedFile(release))

        val bytes = cache.read(release)

        assertThat(bytes).describedAs("the request must still be answered").isEqualTo(releaseBytes)
        assertThat(delegate.fullReads.get()).describedAs("a vanished copy costs a delegate read").isEqualTo(2)
    }

    @Test
    fun `the size of a file is answered by the delegate`() {
        val delegate = delegateWithRelease()
        val cache = cacheOver(delegate)

        val size = cache.getFileSize(Location.of(release)).get()

        assertThat(size).describedAs("the size must be the delegate's answer").isEqualTo(releaseBytes.size.toLong())
        assertThat(delegate.sizeQueries.get()).describedAs("the size query must reach the delegate").isEqualTo(1)
    }

}
