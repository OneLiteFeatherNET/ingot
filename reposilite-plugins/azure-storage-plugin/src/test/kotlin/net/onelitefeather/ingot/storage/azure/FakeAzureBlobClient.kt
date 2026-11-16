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

package net.onelitefeather.ingot.storage.azure

import java.io.InputStream
import java.time.Instant

/**
 * An in-memory blob service. Committed blobs are visible, staged blocks are not, as in Azure. A failure can be armed for the next
 * stage, which is how a failed upload is produced without a network.
 */
internal class FakeAzureBlobClient : AzureBlobClient {

    val committed = mutableMapOf<String, ByteArray>()
    val contentTypes = mutableMapOf<String, String>()
    val putNames = mutableListOf<String>()
    val commitCalls = mutableListOf<Pair<String, List<String>>>()
    val stagedBlocks = mutableMapOf<String, MutableMap<String, ByteArray>>()

    /** Called before each stage, with the blob name and the block id. */
    var beforeStage: (String, String) -> Unit = { _, _ -> }

    var stageFailure: RuntimeException? = null

    override fun putBlob(name: String, content: ByteArray, contentType: String) {
        putNames += name
        committed[name] = content
        contentTypes[name] = contentType
    }

    override fun stageBlock(name: String, blockId: String, content: ByteArray) {
        beforeStage(name, blockId)
        stageFailure?.let { throw it }
        stagedBlocks.getOrPut(name) { mutableMapOf() }[blockId] = content
    }

    override fun commitBlockList(name: String, blockIds: List<String>, contentType: String) {
        commitCalls += name to blockIds
        val blocks = stagedBlocks[name].orEmpty()
        committed[name] = blockIds.fold(ByteArray(0)) { acc, id -> acc + blocks.getValue(id) }
        contentTypes[name] = contentType
    }

    /** Every download request, as name, offset and count. A download is a read of content. */
    val downloads = mutableListOf<Triple<String, Long, Long?>>()

    /** When set, every range is refused as the service refuses a range that starts past the end (416), whatever the size. */
    var refuseEveryRange = false

    /** When set, every call fails with this exception, as a service failure does. */
    var forcedFailure: RuntimeException? = null

    override fun properties(name: String): AzureBlobProperties =
        forcedFailure?.let { throw it } ?: committed[name]
            ?.let { AzureBlobProperties(contentLength = it.size.toLong(), lastModified = modifiedAt) }
            ?: throw AzureBlobNotFoundException(name)

    override fun download(name: String, offset: Long, count: Long?): InputStream {
        downloads += Triple(name, offset, count)
        forcedFailure?.let { throw it }
        val content = committed[name] ?: throw AzureBlobNotFoundException(name)
        if (refuseEveryRange) throw AzureRangeNotSatisfiableException(name)
        val end = count?.let { minOf(content.size.toLong(), offset + it) } ?: content.size.toLong()
        return content.copyOfRange(offset.toInt(), end.toInt()).inputStream()
    }

    /** Every listing request, as prefix and maximum number of results. */
    val listCalls = mutableListOf<Pair<String, Int?>>()

    /**
     * A listing by hierarchy with the delimiter `/`: a name under the prefix that has a further `/` is a directory, the prefix itself
     * is a blob (a folder marker when it ends with `/`), and any other name is a blob.
     */
    override fun listChildren(prefix: String, maxResults: Int?): List<AzureBlobChild> {
        listCalls += prefix to maxResults
        forcedFailure?.let { throw it }
        val children = linkedMapOf<String, AzureBlobChild>()
        committed.keys.filter { it.startsWith(prefix) }.sorted().forEach { key ->
            val rest = key.removePrefix(prefix)
            val child = if (rest.contains('/') && rest.isNotEmpty()) {
                AzureBlobChild(prefix + rest.substringBefore('/') + "/", null)
            } else {
                AzureBlobChild(key, properties(key))
            }
            children.putIfAbsent(child.name, child)
        }
        val entries = children.values.toList()
        return maxResults?.let { entries.take(it) } ?: entries
    }

    /** Every delete request, including the ones that fail. */
    val deletes = mutableListOf<String>()

    /** Names whose next delete fails with a network error and is then deleted normally, as a retry sees it. */
    val failingDeletes = mutableSetOf<String>()

    override fun delete(name: String) {
        deletes += name
        if (failingDeletes.remove(name)) throw IllegalStateException("network lost")
        committed.remove(name) ?: throw AzureBlobNotFoundException(name)
    }

    private companion object {
        val modifiedAt: Instant = Instant.parse("2026-01-02T03:04:05Z")
    }

}
