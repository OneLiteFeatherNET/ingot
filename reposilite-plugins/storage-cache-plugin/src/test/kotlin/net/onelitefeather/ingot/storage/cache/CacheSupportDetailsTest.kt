@file:OptIn(net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi::class)

package net.onelitefeather.ingot.storage.cache

import com.reposilite.storage.api.Location
import net.onelitefeather.ingot.storage.capability.SupportDetails
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The counters of the cache in the support details of its repository. */
class CacheSupportDetailsTest {

    @TempDir
    lateinit var directory: Path

    private val clock = MovableClock()

    private val first = "org/example/a/1.0/a-1.0.jar"
    private val second = "org/example/b/1.0/b-1.0.jar"
    private val tenBytes = ByteArray(10) { it.toByte() }

    private fun cacheOver(delegate: RecordingDelegate, maxSize: Long) =
        CacheStorageProvider(delegate = delegate, directory = directory.resolve("cache"), maxSize = maxSize, clock = clock)

    private fun CacheStorageProvider.read(key: String): ByteArray =
        getFile(Location.of(key)).get().use { it.readBytes() }

    private fun CacheStorageProvider.details(): Map<String, String> =
        (this as SupportDetails).describe()

    @Test
    fun `the support details count one miss and one fill for the first read and one hit for the second`() {
        // given: a cache over a delegate that holds the file
        val cache = cacheOver(RecordingDelegate(mapOf(first to tenBytes)), maxSize = 100)

        // when: the same file is read twice
        cache.read(first)
        cache.read(first)

        // then: the first read missed and filled, and the second read was a hit
        assertThat(cache.details())
            .containsEntry("misses", "1")
            .containsEntry("fills", "1")
            .containsEntry("hits", "1")
    }

    @Test
    fun `the support details report the bytes held and the limit of the cache`() {
        // given: a cache with a limit of 100 bytes
        val cache = cacheOver(RecordingDelegate(mapOf(first to tenBytes)), maxSize = 100)

        // when: one file of 10 bytes is cached
        cache.read(first)

        // then: the cache holds 10 bytes of its 100
        assertThat(cache.details())
            .containsEntry("bytes", "10")
            .containsEntry("maxBytes", "100")
    }

    @Test
    fun `the support details count an eviction when a copy makes room for another`() {
        // given: a cache that fits one 10-byte copy
        val cache = cacheOver(RecordingDelegate(mapOf(first to tenBytes, second to tenBytes)), maxSize = 15)

        // when: the second file is read, so the first copy is evicted
        cache.read(first)
        cache.read(second)

        // then: one eviction is counted
        assertThat(cache.details()).containsEntry("evictions", "1")
    }

    @Test
    fun `the support details count a skipped fill when no copy can be evicted`() {
        // given: a cache whose only copy is open, so it cannot be evicted
        val delegate = RecordingDelegate(mapOf(first to tenBytes, second to tenBytes))
        val cache = cacheOver(delegate, maxSize = 15)
        val open = cache.getFile(Location.of(first)).get()

        // when: a second file is read and cannot be cached
        cache.read(second)
        open.close()

        // then: the skipped fill is counted
        assertThat(cache.details()).containsEntry("skippedFills", "1")
    }

    @Test
    fun `the support details count a fill failure when the copy does not match the delegate size`() {
        // given: a delegate that reports a size that the bytes do not have
        val delegate = RecordingDelegate(mapOf(first to tenBytes)).apply { reportedSizeOverride = 3L }
        val cache = cacheOver(delegate, maxSize = 100)

        // when: the file is read and the copy is refused
        cache.read(first)

        // then: the failed fill is counted and no fill is
        assertThat(cache.details())
            .containsEntry("fillFailures", "1")
            .containsEntry("fills", "0")
    }

    @Test
    fun `the support details never contain the cache directory`() {
        // given: a cache that holds one copy
        val cache = cacheOver(RecordingDelegate(mapOf(first to tenBytes)), maxSize = 100)
        cache.read(first)

        // when: the details are read
        val values = cache.details().values

        // then: no value names a path of the replica
        assertThat(values).noneMatch { it.contains(directory.toString()) }
    }

}
