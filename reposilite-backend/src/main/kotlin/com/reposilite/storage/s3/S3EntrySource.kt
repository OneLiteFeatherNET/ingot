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


package com.reposilite.storage.s3

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.internalServerError
import com.reposilite.storage.StorageEntry
import com.reposilite.storage.StorageEntryPage
import com.reposilite.storage.StorageEntrySource
import com.reposilite.storage.api.Location
import java.time.Instant
import panda.std.Result
import panda.std.asSuccess
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request

/**
 * Flat listing of a bucket with one ListObjectsV2 request per page: no delimiter, so nested keys come back directly,
 * and StartAfter carries the cursor. Size and LastModified come with each key, so no HEAD request is needed.
 *
 * The listing is confined to [keyPrefix], which is the repository prefix of a shared bucket. The cursor and the
 * paths are relative to that prefix, and keys ending in a slash are folder objects and are skipped.
 *
 * @param keyPrefix prefix prepended to every object key of the storage
 */
class S3EntrySource(
    private val s3: S3Client,
    private val bucket: String,
    private val keyPrefix: String = "",
) : StorageEntrySource {

    override fun listEntries(prefix: Location, after: String?, limit: Int): Result<StorageEntryPage, ErrorResponse> {
        require(limit > 0) { "limit must be positive, was $limit" }

        return try {
            val directory = prefix.toString().let { if (it.isEmpty()) "" else "$it/" }
            val request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(keyPrefix + directory)
                .maxKeys(minOf(limit, MAX_KEYS_PER_PAGE))

            if (after != null) {
                request.startAfter(keyPrefix + after)
            }

            val response = s3.listObjectsV2(request.build())
            val keys = response.contents()

            val entries = keys
                .filterNot { it.key().endsWith("/") }
                .mapNotNull { key ->
                    Location.ofRequest(key.key().removePrefix(keyPrefix)).orNull()?.let { path ->
                        StorageEntry(
                            path = path,
                            size = key.size() ?: 0L,
                            lastModified = key.lastModified() ?: Instant.EPOCH,
                        )
                    }
                }

            val next = when {
                !response.isTruncated() -> null
                // A truncated page always has keys; without one the cursor could not advance.
                else -> keys.last().key().removePrefix(keyPrefix)
            }

            StorageEntryPage(entries = entries, next = next).asSuccess()
        } catch (exception: Exception) {
            internalServerError(exception.message ?: exception::class.toString())
        }
    }

    private companion object {
        /** The largest page S3 returns for one ListObjectsV2 request. */
        const val MAX_KEYS_PER_PAGE = 1000
    }

}
