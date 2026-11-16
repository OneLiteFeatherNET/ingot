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

import com.google.auth.Credentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.NoCredentials
import com.google.cloud.http.HttpTransportOptions
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.time.Instant

/**
 * The client options of one repository. The transport is always HTTP, so the gRPC stack of the SDK is never used. The authentication
 * mode selects the credentials: `service-account-key` takes the key of the settings, and `adc` sets none, so the SDK reads the
 * environment (the metadata server, workload identity or GOOGLE_APPLICATION_CREDENTIALS). The key of `adc` is never read.
 */
internal fun storageOptionsOf(settings: GcsStorageProviderSettings): StorageOptions.Builder {
    val builder = StorageOptions.newBuilder()
        .setTransportOptions(HttpTransportOptions.newBuilder().build())
    if (settings.projectId.isNotBlank()) {
        builder.setProjectId(settings.projectId)
    }
    if (settings.endpoint.isNotBlank()) {
        builder.setHost(settings.endpoint)
    }
    credentialsOf(settings)?.let { builder.setCredentials(it) }
    return builder
}

/** The explicit credentials of one repository, or null when the SDK reads them from the environment (`adc`). */
internal fun credentialsOf(settings: GcsStorageProviderSettings): Credentials? =
    when {
        // The endpoint is the emulator of the container tests, which takes no credentials
        settings.endpoint.isNotBlank() -> NoCredentials.getInstance()
        settings.authentication == AUTHENTICATION_SERVICE_ACCOUNT_KEY ->
            ServiceAccountCredentials.fromStream(settings.credentialsJson.byteInputStream())
        else -> null
    }

/**
 * The adapter from the provider's object client to the Cloud Storage client of the SDK. A 404 of the service is a missing object,
 * and every other service answer becomes a [GcsServiceException] with its status, so no provider code sees an SDK type.
 */
internal class GcsSdkObjectClient(
    private val storage: Storage,
    private val bucket: String,
) : GcsObjectClient {

    override fun create(name: String, content: ByteArray, contentType: String) = translated {
        storage.create(BlobInfo.newBuilder(blobId(name)).setContentType(contentType).build(), content)
        Unit
    }

    override fun startUpload(name: String, contentType: String, chunkSize: Int): GcsUpload = translated {
        val channel = storage.writer(BlobInfo.newBuilder(blobId(name)).setContentType(contentType).build())
        channel.setChunkSize(chunkSize)
        object : GcsUpload {
            override fun write(chunk: ByteArray) = translated {
                val buffer = ByteBuffer.wrap(chunk)
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
            }

            /** Closing the channel of the SDK finishes the upload, so the object becomes visible only here. */
            override fun finish() = translated { channel.close() }
        }
    }

    override fun read(name: String, offset: Long, length: Long?): InputStream = translated {
        val channel = storage.reader(blobId(name))
        channel.seek(offset)
        if (length != null) {
            channel.limit(offset + length)
        }
        Channels.newInputStream(channel)
    }

    override fun metadata(name: String): GcsObjectInfo = translated {
        val blob = storage.get(blobId(name)) ?: throw GcsNotFoundException(name)
        blob.toInfo(name)
    }

    override fun list(prefix: String, startOffset: String?, limit: Int): List<GcsObjectInfo> = translated {
        val options = mutableListOf(Storage.BlobListOption.prefix(prefix), Storage.BlobListOption.pageSize(minOf(limit, MAX_PAGE).toLong()))
        startOffset?.let { options += Storage.BlobListOption.startOffset(it) }
        storage.list(bucket, *options.toTypedArray()).iterateAll().asSequence()
            .filter { !it.isDirectory }
            .take(limit)
            .map { it.toInfo(it.name) }
            .toList()
    }

    override fun listDelimited(prefix: String): List<GcsEntry> = translated {
        storage.list(
            bucket,
            Storage.BlobListOption.prefix(prefix),
            Storage.BlobListOption.currentDirectory(),
            Storage.BlobListOption.pageSize(MAX_PAGE.toLong()),
        ).iterateAll()
            .map { blob -> if (blob.isDirectory) GcsPrefix(blob.name) else blob.toInfo(blob.name) }
            .sortedBy { it.name }
    }

    override fun checkBucket() = translated {
        storage.get(bucket) ?: throw GcsNotFoundException(bucket)
        Unit
    }

    override fun delete(name: String) = translated {
        if (!storage.delete(blobId(name))) {
            throw GcsNotFoundException(name)
        }
    }

    override fun close() {
        storage.close()
    }

    private fun blobId(name: String): BlobId = BlobId.of(bucket, name)

    private fun Blob.toInfo(name: String): GcsObjectInfo =
        GcsObjectInfo(name = name, size = size ?: 0L, updated = updateTimeOffsetDateTime?.toInstant() ?: Instant.EPOCH)

    private companion object {
        /** The largest page that the service returns for one request. */
        const val MAX_PAGE = 1000
    }

}

/** Runs one request of the SDK and translates its failures into the exceptions of the object client. */
private fun <T> translated(action: () -> T): T =
    try {
        action()
    } catch (exception: StorageException) {
        if (exception.code == 404) {
            throw GcsNotFoundException("requested object")
        }
        throw GcsServiceException(exception.code, "Google Cloud Storage answered HTTP ${exception.code}")
    }
