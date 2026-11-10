package com.reposilite.storage.s3

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class CachableS3ClientTest {
    private lateinit var client: CachableS3Client
    private lateinit var headRequests: AtomicInteger

    @BeforeEach
    fun setUp() {
        headRequests = AtomicInteger(0)

        val s3Client = object: S3Client {
            override fun deleteObject(request: DeleteObjectRequest): DeleteObjectResponse {
                return DeleteObjectResponse.builder().build()
            }

            override fun headObject(request: HeadObjectRequest): HeadObjectResponse {
                headRequests.incrementAndGet()
                return HeadObjectResponse.builder().build()
            }

            override fun putObject(request: PutObjectRequest, requestBody: RequestBody?): PutObjectResponse {
                return PutObjectResponse.builder().build()
            }

            override fun serviceName(): String? {
                return S3Client.SERVICE_NAME
            }

            override fun close() {

            }
        }
        val cache: Cache<String, HeadObjectResponse> = Caffeine.newBuilder().maximumSize(100).build()
        client = CachableS3Client(s3Client, cache)
    }

    @Test
    fun `should cache headObject requests`() {
        val key = "foo/bar.txt"
        val request = HeadObjectRequest.builder().key(key).build()

        client.headObject(request)

        assertThat(headRequests.get()).isEqualTo(1)
        client.headObject(request)
        assertThat(headRequests.get()).isEqualTo(1)
    }

    @Test
    fun `should invalidate cache on deleteObject`() {
        val key = "foo/bar.txt"

        client.headObject(HeadObjectRequest.builder().key(key).build())
        client.headObject(HeadObjectRequest.builder().key(key).build())
        assertThat(headRequests.get()).isEqualTo(1)

        client.deleteObject(DeleteObjectRequest.builder().key(key).build())
        client.headObject(HeadObjectRequest.builder().key(key).build())
        assertThat(headRequests.get()).isEqualTo(2)
    }

    @Test
    fun `should invalidate cache on putObject`() {
        val key = "foo/bar.txt"

        client.headObject(HeadObjectRequest.builder().key(key).build())
        client.headObject(HeadObjectRequest.builder().key(key).build())
        assertThat(headRequests.get()).isEqualTo(1)

        client.putObject(PutObjectRequest.builder().key(key).build(), RequestBody.empty())
        client.headObject(HeadObjectRequest.builder().key(key).build())
        assertThat(headRequests.get()).isEqualTo(2)

    }

    @Test
    fun `should invalidate maven-metadata xml on putObject`() {
        val key = "parent/foo/bar.txt"
        val mavenMetadata1 = "parent/maven-metadata.xml"
        val mavenMetadata2 = "parent/maven-metadata.xml.sha512"

        client.headObject(HeadObjectRequest.builder().key(mavenMetadata1).build())
        client.headObject(HeadObjectRequest.builder().key(mavenMetadata2).build())
        assertThat(headRequests.get()).isEqualTo(2)

        client.putObject(PutObjectRequest.builder().key(key).build(), RequestBody.empty())

        client.headObject(HeadObjectRequest.builder().key(mavenMetadata1).build())
        client.headObject(HeadObjectRequest.builder().key(mavenMetadata2).build())
        assertThat(headRequests.get()).isEqualTo(4)
    }

    @Test
    fun `should send every maven-metadata request to S3`() {
        val keys = listOf("parent/maven-metadata.xml", "parent/maven-metadata.xml.sha1", "parent/maven-metadata-local.xml")

        keys.forEach { key ->
            client.headObject(HeadObjectRequest.builder().key(key).build())
            client.headObject(HeadObjectRequest.builder().key(key).build())
        }

        assertThat(headRequests.get())
            .`as`("each maven-metadata request is answered by S3, none from the cache")
            .isEqualTo(6)
    }

    @Test
    fun `should send every snapshot artifact request to S3`() {
        val key = "group/artifact/1.0-SNAPSHOT/artifact-1.0-20260101.120000-1.jar"

        client.headObject(HeadObjectRequest.builder().key(key).build())
        client.headObject(HeadObjectRequest.builder().key(key).build())

        assertThat(headRequests.get())
            .`as`("a snapshot directory is mutable, so both requests reach S3")
            .isEqualTo(2)
    }

    @Test
    fun `should keep caching a release artifact`() {
        val key = "group/artifact/1.0/artifact-1.0.jar"

        client.headObject(HeadObjectRequest.builder().key(key).build())
        client.headObject(HeadObjectRequest.builder().key(key).build())

        assertThat(headRequests.get())
            .`as`("a release artifact is immutable, so the second request is answered from the cache")
            .isEqualTo(1)
    }

    @Test
    fun `should not use a cached answer older than 300 seconds once a cluster bus is bound`() {
        val ticker = ManualTicker()
        val requests = AtomicInteger(0)
        val client = clusterAwareClient(ticker, requests)
        client.bindToClusterBus()
        val request = HeadObjectRequest.builder().key("group/artifact/1.0/artifact-1.0.jar").build()

        client.headObject(request)
        ticker.advanceSeconds(301)
        client.headObject(request)

        assertThat(requests.get())
            .`as`("the answer stored at 0 must not be used at 301 seconds with a bus")
            .isEqualTo(2)
    }

    @Test
    fun `should not renew a cached answer on read once a cluster bus is bound`() {
        val ticker = ManualTicker()
        val requests = AtomicInteger(0)
        val client = clusterAwareClient(ticker, requests)
        client.bindToClusterBus()
        val request = HeadObjectRequest.builder().key("group/artifact/1.0/artifact-1.0.jar").build()

        client.headObject(request)
        ticker.advanceSeconds(200)
        client.headObject(request)
        assertThat(requests.get())
            .`as`("the read at 200 seconds is answered from the cache")
            .isEqualTo(1)

        ticker.advanceSeconds(101)
        client.headObject(request)

        assertThat(requests.get())
            .`as`("the read at 200 seconds must not extend the 300 second bound measured from the store time")
            .isEqualTo(2)
    }

    @Test
    fun `should keep a cached answer past 300 seconds without a cluster bus`() {
        val ticker = ManualTicker()
        val requests = AtomicInteger(0)
        val client = clusterAwareClient(ticker, requests)
        val request = HeadObjectRequest.builder().key("group/artifact/1.0/artifact-1.0.jar").build()

        client.headObject(request)
        ticker.advanceSeconds(301)
        client.headObject(request)

        assertThat(requests.get())
            .`as`("without a bus the configured one day expiry applies, so the answer is still used")
            .isEqualTo(1)
    }

    @Test
    fun `should invalidate the cached answer of the key on completeMultipartUpload`() {
        val fixture = MultipartCacheFixture()
        val key = "com/example/a/1.0/a-1.0.jar"

        fixture.client.headObject(HeadObjectRequest.builder().key(key).build())
        fixture.client.headObject(HeadObjectRequest.builder().key(key).build())
        assertThat(fixture.headRequests.get()).isEqualTo(1)

        fixture.client.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket("test").key(key).uploadId("upload-1").build())
        fixture.client.headObject(HeadObjectRequest.builder().key(key).build())

        assertThat(fixture.headRequests.get())
            .`as`("the answer cached before the multipart upload is replaced by a request to S3")
            .isEqualTo(2)
    }

    @Test
    fun `should invalidate the cached maven-metadata xml of the parent on completeMultipartUpload`() {
        val fixture = MultipartCacheFixture()
        val mavenMetadata = "parent/maven-metadata.xml"
        fixture.cache.put(mavenMetadata, HeadObjectResponse.builder().build())

        fixture.client.completeMultipartUpload(
            CompleteMultipartUploadRequest.builder().bucket("test").key("parent/foo/bar.txt").uploadId("upload-1").build()
        )

        assertThat(fixture.cache.getIfPresent(mavenMetadata))
            .`as`("the cached maven-metadata.xml of the parent directory is removed, as after a single put")
            .isNull()
    }

    @Test
    fun `should keep the cached answer while a multipart upload is created and its parts are sent`() {
        val fixture = MultipartCacheFixture()
        val key = "com/example/a/1.0/a-1.0.jar"

        fixture.client.headObject(HeadObjectRequest.builder().key(key).build())
        fixture.client.createMultipartUpload(CreateMultipartUploadRequest.builder().bucket("test").key(key).build())
        fixture.client.uploadPart(
            UploadPartRequest.builder().bucket("test").key(key).uploadId("upload-1").partNumber(1).build(),
            RequestBody.fromBytes(ByteArray(1))
        )
        fixture.client.headObject(HeadObjectRequest.builder().key(key).build())

        assertThat(fixture.headRequests.get())
            .`as`("creating the upload and sending its parts do not change the key, so the cached answer is kept")
            .isEqualTo(1)
        assertThat(fixture.uploadCalls)
            .`as`("the multipart calls reached the wrapped client")
            .containsExactly("create", "part")
    }

    /** A client whose cache is visible to the test, with a wrapped S3 that counts HEAD requests and records the multipart calls. */
    private class MultipartCacheFixture {
        val cache: Cache<String, HeadObjectResponse> = Caffeine.newBuilder().maximumSize(100).build()
        val headRequests = AtomicInteger(0)
        val uploadCalls = mutableListOf<String>()

        val client = CachableS3Client(
            object : S3Client {
                override fun headObject(request: HeadObjectRequest): HeadObjectResponse {
                    headRequests.incrementAndGet()
                    return HeadObjectResponse.builder().build()
                }

                override fun createMultipartUpload(request: CreateMultipartUploadRequest): CreateMultipartUploadResponse {
                    uploadCalls += "create"
                    return CreateMultipartUploadResponse.builder().uploadId("upload-1").build()
                }

                override fun uploadPart(request: UploadPartRequest, requestBody: RequestBody?): UploadPartResponse {
                    uploadCalls += "part"
                    return UploadPartResponse.builder().eTag("etag-1").build()
                }

                override fun completeMultipartUpload(request: CompleteMultipartUploadRequest): CompleteMultipartUploadResponse {
                    uploadCalls += "complete"
                    return CompleteMultipartUploadResponse.builder().build()
                }

                override fun serviceName(): String = S3Client.SERVICE_NAME

                override fun close() {}
            },
            cache,
        )
    }

    private class ManualTicker : Ticker {
        private var nanos: Long = 0L

        override fun read(): Long = nanos

        fun advanceSeconds(seconds: Long) {
            nanos += Duration.ofSeconds(seconds).toNanos()
        }
    }

    private fun clusterAwareClient(ticker: ManualTicker, requests: AtomicInteger): CachableS3Client {
        val s3Client = object : S3Client {
            override fun deleteObject(request: DeleteObjectRequest): DeleteObjectResponse =
                DeleteObjectResponse.builder().build()

            override fun headObject(request: HeadObjectRequest): HeadObjectResponse {
                requests.incrementAndGet()
                return HeadObjectResponse.builder().build()
            }

            override fun putObject(request: PutObjectRequest, requestBody: RequestBody?): PutObjectResponse =
                PutObjectResponse.builder().build()

            override fun serviceName(): String = S3Client.SERVICE_NAME

            override fun close() {}
        }
        val settings = S3MetadataCacheSettings(
            enabled = true,
            maximumSize = 100,
            expireAfterAccessSeconds = 60 * 60 * 24,
            expireAfterCreationSeconds = 60 * 60 * 24,
        )
        return CachableS3Client(s3Client, settings, ticker)
    }
}

