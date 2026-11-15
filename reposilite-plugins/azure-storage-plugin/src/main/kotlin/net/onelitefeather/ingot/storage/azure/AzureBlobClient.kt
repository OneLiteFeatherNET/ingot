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
 * The operations of the Blob service that the provider needs, with the names of blobs already resolved. The provider talks to this
 * interface only, so its tests run against a fake, and the Azure SDK is confined to one implementation of it.
 */
interface AzureBlobClient : AutoCloseable {

    /** Writes [content] as one committed block blob (Put Blob), replacing a blob of the same name. */
    fun putBlob(name: String, content: ByteArray, contentType: String)

    /** Stages one uncommitted block of the blob [name] under [blockId]. A staged block is invisible to readers until it is committed. */
    fun stageBlock(name: String, blockId: String, content: ByteArray)

    /** Commits the staged blocks [blockIds], in this order, as the content of the blob [name] (Put Block List). */
    fun commitBlockList(name: String, blockIds: List<String>, contentType: String)

    /** The size and the last modification of the committed blob [name]. Throws [AzureBlobNotFoundException] when it is absent. */
    fun properties(name: String): AzureBlobProperties

    /**
     * The content of the blob [name] from [offset], at most [count] bytes or to the end when [count] is null (ranged Get Blob).
     * Throws [AzureBlobNotFoundException] when the blob is absent, and [AzureRangeNotSatisfiableException] when the service refuses the range.
     */
    fun download(name: String, offset: Long, count: Long?): InputStream

    /**
     * The entries directly under the prefix [prefix] (a listing by hierarchy with the delimiter `/`). At most [maxResults] entries are
     * returned when it is set. A directory is an entry without properties, and its name ends with `/`.
     */
    fun listChildren(prefix: String, maxResults: Int?): List<AzureBlobChild>

    /** Reads the properties of the container. Throws [AzureBlobNotFoundException] when the container is absent. */
    fun checkContainer()

    /** Deletes the blob [name], with its snapshots. Throws [AzureBlobNotFoundException] when it is absent. */
    fun delete(name: String)

    override fun close() {}

}

/** The committed state of a blob that the provider reports. */
data class AzureBlobProperties(val contentLength: Long, val lastModified: Instant)

/** An entry of a listing by hierarchy: a blob with its properties, or a directory with none. The [name] is the full name in the container. */
data class AzureBlobChild(val name: String, val properties: AzureBlobProperties?) {

    val isDirectory: Boolean
        get() = properties == null

}

/** The blob, or its container, does not exist (404). */
class AzureBlobNotFoundException(name: String) : RuntimeException("Azure blob '$name' does not exist")

/**
 * The service answered with a failure that is neither a missing blob nor a refused range. Only the status and the error code of the
 * answer are kept, never the request, so a SAS token or an account key cannot reach a log through this exception.
 */
class AzureServiceException(name: String, val statusCode: Int, val errorCode: String?) :
    RuntimeException("Azure blob '$name' answered HTTP $statusCode" + (errorCode?.let { " ($it)" } ?: ""))

/** The service refused a range that starts at or after the end of the blob (416). */
class AzureRangeNotSatisfiableException(name: String) : RuntimeException("Azure blob '$name' refused the requested range")
