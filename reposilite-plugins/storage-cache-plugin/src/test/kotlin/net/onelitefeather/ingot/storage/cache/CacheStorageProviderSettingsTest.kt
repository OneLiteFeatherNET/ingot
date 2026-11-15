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

import com.reposilite.storage.StorageProviderSettings
import net.onelitefeather.ingot.storage.capability.SharedNamespaceAware
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path

private data class TestDelegateSettings(override val type: String = "s3") : StorageProviderSettings

private data class NamespacedDelegateSettings(val namespace: String?) : StorageProviderSettings, SharedNamespaceAware {
    override val type: String = "s3"
    override fun namespaceKey(repositoryName: String): String? = namespace?.let { "$it/$repositoryName" }
    override fun overlaps(key: String, otherKey: String): Boolean = key.startsWith(otherKey) || otherKey.startsWith(key)
}

private fun settings(
    delegate: StorageProviderSettings = TestDelegateSettings(),
    maxSize: String = "5GB",
    maxFileSize: String = "",
    cacheMutable: Boolean = false,
) = CacheStorageProviderSettings(delegate = delegate, maxSize = maxSize, maxFileSize = maxFileSize, cacheMutable = cacheMutable)

class CacheStorageProviderSettingsTest {

    @Test
    fun `type is always cache`() {
        assertThat(settings().type).isEqualTo("cache")
    }

    @Test
    fun `directory defaults to the empty value that resolves to the repository cache folder`() {
        assertThat(settings().directory).isEmpty()
    }

    @Test
    fun `max size defaults to five gigabytes`() {
        assertThat(CacheStorageProviderSettings(delegate = TestDelegateSettings()).maxSize).isEqualTo("5GB")
    }

    @Test
    fun `max file size defaults to no limit`() {
        assertThat(CacheStorageProviderSettings(delegate = TestDelegateSettings()).maxFileSize).isEmpty()
    }

    @Test
    fun `upload caching defaults to off`() {
        assertThat(CacheStorageProviderSettings(delegate = TestDelegateSettings()).cacheOnUpload).isFalse()
    }

    @Test
    fun `mutable caching defaults to off`() {
        assertThat(CacheStorageProviderSettings(delegate = TestDelegateSettings()).cacheMutable).isFalse()
    }

    @Test
    fun `five gigabytes are read as binary units`() {
        assertThat(parseCacheSize("maxSize", "5GB")).isEqualTo(5L * 1024 * 1024 * 1024)
    }

    @Test
    fun `twenty gigabytes are read as binary units`() {
        assertThat(parseCacheSize("maxSize", "20GB")).isEqualTo(20L * 1024 * 1024 * 1024)
    }

    @Test
    fun `five hundred twelve megabytes are read as binary units`() {
        assertThat(parseCacheSize("maxFileSize", "512MB")).isEqualTo(512L * 1024 * 1024)
    }

    @Test
    fun `units are case insensitive`() {
        assertThat(parseCacheSize("maxSize", "5gb")).isEqualTo(5L * 1024 * 1024 * 1024)
    }

    @Test
    fun `a value with a long unit name is refused and the message names the key`() {
        assertThatThrownBy { parseCacheSize("maxSize", "5 gigabytes") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("maxSize")
    }

    @Test
    fun `a negative size is refused`() {
        assertThatThrownBy { parseCacheSize("maxSize", "-1GB") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("maxSize")
    }

    @Test
    fun `a size that overflows a long is refused`() {
        assertThatThrownBy { parseCacheSize("maxSize", "99999999999TB") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("maxSize")
    }

    @Test
    fun `an empty max file size is read as no limit`() {
        assertThat(parseOptionalCacheSize("maxFileSize", "")).isNull()
    }

    @Test
    fun `valid settings pass validation`() {
        assertThat(settings(maxSize = "20GB", maxFileSize = "512MB").validated().maxSize).isEqualTo("20GB")
    }

    @Test
    fun `an unparsable max size fails validation and the message names the key`() {
        assertThatThrownBy { settings(maxSize = "five").validated() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("maxSize")
    }

    @Test
    fun `an unparsable max file size fails validation and the message names the key`() {
        assertThatThrownBy { settings(maxFileSize = "big").validated() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("maxFileSize")
    }

    @Test
    fun `a delegate of the cache type is refused and the message names it`() {
        assertThatThrownBy { settings(delegate = TestDelegateSettings("cache")).validated() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cache")
            .hasMessageContaining("delegate")
    }

    @Test
    fun `mutable caching is refused and the message names the key`() {
        assertThatThrownBy { settings(cacheMutable = true).validated() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cacheMutable")
    }

    @Test
    fun `the default directory is the cache folder of the repository`() {
        val workingDirectory = Path.of("work")
        assertThat(settings().cacheDirectory(workingDirectory, "releases"))
            .isEqualTo(workingDirectory.resolve("cache").resolve("releases"))
    }

    @Test
    fun `an explicit directory is used as given`() {
        val explicit = settings().copy(directory = "/var/lib/ingot-cache")
        assertThat(explicit.cacheDirectory(Path.of("work"), "releases")).isEqualTo(Path.of("/var/lib/ingot-cache"))
    }

    @Test
    fun `the namespace of the cache is the namespace of its delegate`() {
        val cache = settings(delegate = NamespacedDelegateSettings("https://s3/bucket"))
        assertThat(cache.namespaceKey("releases")).isEqualTo("https://s3/bucket/releases")
    }

    @Test
    fun `a delegate without a namespace gives the cache no namespace`() {
        assertThat(settings(delegate = NamespacedDelegateSettings(null)).namespaceKey("releases")).isNull()
    }

    @Test
    fun `the collision rule of the cache is the rule of its delegate`() {
        val cache = settings(delegate = NamespacedDelegateSettings("https://s3/bucket"))
        assertThat(cache.overlaps("https://s3/bucket/releases", "https://s3/bucket/releases/sub")).isTrue()
    }

}
