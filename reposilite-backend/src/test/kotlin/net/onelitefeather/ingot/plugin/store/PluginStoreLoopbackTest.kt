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

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The real HTTP transport against a loopback server on an ephemeral port. The settings are built directly, because the
 * parser refuses plain HTTP, and this test serves the source over plain HTTP on the loopback address only.
 */
internal class PluginStoreLoopbackTest {

    @TempDir
    lateinit var root: Path

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneOffset.UTC)

    private var server: HttpServer? = null
    private lateinit var source: String

    @BeforeEach
    fun startSource() {
        val started = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        started.createContext("/") { exchange ->
            val body = FILES[exchange.requestURI.path.removePrefix(MOUNT)]
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        started.start()
        server = started
        source = "http://127.0.0.1:${started.address.port}$MOUNT"
    }

    @AfterEach
    fun stopSource() {
        server?.stop(0)
        server = null
    }

    @Test
    fun `should download a signed plugin from a loopback source and keep it in the cache`() {
        // when: the store resolves against the loopback source
        val entry = store().resolve().single()

        // then: the jar is loaded, verified, and cached under the plugin cache
        assertThat(entry.state).describedAs("a signed plugin from the source must load").isEqualTo(StoreState.LOADED)
        assertThat(entry.jar).isNotNull
        assertThat(Files.exists(entry.jar!!)).describedAs("the verified jar must exist in the cache").isTrue()
        assertThat(entry.jar!!.startsWith(root.resolve("plugin-cache")))
            .describedAs("the jar must be cached below the data directory, not in the plugins directory")
            .isTrue()
    }

    @Test
    fun `should load the verified cached copy when the source has stopped`() {
        // given: a plugin downloaded on an earlier start
        store().resolve()

        // when: the source stops answering and the next start resolves again
        server?.stop(0)
        server = null
        val entry = store().resolve().single()

        // then: the verified cached copy is loaded without the network
        assertThat(entry.state).describedAs("an offline start must load the verified cache").isEqualTo(StoreState.CACHED)
    }

    private fun store(): PluginStore {
        val settings = PluginStoreSettings(
            selection = listOf(PluginCoordinate("net.onelitefeather.ingot", "opensearch-plugin", "1.0.0")),
            sources = listOf(source),
            trustedKeysLocation = null,
            allowUnsigned = false,
            disabled = false,
            problems = emptyList(),
        )
        return PluginStore(
            settings = settings,
            ingotVersion = "1.5.0",
            cache = PluginCache(root.resolve("plugin-cache")),
            transport = HttpPluginSourceTransport(),
            verifier = PluginSignatureVerifier(PluginSignatureVerifier.readKeyRings(StoreFixtures.resource("trusted-public.asc")), clock),
            clock = clock,
        )
    }

    private companion object {
        const val MOUNT = "/maven"

        /** The files that the loopback source serves, by their path below the mount. */
        val FILES: Map<String, ByteArray> by lazy {
            mapOf(
                "/ingot-plugin-catalogue.json" to StoreFixtures.resource("ingot-plugin-catalogue.json"),
                "/ingot-plugin-catalogue.json.asc" to StoreFixtures.resource("ingot-plugin-catalogue.json.asc"),
                "/net/onelitefeather/ingot/opensearch-plugin/1.0.0/opensearch-plugin-1.0.0.jar" to StoreFixtures.resource("sample-plugin.bin"),
                "/net/onelitefeather/ingot/opensearch-plugin/1.0.0/opensearch-plugin-1.0.0.jar.asc" to StoreFixtures.resource("sample-plugin.trusted.asc"),
            )
        }
    }

}
