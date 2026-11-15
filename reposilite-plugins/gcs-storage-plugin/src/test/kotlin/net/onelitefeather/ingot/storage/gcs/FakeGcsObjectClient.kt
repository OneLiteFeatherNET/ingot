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

package net.onelitefeather.ingot.storage.gcs

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant

/**
 * An in-memory object service. An object becomes visible when its upload is finished, as in Google Cloud Storage. Every request is
 * recorded, so a test can assert how the provider used the service and not only what it stored.
 */
internal class FakeGcsObjectClient : GcsObjectClient {

    val objects = mutableMapOf<String, ByteArray>()
    val contentTypes = mutableMapOf<String, String>()

    /** The names of the single-request writes, in order. */
    val creates = mutableListOf<String>()

    /** The chunk size of every resumable upload that was opened. */
    val openedUploads = mutableListOf<Int>()

    /** The size of every write of every resumable upload, in order. */
    val uploadWrites = mutableListOf<List<Int>>()

    override fun create(name: String, content: ByteArray, contentType: String) {
        creates += name
        objects[name] = content
        contentTypes[name] = contentType
    }

    /** Every ranged read as name, offset and length, where a null length reads to the end. */
    val reads = mutableListOf<Triple<String, Long, Long?>>()

    override fun read(name: String, offset: Long, length: Long?): InputStream {
        reads += Triple(name, offset, length)
        val content = objects[name] ?: throw GcsNotFoundException(name)
        val end = length?.let { minOf(content.size.toLong(), offset + it) } ?: content.size.toLong()
        return content.copyOfRange(offset.toInt(), end.toInt()).inputStream()
    }

    override fun metadata(name: String): GcsObjectInfo {
        val content = objects[name] ?: throw GcsNotFoundException(name)
        return GcsObjectInfo(name = name, size = content.size.toLong(), updated = modifiedAt)
    }

    /** Every flat listing as prefix, start offset and limit. */
    val listings = mutableListOf<Triple<String, String?, Int>>()

    /** Objects under [prefix] whose names are at or after [startOffset], in name order. */
    override fun list(prefix: String, startOffset: String?, limit: Int): List<GcsObjectInfo> {
        listings += Triple(prefix, startOffset, limit)
        return objects.keys.filter { it.startsWith(prefix) && (startOffset == null || it >= startOffset) }
            .sorted()
            .take(limit)
            .map { metadata(it) }
    }

    /** A delimited listing: objects directly under [prefix], and one directory entry per deeper name. */
    override fun listDelimited(prefix: String): List<GcsEntry> {
        val entries = linkedMapOf<String, GcsEntry>()
        objects.keys.filter { it.startsWith(prefix) }.sorted().forEach { name ->
            val rest = name.removePrefix(prefix)
            val entry = if (rest.contains('/')) GcsPrefix(prefix + rest.substringBefore('/') + "/") else metadata(name)
            entries.putIfAbsent(entry.name, entry)
        }
        return entries.values.toList()
    }

    override fun startUpload(name: String, contentType: String, chunkSize: Int): GcsUpload {
        openedUploads += chunkSize
        val writes = mutableListOf<Int>()
        uploadWrites += writes
        val content = ByteArrayOutputStream()
        return object : GcsUpload {
            override fun write(chunk: ByteArray) {
                writes += chunk.size
                content.write(chunk)
            }

            override fun finish() {
                objects[name] = content.toByteArray()
                contentTypes[name] = contentType
            }
        }
    }

    companion object {
        val modifiedAt: Instant = Instant.parse("2026-01-02T03:04:05Z")
    }

}
