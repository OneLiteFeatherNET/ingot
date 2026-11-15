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

import java.io.InputStream
import java.time.Instant

/**
 * The part of the Google Cloud Storage client that the provider uses. The provider talks to this interface only, so its rules are
 * tested with an in-memory fake, and the SDK is reached through one adapter. Names are full object names, with the prefix of the
 * repository already applied.
 */
internal interface GcsObjectClient {

    /** Writes [content] as one object in one request, with [contentType]. */
    fun create(name: String, content: ByteArray, contentType: String)

    /**
     * Opens a resumable upload of chunks of [chunkSize] bytes. The object becomes visible only when [GcsUpload.finish] is called.
     * An upload that is never finished creates no object.
     */
    fun startUpload(name: String, contentType: String, chunkSize: Int): GcsUpload

    /** Reads [length] bytes from [offset] of an object, or the rest of the object when [length] is null. Only those bytes are transferred. */
    fun read(name: String, offset: Long, length: Long?): InputStream

    /** The metadata of an object, or [GcsNotFoundException] when the object does not exist. */
    fun metadata(name: String): GcsObjectInfo

}

/** The metadata of one object as the service reports it. */
internal data class GcsObjectInfo(val name: String, val size: Long, val updated: Instant)

/** The service has no object of this name. */
internal class GcsNotFoundException(name: String) : RuntimeException("Object not found: $name")

/** A resumable upload of one object. */
internal interface GcsUpload {

    /** Sends the next chunk. The chunk is not visible to readers until [finish]. */
    fun write(chunk: ByteArray)

    /** Makes the object visible, with all the chunks written so far. */
    fun finish()

}
