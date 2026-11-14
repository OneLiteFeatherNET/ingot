/*
 * Copyright (c) 2023 dzikoysk
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

@file:OptIn(net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi::class)

package com.reposilite.storage.s3

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Sensitive
import com.reposilite.storage.StorageProviderSettings
import io.javalin.openapi.Custom
import net.onelitefeather.ingot.storage.capability.SharedNamespaceAware
import net.onelitefeather.ingot.storage.capability.findSharedNamespaceConflicts
import net.onelitefeather.ingot.storage.capability.SupportDetails

@Doc(title = "S3 Metadata Cache", description = "Caches things like file size from S3 objects")
data class S3MetadataCacheSettings(
    @get:Doc(title = "Enabled", description = "Enable the cache?")
    val enabled: Boolean = false,
    @get:Doc(title = "Max Cache Entries", description = "Maximum number of cached entries")
    val maximumSize: Long = 10_000,
    @get:Doc(title = "Expire After Access", description = "Expire cache entries after the specified number of seconds since last read access")
    val expireAfterAccessSeconds: Long = 60 * 60 * 24, // 1 day
    @get:Doc(title = "Expire After Creation", description = "Expire cache entries after the specified number of seconds since the initial download")
    val expireAfterCreationSeconds: Long = 60 * 60 * 24, // 1 day
)


@Doc(title = "S3 Storage Provider", description = "Amazon S3 storage provider settings")
data class S3StorageProviderSettings(
    @get:Custom(name = "const", value = "s3")
    override val type: String = "s3",
    @get:Doc(title = "Bucket", description = "The selected AWS bucket")
    val bucketName: String = "",
    @get:Sensitive
    @get:Doc(title = "Endpoint", description = "Overwrite the AWS endpoint (optional)")
    val endpoint: String = "",
    @get:Sensitive
    @get:Doc(title = "Access Key", description = "Overwrite AWS access-key used to authenticate (optional)")
    val accessKey: String = "",
    @get:Sensitive
    @get:Doc(title = "Secret Key", description = "Overwrite AWS secret-key used to authenticate (optional)")
    val secretKey: String = "",
    @get:Doc(title = "Region", description = "Overwrite AWS region (optional)")
    val region: String = "",
    @get:Doc(title = "Key Prefix", description = "Optional prefix prepended to all object keys, e.g. to scope data within a bucket shared with other services")
    val prefix: String = "",
    @get:Doc(title = "Shared Bucket", description = "Namespace objects under the repository name so several repositories can share one bucket (optional)")
    val sharedBucket: Boolean = false,
    @get:Doc(title = "Local Metadata Cache", description = "Local metadata cache settings (optional). The default is no caching. NOTE: This cache is local only. If you run multiple instances, they will not share the cache!")
    val metadataCacheSettings: S3MetadataCacheSettings? = null,
) : StorageProviderSettings, SharedNamespaceAware, SupportDetails {

    /**
     * The namespace of this repository: the endpoint and the bucket, with the key prefix the repository resolves to. A blank bucket
     * has no namespace, so it never collides.
     */
    override fun namespaceKey(repositoryName: String): String? {
        if (bucketName.isBlank()) {
            return null
        }
        return listOf(endpoint.trim().trimEnd('/'), bucketName.trim(), resolveKeyPrefix(repositoryName)).joinToString(NAMESPACE_SEPARATOR)
    }

    /** Two namespaces collide when they are in the same bucket of the same endpoint, and one key prefix equals or nests under the other. */
    override fun overlaps(key: String, otherKey: String): Boolean {
        val (endpoint, bucket, prefix) = key.split(NAMESPACE_SEPARATOR, limit = 3)
        val (otherEndpoint, otherBucket, otherPrefix) = otherKey.split(NAMESPACE_SEPARATOR, limit = 3)
        return endpoint == otherEndpoint && bucket == otherBucket && (prefix.startsWith(otherPrefix) || otherPrefix.startsWith(prefix))
    }

    override fun overlapMessage(): String =
        "Its S3 key namespace overlaps another repository sharing the same bucket. Give each repository a distinct 'prefix', or enable 'sharedBucket' on every repository sharing the bucket."

    /** The region, when one is configured. */
    override fun describe(): Map<String, String> =
        region.takeIf { it.isNotBlank() }?.let { mapOf("region" to it) }.orEmpty()

}

/** Separates the parts of a namespace key. It cannot occur in an endpoint, a bucket name or a configured prefix. */
private const val NAMESPACE_SEPARATOR = "\u0000"

fun S3StorageProviderSettings.resolveKeyPrefix(repositoryName: String): String {
    val base = prefix.trim().trim('/').let { if (it.isEmpty()) "" else "$it/" }
    return when {
        sharedBucket -> base + repositoryName.trim().trim('/') + "/"
        else -> base
    }
}

/** The repositories whose S3 key namespaces collide, see [SharedNamespaceAware]. */
fun findS3SharedBucketConflicts(repositories: List<Pair<String, S3StorageProviderSettings>>): Set<String> =
    findSharedNamespaceConflicts(repositories).keys
