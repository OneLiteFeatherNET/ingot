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
 * The keys that a write makes stale in the head cache, besides the written key. Every returned key is a prefix: each cached entry
 * whose key starts with it is dropped. A format that stores in an S3 storage names its own keys this way.
 */
typealias RelatedKeyRule = (key: String) -> Collection<String>

/**
 * The rule of earlier versions, which applies when no rule is given: the `maven-metadata.xml` two levels above the written key is
 * dropped, because a new version may have changed the metadata of its artifact.
 */
val defaultRelatedKeys: RelatedKeyRule = { key ->
    Path.of(key).parent?.parent?.resolve("maven-metadata.xml")?.toString()?.let { listOf(it) }.orEmpty()
}

/** A storage whose head cache takes the related keys of the format that stores in it, see [RelatedKeyRule]. */
interface RelatedKeyAware {

    fun useRelatedKeys(rule: RelatedKeyRule)

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

    /** The keys a write makes stale, see [RelatedKeyRule]. Replaced by the format of the repository that stores in this client. */
    @Volatile
    private var relatedKeys: RelatedKeyRule = defaultRelatedKeys

    /** Guarded by this instance, see [bindToClusterBus]. */
    private var clusterBound = false

    constructor(s3: S3Client, headObjectCache: Cache<String, HeadObjectResponse>) : this(s3, headObjectCache, defaultRelatedKeys)

    constructor(s3: S3Client, headObjectCache: Cache<String, HeadObjectResponse>, relatedKeys: RelatedKeyRule) : super(s3) {
        this.delegate = s3
        this.settings = null
        this.ticker = Ticker.systemTicker()
        this.cache = headObjectCache
        this.relatedKeys = relatedKeys
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

    /** Replaces the rule of the keys a write makes stale. A format calls it once, when its repository is built. */
    internal fun useRelatedKeys(rule: RelatedKeyRule) {
        relatedKeys = rule
    }

    private fun invalidate(s3Key: String) {
        cache.invalidate(s3Key)
        // The related keys of the format are dropped as well. When in doubt, the cache is invalidated a bit too often.
        val rule = relatedKeys
        rule(s3Key).forEach { prefix ->
            cache.asMap().keys.removeIf { s -> s.startsWith(prefix) }
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
