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

/**
 * The operations of the Blob service that the provider needs, with the names of blobs already resolved. The provider talks to this
 * interface only, so its tests run against a fake, and the Azure SDK is confined to one implementation of it.
 */
internal interface AzureBlobClient : AutoCloseable {

    /** Writes [content] as one committed block blob (Put Blob), replacing a blob of the same name. */
    fun putBlob(name: String, content: ByteArray, contentType: String)

    /** Stages one uncommitted block of the blob [name] under [blockId]. A staged block is invisible to readers until it is committed. */
    fun stageBlock(name: String, blockId: String, content: ByteArray)

    /** Commits the staged blocks [blockIds], in this order, as the content of the blob [name] (Put Block List). */
    fun commitBlockList(name: String, blockIds: List<String>, contentType: String)

    override fun close() {}

}
