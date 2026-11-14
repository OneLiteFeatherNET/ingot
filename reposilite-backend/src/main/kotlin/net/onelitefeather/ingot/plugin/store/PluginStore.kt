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

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** The state of one selected plugin after the store has resolved it. The loader adds [FAILED] and [SHADOWED]. */
enum class StoreState {
    /** Downloaded, digest and signature verified, and cached. */
    LOADED,

    /** Loaded from the cache, whose digest and signature were checked again against the local copy. */
    CACHED,

    /** Loaded without a signature, because unsigned content is allowed. Marked as unverified in the status. */
    UNVERIFIED,

    /** Not loaded, because a check failed or the entry may not be selected. The reason names the step. */
    REFUSED,

    /** Not loaded, because no source could be reached and no verified copy is cached, or the startup budget ran out. */
    UNAVAILABLE,

    /** Loaded, but its class could not be initialised. The server starts without it. */
    FAILED,

    /** Not loaded, because a jar of the plugins directory or a bundled jar already has its plugin id. */
    SHADOWED,
}

/** One selected plugin with the outcome of the store. [jar] is set only when the plugin may be loaded. */
data class StoreEntry(
    val coordinate: PluginCoordinate,
    val state: StoreState,
    val pluginId: String? = null,
    val jar: Path? = null,
    val source: String? = null,
    val fingerprint: String? = null,
    val reason: String? = null,
) {

    val loadable: Boolean
        get() = jar != null && state in setOf(StoreState.LOADED, StoreState.CACHED, StoreState.UNVERIFIED)

}

/**
 * Resolves the selection of the plugin store. A verified cached copy is used first and needs no network. Otherwise the
 * sources are asked in configured order, and the first one that lists the coordinate provides it. A jar is written to the
 * cache only after its digest and signature verified, so nothing unverified is ever cached.
 *
 * [verifier] is null when no trusted key is configured. A store without any selection does nothing and touches nothing.
 */
class PluginStore(
    private val settings: PluginStoreSettings,
    private val ingotVersion: String,
    private val cache: PluginCache,
    private val transport: PluginSourceTransport,
    private val verifier: PluginSignatureVerifier?,
    private val clock: Clock,
    private val startupBudget: Duration = DEFAULT_STARTUP_BUDGET,
) {

    private val listings = mutableMapOf<String, SourceListing>()

    fun resolve(): List<StoreEntry> {
        if (!settings.isActive) {
            return emptyList()
        }
        val deadline = clock.instant().plus(startupBudget)
        return settings.selection.map { resolveOne(it, deadline) }
    }

    private fun resolveOne(coordinate: PluginCoordinate, deadline: Instant): StoreEntry {
        cachedEntry(coordinate)?.let { return it }

        if (!clock.instant().isBefore(deadline)) {
            return StoreEntry(coordinate, StoreState.UNAVAILABLE, reason = "the startup budget of ${startupBudget.seconds} seconds is used up")
        }
        if (settings.sources.isEmpty()) {
            return StoreEntry(coordinate, StoreState.REFUSED, reason = "no source is configured")
        }

        val refusals = mutableListOf<String>()
        var unreachable = false
        for (source in settings.sources) {
            if (!clock.instant().isBefore(deadline)) {
                return StoreEntry(coordinate, StoreState.UNAVAILABLE, reason = "the startup budget of ${startupBudget.seconds} seconds is used up")
            }
            when (val listing = listingOf(source)) {
                is SourceListing.Unreachable -> {
                    unreachable = true
                    refusals += "$source: ${listing.reason}"
                }
                is SourceListing.Refused -> refusals += "$source: ${listing.reason}"
                is SourceListing.Listed -> {
                    val entry = listing.catalogue.entries.firstOrNull { it.coordinate == coordinate } ?: continue
                    return download(coordinate, entry, source)
                }
            }
        }

        val reason = refusals.joinToString("; ").ifEmpty { "no source lists the plugin" }
        return StoreEntry(coordinate, if (unreachable) StoreState.UNAVAILABLE else StoreState.REFUSED, reason = reason)
    }

    /** The verified cached jar, or a refusal when the cache cannot be trusted. Null means that the sources decide. */
    private fun cachedEntry(coordinate: PluginCoordinate): StoreEntry? {
        val cached = cache.read(coordinate) ?: return null
        val jar = Files.readAllBytes(cached.jar)
        if (PluginDigest.sha256Hex(jar) != cached.record.sha256) {
            return StoreEntry(coordinate, StoreState.REFUSED, reason = "the cached jar no longer matches its record, so it is not loaded")
        }
        CompatibilityRule.check(cached.record.compatibility, ingotVersion)?.let { reason ->
            return StoreEntry(coordinate, StoreState.REFUSED, reason = reason)
        }

        if (!cached.record.signed) {
            if (!settings.allowUnsigned) {
                return StoreEntry(coordinate, StoreState.REFUSED, reason = "the cached jar is unsigned and unsigned content is not allowed")
            }
            return cachedLoad(coordinate, cached, StoreState.UNVERIFIED, fingerprint = null)
        }

        // A signature that no trusted key verifies any more is not loaded. The sources may still provide a verified copy.
        val outcome = cached.signature?.let { signature -> verifier?.verify(jar, signature) } ?: return null
        return when (outcome) {
            is SignatureOutcome.Verified -> cachedLoad(coordinate, cached, StoreState.CACHED, outcome.fingerprint)
            is SignatureOutcome.Refused -> null
        }
    }

    private fun cachedLoad(coordinate: PluginCoordinate, cached: CachedPlugin, state: StoreState, fingerprint: String?): StoreEntry =
        StoreEntry(
            coordinate = coordinate,
            state = state,
            pluginId = cached.record.pluginId,
            jar = cached.jar,
            source = cached.record.source,
            fingerprint = fingerprint,
        )

    private fun listingOf(source: String): SourceListing =
        listings.getOrPut(source) { loadListing(source) }

    private fun loadListing(source: String): SourceListing {
        if (verifier == null && !settings.allowUnsigned) {
            return SourceListing.Refused("no trusted key is configured, so the source is not used")
        }

        val catalogueUrl = "${source.trimEnd('/')}/${PluginCatalogue.FILE_NAME}"
        val bytes = when (val fetched = transport.fetch(catalogueUrl, CATALOGUE_LIMIT)) {
            is FetchResult.Found -> fetched.bytes
            FetchResult.NotFound -> return SourceListing.Refused("the source has no ${PluginCatalogue.FILE_NAME}")
            is FetchResult.Unreachable -> return SourceListing.Unreachable(fetched.reason)
        }

        if (verifier != null) {
            when (val signature = transport.fetch("$catalogueUrl.asc", SIGNATURE_LIMIT)) {
                is FetchResult.Found -> when (val outcome = verifier.verify(bytes, signature.bytes)) {
                    is SignatureOutcome.Refused -> return SourceListing.Refused("the catalogue signature is refused: ${outcome.reason}")
                    is SignatureOutcome.Verified -> Unit
                }
                FetchResult.NotFound -> if (!settings.allowUnsigned) {
                    return SourceListing.Refused("the catalogue has no signature")
                }
                is FetchResult.Unreachable -> return SourceListing.Unreachable(signature.reason)
            }
        }

        val parsed = PluginCatalogue.parse(bytes)
        if (!parsed.isOk) {
            return SourceListing.Refused(parsed.error)
        }
        return SourceListing.Listed(parsed.get())
    }

    private fun download(coordinate: PluginCoordinate, entry: PluginCatalogueEntry, source: String): StoreEntry {
        fun refused(reason: String) = StoreEntry(coordinate, StoreState.REFUSED, pluginId = entry.id, source = source, reason = reason)

        if (!entry.selectable) {
            return refused("the entry is yanked, so a new selection of it is refused")
        }
        CompatibilityRule.check(entry.compatibility, ingotVersion)?.let { return refused(it) }

        val jarUrl = "${source.trimEnd('/')}/${jarPath(coordinate)}"
        val jar = when (val fetched = transport.fetch(jarUrl, JAR_LIMIT)) {
            is FetchResult.Found -> fetched.bytes
            FetchResult.NotFound -> return refused("the source does not hold the jar that its catalogue lists")
            is FetchResult.Unreachable -> return StoreEntry(coordinate, StoreState.UNAVAILABLE, pluginId = entry.id, source = source, reason = fetched.reason)
        }

        if (!PluginDigest.matches(jar, entry.sha256)) {
            return refused("the digest of the jar differs from the catalogue, so the jar is refused")
        }

        val signature: ByteArray? = if (verifier == null) {
            null
        } else {
            when (val fetched = transport.fetch("$jarUrl.asc", SIGNATURE_LIMIT)) {
                is FetchResult.Found -> fetched.bytes
                FetchResult.NotFound -> null
                is FetchResult.Unreachable -> return StoreEntry(coordinate, StoreState.UNAVAILABLE, pluginId = entry.id, source = source, reason = fetched.reason)
            }
        }

        var fingerprint: String? = null
        if (signature != null && verifier != null) {
            when (val outcome = verifier.verify(jar, signature)) {
                is SignatureOutcome.Refused -> return refused("the signature of the jar is refused: ${outcome.reason}")
                is SignatureOutcome.Verified -> fingerprint = outcome.fingerprint
            }
        }
        if (signature == null && !settings.allowUnsigned) {
            return refused("the jar has no signature, so it is refused unless unsigned content is allowed")
        }

        cache.write(
            coordinate,
            jar,
            signature,
            CacheRecord(
                sha256 = entry.sha256,
                pluginId = entry.id,
                source = source,
                compatibility = entry.compatibility,
                signed = signature != null,
                fingerprint = fingerprint,
                verifiedAt = clock.instant(),
            ),
        )
        return StoreEntry(
            coordinate = coordinate,
            state = if (signature != null) StoreState.LOADED else StoreState.UNVERIFIED,
            pluginId = entry.id,
            jar = cache.jarFile(coordinate),
            source = source,
            fingerprint = fingerprint,
        )
    }

    private sealed interface SourceListing {
        data class Listed(val catalogue: PluginCatalogue) : SourceListing
        data class Refused(val reason: String) : SourceListing
        data class Unreachable(val reason: String) : SourceListing
    }

    companion object {

        /** The startup budget of the whole store, and the time the sources may take together before the server starts without them. */
        val DEFAULT_STARTUP_BUDGET: Duration = Duration.ofSeconds(60)

        const val CATALOGUE_LIMIT: Long = 5L * 1024 * 1024
        const val SIGNATURE_LIMIT: Long = 64L * 1024
        const val JAR_LIMIT: Long = 64L * 1024 * 1024

        /** The path of a jar below a source, in the standard Maven layout. */
        fun jarPath(coordinate: PluginCoordinate): String =
            "${coordinate.groupId.replace('.', '/')}/${coordinate.artifactId}/${coordinate.version}/${coordinate.artifactId}-${coordinate.version}.jar"

    }

}
