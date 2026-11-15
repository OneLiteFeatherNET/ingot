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

}
