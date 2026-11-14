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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** A transport that answers from a map and records every address it was asked for. Each test owns its instance. */
internal class FakeSourceTransport(private val responses: Map<String, FetchResult>) : PluginSourceTransport {

    val requested = mutableListOf<String>()

    override fun fetch(url: String, maxBytes: Long): FetchResult {
        requested += url
        return responses[url] ?: FetchResult.NotFound
    }

}

internal class PluginStoreTest {

    @TempDir
    lateinit var root: Path

    private val cacheRoot: Path
        get() = root.resolve("plugin-cache")

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneOffset.UTC)

    private fun settings(allowUnsigned: Boolean = false, selection: String = COORDINATE, sources: String = SOURCE): PluginStoreSettings =
        PluginStoreSettings.read(
            mapOf(
                "INGOT_PLUGINS" to selection,
                "INGOT_PLUGIN_SOURCES" to sources,
                "INGOT_PLUGIN_ALLOW_UNSIGNED" to allowUnsigned.toString(),
            )
        )

    private fun trustedVerifier(): PluginSignatureVerifier =
        PluginSignatureVerifier(PluginSignatureVerifier.readKeyRings(StoreFixtures.resource("trusted-public.asc")), clock)

    private fun store(
        settings: PluginStoreSettings = settings(),
        transport: PluginSourceTransport,
        verifier: PluginSignatureVerifier? = trustedVerifier(),
        budget: Duration = Duration.ofSeconds(60),
    ): PluginStore = PluginStore(
        settings = settings,
        ingotVersion = "1.5.0",
        cache = PluginCache(cacheRoot),
        transport = transport,
        verifier = verifier,
        clock = clock,
        startupBudget = budget,
    )

    /** The signed catalogue of the fixtures, with its jar and both signatures, as a source serves them. */
    private fun signedSource(): Map<String, FetchResult> = mapOf(
        CATALOGUE_URL to found(StoreFixtures.resource("ingot-plugin-catalogue.json")),
        "$CATALOGUE_URL.asc" to found(StoreFixtures.resource("ingot-plugin-catalogue.json.asc")),
        JAR_URL to found(StoreFixtures.resource("sample-plugin.bin")),
        "$JAR_URL.asc" to found(StoreFixtures.resource("sample-plugin.trusted.asc")),
    )

    @Test
    fun `should make no request and create no cache directory when nothing is selected`() {
        // given: a store with no selection
        val transport = FakeSourceTransport(signedSource())

        // when: the store resolves
        val entries = store(settings = settings(selection = ""), transport = transport).resolve()

        // then: no entry, no request and no cache directory
        assertThat(entries).describedAs("an empty selection resolves to nothing").isEmpty()
        assertThat(transport.requested).describedAs("an installation without selection must make no request").isEmpty()
        assertThat(cacheRoot).describedAs("the cache must not be created without a selection").doesNotExist()
    }

    @Test
    fun `should make no request when the recovery switch is on`() {
        // given: a selection with the recovery switch on
        val recovery = PluginStoreSettings.read(
            mapOf("INGOT_PLUGINS" to COORDINATE, "INGOT_PLUGIN_SOURCES" to SOURCE, "INGOT_PLUGIN_STORE_DISABLED" to "true")
        )
        val transport = FakeSourceTransport(signedSource())

        // when: the store resolves
        val entries = store(settings = recovery, transport = transport).resolve()

        // then: the store loads nothing and asks no source
        assertThat(entries).isEmpty()
        assertThat(transport.requested).describedAs("the recovery switch must make no request").isEmpty()
    }

    @Test
    fun `should refuse a selected plugin when no source is configured`() {
        // given: a selection without a source
        val entry = store(settings = settings(sources = ""), transport = FakeSourceTransport(emptyMap())).resolve().single()

        // then: the plugin is refused and the reason says that no source is configured
        assertThat(entry.state).isEqualTo(StoreState.REFUSED)
        assertThat(entry.reason).describedAs("the reason must name the missing source").contains("no source")
    }

    @Test
    fun `should load a plugin whose catalogue and jar both verify`() {
        // given: a signed catalogue and a jar with a matching signature
        val transport = FakeSourceTransport(signedSource())

        // when: the store resolves
        val entry = store(transport = transport).resolve().single()

        // then: the plugin is loaded from a verified download with the fingerprint of the trusted key
        assertThat(entry.state).describedAs("a verified plugin must be loaded").isEqualTo(StoreState.LOADED)
        assertThat(entry.jar).describedAs("the verified jar must be in the cache").isNotNull
        assertThat(Files.exists(entry.jar!!)).isTrue()
        assertThat(entry.pluginId).isEqualTo("opensearch-plugin")
        assertThat(entry.fingerprint).isEqualTo(StoreFixtures.fingerprint("trusted-public.asc"))
    }

    @Test
    fun `should refuse a jar whose digest differs from the catalogue and cache nothing`() {
        // given: an unsigned catalogue that records another digest for the jar
        val transport = FakeSourceTransport(
            mapOf(CATALOGUE_URL to found(catalogue(sha256 = "0".repeat(64))), JAR_URL to found(StoreFixtures.resource("sample-plugin.bin")))
        )

        // when: the store resolves with unsigned content allowed
        val entry = store(settings = settings(allowUnsigned = true), transport = transport).resolve().single()

        // then: the jar is refused at the digest step and nothing is cached
        assertThat(entry.state).isEqualTo(StoreState.REFUSED)
        assertThat(entry.reason).describedAs("the reason must name the digest step").contains("digest")
        assertThat(entry.jar).describedAs("a refused jar is not cached").isNull()
    }

    @Test
    fun `should refuse a jar signed by a key that is not trusted`() {
        // given: a signed catalogue and a jar signed by the untrusted key
        val source = signedSource() + (("$JAR_URL.asc") to found(StoreFixtures.resource("sample-plugin.untrusted.asc")))

        // when: the store resolves
        val entry = store(transport = FakeSourceTransport(source)).resolve().single()

        // then: the jar is refused at the signature step
        assertThat(entry.state).isEqualTo(StoreState.REFUSED)
        assertThat(entry.reason).describedAs("the reason must say that the key is not trusted").contains("not trusted")
    }

    @Test
    fun `should refuse every entry of a source whose catalogue signature is not trusted`() {
        // given: a catalogue signed by the untrusted key
        val source = signedSource() + (("$CATALOGUE_URL.asc") to found(StoreFixtures.resource("ingot-plugin-catalogue.untrusted.json.asc")))

        // when: the store resolves
        val entry = store(transport = FakeSourceTransport(source)).resolve().single()

        // then: the source is not used and the reason names the catalogue
        assertThat(entry.state).isEqualTo(StoreState.REFUSED)
        assertThat(entry.reason).describedAs("the reason must name the catalogue signature").contains("catalogue").contains("not trusted")
    }

    @Test
    fun `should refuse a yanked entry for a new selection`() {
        // given: an unsigned catalogue whose entry is yanked
        val transport = FakeSourceTransport(mapOf(CATALOGUE_URL to found(catalogue(yanked = true)), JAR_URL to found(jarBytes())))

        // when: the store resolves
        val entry = store(settings = settings(allowUnsigned = true), transport = transport).resolve().single()

        // then: the yanked entry is refused
        assertThat(entry.state).isEqualTo(StoreState.REFUSED)
        assertThat(entry.reason).describedAs("the reason must say the entry is yanked").contains("yanked")
    }

    @Test
    fun `should refuse an entry whose range does not contain the running Ingot version`() {
        // given: an entry that needs Ingot 1.6.0 or later, while the store runs 1.5.0
        val transport = FakeSourceTransport(
            mapOf(CATALOGUE_URL to found(catalogue(compatibility = COMPAT_FROM_1_6)), JAR_URL to found(jarBytes()))
        )

        // when: the store resolves
        val entry = store(settings = settings(allowUnsigned = true), transport = transport).resolve().single()

        // then: the entry is refused and the reason names the range
        assertThat(entry.state).isEqualTo(StoreState.REFUSED)
        assertThat(entry.reason).describedAs("the reason must name the running version").contains("1.5.0")
    }

    @Test
    fun `should refuse an entry without a compatibility block`() {
        // given: an entry without compatibility
        val transport = FakeSourceTransport(mapOf(CATALOGUE_URL to found(catalogue(compatibility = null)), JAR_URL to found(jarBytes())))

        // when: the store resolves
        val entry = store(settings = settings(allowUnsigned = true), transport = transport).resolve().single()

        // then: the entry is refused
        assertThat(entry.state).isEqualTo(StoreState.REFUSED)
        assertThat(entry.reason).describedAs("the reason must say that compatibility is missing").contains("compatibility")
    }

    @Test
    fun `should load an unsigned plugin as unverified when unsigned content is allowed`() {
        // given: an unsigned catalogue and jar, with unsigned content allowed
        val transport = FakeSourceTransport(mapOf(CATALOGUE_URL to found(catalogue()), JAR_URL to found(jarBytes())))

        // when: the store resolves
        val entry = store(settings = settings(allowUnsigned = true), transport = transport).resolve().single()

        // then: the plugin loads and is marked as unverified
        assertThat(entry.state).describedAs("unsigned content with the switch on must load as unverified").isEqualTo(StoreState.UNVERIFIED)
        assertThat(entry.jar).isNotNull
    }

    @Test
    fun `should refuse an unsigned jar when unsigned content is not allowed`() {
        // given: a signed catalogue, and a jar without a signature
        val source = signedSource() - "$JAR_URL.asc"

        // when: the store resolves with the switch off
        val entry = store(transport = FakeSourceTransport(source)).resolve().single()

        // then: the jar is refused at the signature step
        assertThat(entry.state).isEqualTo(StoreState.REFUSED)
        assertThat(entry.reason).describedAs("the reason must say that the jar has no signature").contains("no signature")
    }

    @Test
    fun `should refuse every source when no trusted key is configured and unsigned content is not allowed`() {
        // given: no trusted key and the switch off
        val entry = store(transport = FakeSourceTransport(signedSource()), verifier = null).resolve().single()

        // then: the source is not used and the reason says that no trusted key is configured
        assertThat(entry.state).isEqualTo(StoreState.REFUSED)
        assertThat(entry.reason).describedAs("the reason must say that no trusted key is configured").contains("no trusted key")
    }

    @Test
    fun `should take the plugin from the first source that lists it and not ask the second for the jar`() {
        // given: two sources that both list the plugin
        val first = "https://first.example.org/maven"
        val second = "https://second.example.org/maven"
        val transport = FakeSourceTransport(
            mapOf(
                "$first/ingot-plugin-catalogue.json" to found(catalogue()),
                "$first/$JAR_PATH" to found(jarBytes()),
                "$second/ingot-plugin-catalogue.json" to found(catalogue()),
                "$second/$JAR_PATH" to found(jarBytes()),
            )
        )

        // when: the store resolves with both sources configured
        val entry = store(settings = settings(allowUnsigned = true, sources = "$first,$second"), transport = transport).resolve().single()

        // then: the first source provides the plugin and the second jar is never requested
        assertThat(entry.source).isEqualTo(first)
        assertThat(transport.requested).describedAs("the second source must not be asked for the jar").doesNotContain("$second/$JAR_PATH")
    }

    @Test
    fun `should skip an unreachable source and load the plugin from the next one`() {
        // given: a first source that cannot be reached and a second one that serves the plugin
        val first = "https://first.example.org/maven"
        val second = "https://second.example.org/maven"
        val transport = FakeSourceTransport(
            mapOf(
                "$first/ingot-plugin-catalogue.json" to FetchResult.Unreachable("connection refused"),
                "$second/ingot-plugin-catalogue.json" to found(catalogue()),
                "$second/$JAR_PATH" to found(jarBytes()),
            )
        )

        // when: the store resolves
        val entry = store(settings = settings(allowUnsigned = true, sources = "$first,$second"), transport = transport).resolve().single()

        // then: the plugin is loaded from the second source
        assertThat(entry.state).isEqualTo(StoreState.UNVERIFIED)
        assertThat(entry.source).isEqualTo(second)
    }

    @Test
    fun `should load the verified cache when every source is unreachable`() {
        // given: a plugin that was downloaded and verified on an earlier start
        store(transport = FakeSourceTransport(signedSource())).resolve()

        // when: the next start finds every source unreachable
        val unreachable = FakeSourceTransport(mapOf(CATALOGUE_URL to FetchResult.Unreachable("timeout")))
        val entry = store(transport = unreachable).resolve().single()

        // then: the verified cached jar is loaded without the network
        assertThat(entry.state).describedAs("an offline start must load the verified cache").isEqualTo(StoreState.CACHED)
        assertThat(entry.jar).isNotNull
    }

    @Test
    fun `should refuse a cached jar whose bytes no longer match its record`() {
        // given: a verified plugin in the cache, which is then altered on disk
        store(transport = FakeSourceTransport(signedSource())).resolve()
        Files.write(PluginCache(cacheRoot).jarFile(PluginCoordinate("net.onelitefeather.ingot", "opensearch-plugin", "1.0.0")), "altered".toByteArray())

        // when: the next start finds the source unreachable
        val entry = store(transport = FakeSourceTransport(emptyMap())).resolve().single()

        // then: the altered jar is refused and not loaded
        assertThat(entry.state).isEqualTo(StoreState.REFUSED)
        assertThat(entry.reason).describedAs("the reason must say the cached jar no longer matches").contains("no longer matches")
    }

    @Test
    fun `should not load a cached jar whose signing key is no longer trusted`() {
        // given: a verified plugin in the cache, and a store that now trusts only the untrusted key
        store(transport = FakeSourceTransport(signedSource())).resolve()
        val otherKeys = PluginSignatureVerifier(PluginSignatureVerifier.readKeyRings(StoreFixtures.resource("untrusted-public.asc")), clock)

        // when: the sources are unreachable
        val unreachable = FakeSourceTransport(mapOf(CATALOGUE_URL to FetchResult.Unreachable("timeout")))
        val entry = store(transport = unreachable, verifier = otherKeys).resolve().single()

        // then: the cached jar is not loaded, and the entry is unavailable
        assertThat(entry.state).describedAs("a cached jar whose key is no longer trusted must not load").isEqualTo(StoreState.UNAVAILABLE)
        assertThat(entry.jar).isNull()
    }

    @Test
    fun `should report unavailable when the startup budget is used up before a download`() {
        // given: a zero budget and no cached copy
        val entry = store(transport = FakeSourceTransport(signedSource()), budget = Duration.ZERO).resolve().single()

        // then: no network download is attempted and the entry is unavailable
        assertThat(entry.state).isEqualTo(StoreState.UNAVAILABLE)
        assertThat(entry.reason).describedAs("the reason must name the startup budget").contains("startup budget")
    }

    @Test
    fun `should report unavailable when no source can be reached and nothing is cached`() {
        // given: every source is unreachable
        val transport = FakeSourceTransport(mapOf(CATALOGUE_URL to FetchResult.Unreachable("timeout")))

        // when: the store resolves
        val entry = store(transport = transport).resolve().single()

        // then: the entry is unavailable, which lets the server start without it
        assertThat(entry.state).isEqualTo(StoreState.UNAVAILABLE)
    }

    private fun found(bytes: ByteArray): FetchResult = FetchResult.Found(bytes)

    private fun jarBytes(): ByteArray = StoreFixtures.resource("sample-plugin.bin")

    /** An unsigned catalogue for the coordinate of the fixtures, built from the given parts. */
    private fun catalogue(
        sha256: String = PluginDigest.sha256Hex(jarBytes()),
        compatibility: String? = COMPAT_1_2_TO_2_0,
        yanked: Boolean = false,
    ): ByteArray {
        val compat = compatibility?.let { "\"compatibility\": $it," } ?: ""
        return """
            {
              "schema": 1,
              "plugins": [ {
                "id": "opensearch-plugin",
                "name": "OpenSearch search backend",
                "publisher": "test-publisher",
                "coordinates": "$COORDINATE",
                "sha256": "$sha256",
                $compat
                "yanked": $yanked
              } ]
            }
        """.trimIndent().toByteArray()
    }

    private companion object {
        const val SOURCE = "https://repo.example.org/maven"
        const val COORDINATE = "net.onelitefeather.ingot:opensearch-plugin:1.0.0"
        const val JAR_PATH = "net/onelitefeather/ingot/opensearch-plugin/1.0.0/opensearch-plugin-1.0.0.jar"
        const val CATALOGUE_URL = "$SOURCE/ingot-plugin-catalogue.json"
        const val JAR_URL = "$SOURCE/$JAR_PATH"
        const val COMPAT_1_2_TO_2_0 = """{ "ingot": { "min": "1.2.0", "below": "2.0.0" }, "pluginApi": 1 }"""
        const val COMPAT_FROM_1_6 = """{ "ingot": { "min": "1.6.0", "below": "2.0.0" }, "pluginApi": 1 }"""
    }

}
