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

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/** What the cache remembers about one verified jar. The record is written last, so a jar without a record is never read. */
data class CacheRecord(
    val sha256: String,
    val pluginId: String,
    val source: String,
    val compatibility: Compatibility?,
    val signed: Boolean,
    val fingerprint: String?,
    val verifiedAt: Instant,
)

/** A jar and its signature as the cache holds them. [signature] is null for an unsigned jar. */
class CachedPlugin(
    val jar: Path,
    val signature: ByteArray?,
    val record: CacheRecord,
)

/**
 * The cache of verified store jars, under `<data>/plugin-cache` and never in the plugins directory. Each file is written to a
 * temporary name beside its target and moved into place, so a crash never leaves a half-written jar that looks verified.
 */
class PluginCache(private val root: Path) {

    fun jarFile(coordinate: PluginCoordinate): Path =
        directory(coordinate).resolve("${coordinate.artifactId}-${coordinate.version}.jar")

    fun write(coordinate: PluginCoordinate, jar: ByteArray, signature: ByteArray?, record: CacheRecord) {
        val directory = directory(coordinate).createDirectories()
        val jarFile = jarFile(coordinate)
        writeAtomically(jarFile, jar)

        val signatureFile = jarFile.resolveSibling(jarFile.name + ".asc")
        if (signature != null) {
            writeAtomically(signatureFile, signature)
        } else {
            Files.deleteIfExists(signatureFile)
        }

        writeAtomically(directory.resolve(RECORD_FILE), encode(record))
    }

    /** The cached plugin, or null when no record exists or the record cannot be read. Nothing is created on read. */
    fun read(coordinate: PluginCoordinate): CachedPlugin? {
        val directory = directory(coordinate)
        val recordFile = directory.resolve(RECORD_FILE)
        if (!recordFile.isRegularFile()) {
            return null
        }
        val record = readRecord(recordFile) ?: return null

        val jar = jarFile(coordinate)
        if (!jar.isRegularFile()) {
            return null
        }
        val signatureFile = jar.resolveSibling(jar.name + ".asc")
        val signature = if (signatureFile.isRegularFile()) Files.readAllBytes(signatureFile) else null
        return CachedPlugin(jar, signature, record)
    }

    /** A record that does not parse is treated as absent, so the entry is downloaded again instead of trusted. */
    private fun readRecord(file: Path): CacheRecord? =
        try {
            decode(Files.readAllBytes(file))
        } catch (exception: Exception) {
            null
        }

    private fun directory(coordinate: PluginCoordinate): Path {
        val groupPath = coordinate.groupId.split(".").fold(root) { path, part -> path.resolve(part) }
        return groupPath.resolve(coordinate.artifactId).resolve(coordinate.version)
    }

    private fun writeAtomically(target: Path, bytes: ByteArray) {
        val temporary = Files.createTempFile(target.parent, target.name, ".tmp")
        try {
            Files.write(temporary, bytes)
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun encode(record: CacheRecord): ByteArray {
        val node = mapper.createObjectNode()
        node.put("sha256", record.sha256)
        node.put("pluginId", record.pluginId)
        node.put("source", record.source)
        node.put("signed", record.signed)
        record.fingerprint?.let { node.put("fingerprint", it) }
        record.compatibility?.let { compatibility ->
            node.putObject("compatibility").apply {
                put("ingotMin", compatibility.ingotMin)
                put("ingotBelow", compatibility.ingotBelow)
                put("pluginApi", compatibility.pluginApi)
            }
        }
        node.put("verifiedAt", record.verifiedAt.epochSecond)
        return mapper.writeValueAsBytes(node)
    }

    private fun decode(bytes: ByteArray): CacheRecord? {
        val node = mapper.readTree(bytes) ?: return null
        val compatibility = node.get("compatibility")?.takeIf { it.isObject }?.let {
            Compatibility(
                ingotMin = it.get("ingotMin").asText(),
                ingotBelow = it.get("ingotBelow").asText(),
                pluginApi = it.get("pluginApi").asInt(),
            )
        }
        return CacheRecord(
            sha256 = node.get("sha256").asText(),
            pluginId = node.get("pluginId").asText(),
            source = node.get("source").asText(),
            compatibility = compatibility,
            signed = node.get("signed").asBoolean(),
            fingerprint = node.get("fingerprint")?.takeIf { !it.isNull }?.asText(),
            verifiedAt = Instant.ofEpochSecond(node.get("verifiedAt").asLong()),
        )
    }

    private companion object {
        const val RECORD_FILE = "record.json"
        val mapper = ObjectMapper()
    }

}
