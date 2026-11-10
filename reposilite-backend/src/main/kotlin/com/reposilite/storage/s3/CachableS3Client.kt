package com.reposilite.storage.s3

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.DelegatingS3Client
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.nio.file.Path
import java.time.Duration

/** The longest a cached HEAD answer may be used once a cluster bus is configured, counted from when it was stored. */
internal const val CLUSTER_HEAD_CACHE_MAX_AGE_SECONDS = 300L

/**
 * Whether an object key names metadata that another instance may overwrite at any time: a `maven-metadata` file with its checksums,
 * or any file inside a `-SNAPSHOT` directory. Such answers are never cached.
 */
internal fun isMutableS3Key(key: String): Boolean {
    val segments = key.split('/')
    return segments.last().startsWith("maven-metadata") || segments.dropLast(1).any { it.endsWith("-SNAPSHOT") }
}

/**
 * This S3 clients caches head object requests
 */
class CachableS3Client : DelegatingS3Client {
    private val delegate : S3Client
    private val settings: S3MetadataCacheSettings?
    private val ticker: Ticker

    /** Replaced once when a cluster bus binds, because the write expiry of a Caffeine cache cannot change in place. */
    @Volatile
    private var cache : Cache<String, HeadObjectResponse>

    /** Guarded by this instance, see [bindToClusterBus]. */
    private var clusterBound = false

    constructor(s3: S3Client, headObjectCache: Cache<String, HeadObjectResponse>) : super(s3) {
        this.delegate = s3
        this.settings = null
        this.ticker = Ticker.systemTicker()
        this.cache = headObjectCache
    }

    internal constructor(s3: S3Client, settings: S3MetadataCacheSettings, ticker: Ticker = Ticker.systemTicker()) : super(s3) {
        this.delegate = s3
        this.settings = settings
        this.ticker = ticker
        this.cache = newHeadObjectCache(settings, clusterBound = false, ticker = ticker)
    }

    override fun headObject(headObjectRequest: HeadObjectRequest): HeadObjectResponse {
        val key = headObjectRequest.key()
        if (isMutableS3Key(key)) {
            return delegate.headObject(headObjectRequest)
        }
        return cache.get(key, { _ -> delegate.headObject(headObjectRequest) })
    }

    override fun deleteObject(deleteObjectRequest: DeleteObjectRequest): DeleteObjectResponse? {
        val response = super.deleteObject(deleteObjectRequest)
        invalidate(deleteObjectRequest.key())
        return response
    }

    override fun putObject(putObjectRequest: PutObjectRequest, requestBody: RequestBody?): PutObjectResponse? {
        val response = super.putObject(putObjectRequest, requestBody)
        invalidate(putObjectRequest.key())
        return response
    }

    /** An object written by a multipart upload appears only on completion, so only the completion invalidates. */
    override fun completeMultipartUpload(completeMultipartUploadRequest: CompleteMultipartUploadRequest): CompleteMultipartUploadResponse {
        val response = super.completeMultipartUpload(completeMultipartUploadRequest)
        invalidate(completeMultipartUploadRequest.key())
        return response
    }

    /**
     * Called once a cluster bus is open. From now on an answer lives at most [CLUSTER_HEAD_CACHE_MAX_AGE_SECONDS] after it was stored,
     * and reads do not extend that. Answers stored before the bus opened are dropped, because they were stored without the bound.
     */
    @Synchronized
    internal fun bindToClusterBus() {
        val cacheSettings = checkNotNull(settings) { "A cache passed in as a Cache instance cannot be bound to the cluster" }
        if (clusterBound) {
            return
        }
        clusterBound = true
        cache = newHeadObjectCache(cacheSettings, clusterBound = true, ticker = ticker)
    }

    private fun invalidate(s3Key: String) {
        cache.invalidate(s3Key)
        // If a new object has been written, we also want to invalidate the cache for its maven-metadata.xml file.
        // We don't look for special suffix, we just go blindly one level up and invalidate the maven-metadata.xml file.
        // When in doubt, we invalidate the cache a bit too often, but it keeps the logic simple and more robust
        Path.of(s3Key).parent?.parent?.let { parentPath ->
            val metadataKey = parentPath.resolve("maven-metadata.xml").toString()
            cache.asMap().keys.removeIf { s -> s.startsWith(metadataKey) }
        }
    }
}

/**
 * The cache of head object answers. The configured creation expiry is capped at [CLUSTER_HEAD_CACHE_MAX_AGE_SECONDS] when [clusterBound].
 * A write expiry is measured from the store time and a read does not renew it, so the cap cannot be extended by reads.
 * The access expiry is unchanged.
 */
private fun newHeadObjectCache(
    settings: S3MetadataCacheSettings,
    clusterBound: Boolean,
    ticker: Ticker,
): Cache<String, HeadObjectResponse> {
    val creation = Duration.ofSeconds(settings.expireAfterCreationSeconds)
    val writeExpiry = when {
        clusterBound -> minOf(creation, Duration.ofSeconds(CLUSTER_HEAD_CACHE_MAX_AGE_SECONDS))
        else -> creation
    }
    return Caffeine.newBuilder()
        .maximumSize(settings.maximumSize)
        .ticker(ticker)
        .expireAfterWrite(writeExpiry)
        .expireAfterAccess(Duration.ofSeconds(settings.expireAfterAccessSeconds))
        .build()
}
