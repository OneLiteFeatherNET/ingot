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

package net.onelitefeather.ingot.plugin.store

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.walk

internal class PluginCacheTest {

    @TempDir
    lateinit var root: Path

    private val coordinate = PluginCoordinate("net.onelitefeather.ingot", "opensearch-plugin", "1.0.0")
    private val jar = StoreFixtures.resource("sample-plugin.bin")
    private val signature = StoreFixtures.resource("sample-plugin.trusted.asc")
    private val record = CacheRecord(
        sha256 = PluginDigest.sha256Hex(jar),
        pluginId = "opensearch-plugin",
        source = "https://repo.example.org/maven",
        compatibility = Compatibility(ingotMin = "1.2.0", ingotBelow = "2.0.0", pluginApi = 1),
        signed = true,
        fingerprint = "FAKE",
        verifiedAt = Instant.parse("2026-11-14T00:00:00Z"),
    )

    @Test
    fun `should write the jar, its signature and its record into the coordinate directory`() {
        // when: a verified plugin is written to the cache
        PluginCache(root.resolve("plugin-cache")).write(coordinate, jar, signature, record)

        // then: the three files sit under the group, artifact and version directories
        val directory = root.resolve("plugin-cache/net/onelitefeather/ingot/opensearch-plugin/1.0.0")
        assertThat(directory.resolve("opensearch-plugin-1.0.0.jar")).describedAs("the jar must be cached").exists()
        assertThat(directory.resolve("opensearch-plugin-1.0.0.jar.asc")).describedAs("the signature must be cached").exists()
        assertThat(directory.resolve("record.json")).describedAs("the record must be cached").exists()
    }

    @Test
    fun `should read back the record that was written`() {
        // given: a verified plugin in the cache
        val cache = PluginCache(root.resolve("plugin-cache"))
        cache.write(coordinate, jar, signature, record)

        // when: the plugin is read
        val cached = cache.read(coordinate)

        // then: the record is the one that was written
        assertThat(cached?.record).describedAs("the record must survive the round trip").isEqualTo(record)
    }

    @Test
    fun `should not read a jar whose record was never written`() {
        // given: a jar without its record, as a crash between the two writes would leave it
        val cache = PluginCache(root.resolve("plugin-cache"))
        cache.jarFile(coordinate).parent.createDirectories()
        Files.write(cache.jarFile(coordinate), jar)

        // when: the cache is read
        val cached = cache.read(coordinate)

        // then: the jar is not treated as verified
        assertThat(cached).describedAs("a jar without a record must not be read as cached").isNull()
    }

    @Test
    fun `should leave no temporary file behind after a write`() {
        // given: a cache root
        val cacheRoot = root.resolve("plugin-cache")

        // when: a plugin is written
        PluginCache(cacheRoot).write(coordinate, jar, signature, record)

        // then: no temporary file remains in the cache
        val temporary = cacheRoot.walk().filter { it.name.endsWith(".tmp") }.toList()
        assertThat(temporary).describedAs("an atomic write must not leave a temporary file").isEmpty()
    }

}
