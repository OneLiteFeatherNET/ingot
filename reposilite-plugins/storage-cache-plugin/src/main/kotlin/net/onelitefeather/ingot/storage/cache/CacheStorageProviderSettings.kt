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

import com.reposilite.storage.StorageProviderSettings
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import net.onelitefeather.ingot.storage.NestedStorageProviderSettings
import net.onelitefeather.ingot.storage.capability.SharedNamespaceAware
import net.onelitefeather.ingot.storage.capability.SupportDetails
import java.nio.file.Path

/** The storage type of the cache, the value of `type` in a storage block. */
internal const val CACHE_STORAGE_TYPE = "cache"

/**
 * The settings of the read-through cache. The cache serves immutable files from a local directory and sends everything else to the
 * [delegate], which stays authoritative. Every key has a default except the delegate. Sizes use binary units.
 */
data class CacheStorageProviderSettings(
    override val type: String = CACHE_STORAGE_TYPE,
    override val delegate: StorageProviderSettings,
    val directory: String = "",
    val maxSize: String = "5GB",
    val maxFileSize: String = "",
    val cacheOnUpload: Boolean = false,
    val cacheMutable: Boolean = false,
) : NestedStorageProviderSettings, SharedNamespaceAware, SupportDetails {

    override fun withDelegate(delegate: StorageProviderSettings): CacheStorageProviderSettings = copy(delegate = delegate)


    /** The namespace of the delegate, so that a cache and a plain repository over the same store collide as they would without the cache. */
    override fun namespaceKey(repositoryName: String): String? =
        (delegate as? SharedNamespaceAware)?.namespaceKey(repositoryName)

    override fun overlaps(key: String, otherKey: String): Boolean =
        (delegate as? SharedNamespaceAware)?.overlaps(key, otherKey) ?: (key == otherKey)

    override fun describe(): Map<String, String> =
        (delegate as? SupportDetails)?.describe().orEmpty()

    /**
     * Checks the settings that the factory cannot leave to the delegate. Every failure names the key, and never the value of a secret.
     *
     * @throws IllegalArgumentException when a setting is not allowed
     */
    fun validated(): CacheStorageProviderSettings {
        require(delegate.type != CACHE_STORAGE_TYPE) {
            "The delegate of a cache must be a storage of another type, a cache over a cache is refused"
        }
        require(!cacheMutable) {
            "cacheMutable is reserved and must be false, because mutable keys are never cached"
        }
        parseCacheSize("maxSize", maxSize)
        parseOptionalCacheSize("maxFileSize", maxFileSize)
        return this
    }

    /** The directory of the cache: [directory] when it is set, otherwise the cache folder of the repository in the working directory. */
    fun cacheDirectory(workingDirectory: Path, repositoryName: String): Path =
        if (directory.isBlank()) workingDirectory.resolve("cache").resolve(repositoryName) else Path.of(directory)

}

private val SIZE_PATTERN = Regex("^(\\d+)(B|KB|MB|GB|TB)$", RegexOption.IGNORE_CASE)

/**
 * Reads a size such as `5GB` in binary units. The message names [key] and the form of the value, never the value itself.
 *
 * @throws IllegalArgumentException when the value is not a number followed by one of B, KB, MB, GB or TB, or when it overflows
 */
internal fun parseCacheSize(key: String, value: String): Long {
    val match = SIZE_PATTERN.matchEntire(value.trim())
        ?: throw IllegalArgumentException("$key must be a whole number followed by B, KB, MB, GB or TB, for example 5GB")
    val (number, unit) = match.destructured
    val multiplier = when (unit.uppercase()) {
        "B" -> 1L
        "KB" -> 1L shl 10
        "MB" -> 1L shl 20
        "GB" -> 1L shl 30
        else -> 1L shl 40
    }
    return try {
        Math.multiplyExact(number.toLong(), multiplier)
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("$key is too large")
    } catch (_: NumberFormatException) {
        throw IllegalArgumentException("$key is too large")
    }
}

/** Like [parseCacheSize], but a blank value means no limit and gives null. */
internal fun parseOptionalCacheSize(key: String, value: String): Long? =
    if (value.isBlank()) null else parseCacheSize(key, value)
