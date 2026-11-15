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
import java.nio.file.Path

class CacheEvictionTest {

    @TempDir
    lateinit var directory: Path

    private val clock = MovableClock()

    private val first = "org/example/a/1.0/a-1.0.jar"
    private val second = "org/example/b/1.0/b-1.0.jar"
    private val third = "org/example/c/1.0/c-1.0.jar"
    private val tenBytes = ByteArray(10) { it.toByte() }

    private fun cacheOver(delegate: RecordingDelegate, maxSize: Long) =
        CacheStorageProvider(delegate = delegate, directory = directory.resolve("cache"), maxSize = maxSize, clock = clock)

    private fun CacheStorageProvider.read(key: String): ByteArray =
        getFile(Location.of(key)).get().use { it.readBytes() }

    private fun cachedFile(key: String): Path =
        directory.resolve("cache").resolve("data").resolve(key)

    @Test
    fun `the least recently used copy is evicted when a new copy does not fit`() {
        val delegate = RecordingDelegate(mapOf(first to tenBytes, second to tenBytes, third to tenBytes))
        val cache = cacheOver(delegate, maxSize = 25)
        cache.read(first)
        clock.advance(1_000)
        cache.read(second)
        clock.advance(1_000)
        cache.read(first)
        clock.advance(1_000)

        cache.read(third)

        assertThat(cachedFile(second)).describedAs("the copy read least recently must be evicted").doesNotExist()
        assertThat(cachedFile(first)).describedAs("the copy read most recently must stay").exists()
        assertThat(cachedFile(third)).describedAs("the new copy must be committed").exists()
    }

    @Test
    fun `a copy with an open stream is not evicted`() {
        val delegate = RecordingDelegate(mapOf(first to tenBytes, second to tenBytes))
        val cache = cacheOver(delegate, maxSize = 15)
        val open = cache.getFile(Location.of(first)).get()

        cache.read(second)

        assertThat(cachedFile(first)).describedAs("the copy in use by an open stream must stay").exists()
        assertThat(cachedFile(second)).describedAs("the new copy must not fit beside the copy in use").doesNotExist()
        open.close()
    }

    @Test
    fun `a fill that cannot make room is skipped and counted`() {
        val delegate = RecordingDelegate(mapOf(first to tenBytes, second to tenBytes))
        val cache = cacheOver(delegate, maxSize = 15)
        val open = cache.getFile(Location.of(first)).get()

        val bytes = cache.read(second)

        assertThat(bytes).describedAs("the request must be served from the delegate").isEqualTo(tenBytes)
        assertThat(cache.skippedFills).describedAs("the skipped fill must be counted").isEqualTo(1L)
        open.close()
    }

    @Test
    fun `a file larger than the whole cache is never cached`() {
        val delegate = RecordingDelegate(mapOf(first to tenBytes))
        val cache = cacheOver(delegate, maxSize = 5)

        cache.read(first)

        assertThat(cachedFile(first)).describedAs("a file above maxSize must stay at the delegate").doesNotExist()
    }

    @Test
    fun `an upload is not refused when the cache is full`() {
        val delegate = RecordingDelegate(mapOf(first to tenBytes))
        val cache = CacheStorageProvider(
            delegate = delegate,
            directory = directory.resolve("cache"),
            maxSize = 5,
            cacheOnUpload = true,
            clock = clock,
        )

        val result = cache.putFile(Location.of(second), tenBytes.inputStream())

        assertThat(result.isOk).describedAs("the limit must never refuse a write").isTrue()
        assertThat(delegate.files[second]).describedAs("the delegate must store the upload").isEqualTo(tenBytes)
    }

    @Test
    fun `start removes the leftover temporary files of an earlier run`() {
        val fill = directory.resolve("cache").resolve("fill")
        java.nio.file.Files.createDirectories(fill)
        val leftover = java.nio.file.Files.createFile(fill.resolve("fill-leftover.part"))

        cacheOver(RecordingDelegate(), maxSize = 100)

        assertThat(leftover).describedAs("a temporary file of an earlier run must not survive start-up").doesNotExist()
    }

    @Test
    fun `start indexes the copies on disk so that the first read is a hit`() {
        val copy = directory.resolve("cache").resolve("data").resolve(first)
        java.nio.file.Files.createDirectories(copy.parent)
        java.nio.file.Files.write(copy, tenBytes)
        val delegate = RecordingDelegate(mapOf(first to tenBytes))

        val bytes = cacheOver(delegate, maxSize = 100).read(first)

        assertThat(bytes).describedAs("the copy on disk must be served").isEqualTo(tenBytes)
        assertThat(delegate.fullReads.get()).describedAs("a copy indexed at start must not cost a delegate read").isZero()
    }

    @Test
    fun `start evicts the least recently modified copies until the total fits`() {
        val data = directory.resolve("cache").resolve("data")
        val older = data.resolve(first)
        val newer = data.resolve(second)
        java.nio.file.Files.createDirectories(older.parent)
        java.nio.file.Files.createDirectories(newer.parent)
        java.nio.file.Files.write(older, tenBytes)
        java.nio.file.Files.write(newer, tenBytes)
        java.nio.file.Files.setLastModifiedTime(older, java.nio.file.attribute.FileTime.fromMillis(1_000L))
        java.nio.file.Files.setLastModifiedTime(newer, java.nio.file.attribute.FileTime.fromMillis(2_000L))

        cacheOver(RecordingDelegate(), maxSize = 15)

        assertThat(older).describedAs("the least recently modified copy must be evicted at start").doesNotExist()
        assertThat(newer).describedAs("the most recently modified copy must stay").exists()
    }

    @Test
    fun `a second provider on the same directory is refused and the message names the directory`() {
        val first = cacheOver(RecordingDelegate(), maxSize = 100)

        try {
            org.assertj.core.api.Assertions.assertThatThrownBy { cacheOver(RecordingDelegate(), maxSize = 100) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining(directory.resolve("cache").toString())
        } finally {
            first.shutdown()
        }
    }

}
