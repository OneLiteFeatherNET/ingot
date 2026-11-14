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

import com.reposilite.VERSION
import com.reposilite.journalist.Journalist
import org.bouncycastle.openpgp.PGPPublicKeyRing
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * Builds the store of one start from the configuration. It returns null when nothing is selected or the recovery switch is on,
 * so an installation without store settings gets no store, no request and no cache directory. A problem in the settings or in
 * the trusted keys is logged and does not stop the start.
 */
object PluginStoreFactory {

    fun create(
        environment: Map<String, String>,
        localSelection: String,
        dataDirectory: Path,
        journalist: Journalist,
        clock: Clock = Clock.systemUTC(),
    ): PluginStore? {
        val settings = PluginStoreSettings.read(environment, localSelection)
        settings.problems.forEach { journalist.logger.error("Plugin store: $it") }

        if (settings.disabled) {
            journalist.logger.warn("Plugin store: INGOT_PLUGIN_STORE_DISABLED is true, the store is skipped for this start")
            return null
        }
        if (!settings.isActive) {
            return null
        }

        val keyRings = settings.trustedKeysLocation?.let { readTrustedKeys(it, journalist) }
        if (keyRings == null) {
            journalist.logger.warn("Plugin store: no trusted key is configured, so sources are used only with INGOT_PLUGIN_ALLOW_UNSIGNED=true")
        }
        val verifier = keyRings?.let { PluginSignatureVerifier(it, clock) }

        return PluginStore(
            settings = settings,
            ingotVersion = VERSION,
            cache = PluginCache(dataDirectory.resolve(CACHE_DIRECTORY)),
            transport = HttpPluginSourceTransport(),
            verifier = verifier,
            clock = clock,
        )
    }

    /** Reads the key rings of one file, or of every file of a directory. Null when the keys cannot be read. */
    private fun readTrustedKeys(location: Path, journalist: Journalist): List<PGPPublicKeyRing>? =
        try {
            val files = if (location.isDirectory()) {
                location.listDirectoryEntries().filter { it.isRegularFile() }.sortedBy { it.fileName.toString() }
            } else {
                listOf(location)
            }
            files.flatMap { PluginSignatureVerifier.readKeyRings(Files.readAllBytes(it)) }
                .ifEmpty { throw IllegalStateException("no OpenPGP public key was found") }
        } catch (exception: Exception) {
            journalist.logger.error("Plugin store: INGOT_PLUGIN_TRUSTED_KEYS cannot be read, so no trusted key is loaded: ${exception.message}")
            null
        }

    private const val CACHE_DIRECTORY: String = "plugin-cache"

}
