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

import com.github.benmanes.caffeine.cache.Ticker
import com.reposilite.status.application.FailureComponents
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.storage.api.Location
import net.onelitefeather.ingot.storage.capability.ClusterBus
import net.onelitefeather.ingot.storage.capability.ClusterCacheAware
import net.onelitefeather.ingot.storage.capability.RelatedKeyAware
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response

/**
 * The S3 provider as a cluster-bound capability: the bind bounds the head cache by the maximum age it is given, and invalidation
 * drops the keys it names and the related keys of the format. Each test builds its own client, ticker and counters.
 */
internal class S3ClusterCapabilityTest {

    private val logger = InMemoryLogger()
    private val failureFacade = FailureComponents(journalist = logger).failureFacade()

    /** Counts the head requests that reach the bucket, so a test can tell a cached answer from a new request. */
    private class HeadCountingS3Client : S3Client {

        val heads = AtomicInteger()

        override fun serviceName(): String = S3Client.SERVICE_NAME

        override fun close() {}

        override fun headBucket(request: HeadBucketRequest): HeadBucketResponse =
            HeadBucketResponse.builder().build()

        override fun headObject(request: HeadObjectRequest): HeadObjectResponse {
            heads.incrementAndGet()
            return HeadObjectResponse.builder().contentLength(5L).lastModified(Instant.EPOCH).build()
        }

        override fun getObject(request: GetObjectRequest): ResponseInputStream<GetObjectResponse> =
            throw UnsupportedOperationException("not used by these tests")

        override fun putObject(request: PutObjectRequest, body: RequestBody): PutObjectResponse =
            throw UnsupportedOperationException("not used by these tests")

        override fun deleteObject(request: DeleteObjectRequest): DeleteObjectResponse =
            throw UnsupportedOperationException("not used by these tests")

        override fun listObjectsV2(request: ListObjectsV2Request): ListObjectsV2Response =
            ListObjectsV2Response.builder().isTruncated(false).build()

    }

    /** A clock the test moves by hand, so no test waits for time to pass. */
    private class ManualTicker : Ticker {

        var nanos = 0L

        override fun read(): Long = nanos

        fun advance(duration: Duration) {
            nanos += duration.toNanos()
        }

    }

    private object OpenBus : ClusterBus

    private fun providerWith(client: HeadCountingS3Client, ticker: Ticker): S3StorageProvider =
        S3StorageProvider(
            failureFacade = failureFacade,
            s3 = CachableS3Client(client, S3MetadataCacheSettings(enabled = true, expireAfterCreationSeconds = 3_600), ticker),
            bucket = "test",
        )

    @Test
    fun `a bound S3 provider expires an answer 300 seconds after it was stored`() {
        // given: an answer stored before the cluster bus opens, and the bus bound with the default maximum age of 300 seconds
        val client = HeadCountingS3Client()
        val ticker = ManualTicker()
        val provider = providerWith(client, ticker)
        provider.getFileDetails(Location.of("com/example/lib/1.0/lib-1.0.jar"))
        (provider as ClusterCacheAware).bind(OpenBus, Duration.ofSeconds(300))

        // when: the answer is read again, then read after 299 and 301 seconds
        provider.getFileDetails(Location.of("com/example/lib/1.0/lib-1.0.jar"))
        val storedAtBind = client.heads.get()
        ticker.advance(Duration.ofSeconds(299))
        provider.getFileDetails(Location.of("com/example/lib/1.0/lib-1.0.jar"))
        val beforeExpiry = client.heads.get()
        ticker.advance(Duration.ofSeconds(2))
        provider.getFileDetails(Location.of("com/example/lib/1.0/lib-1.0.jar"))

        // then: the answer stored after the bind is served until 300 seconds, and the request after that reaches the bucket
        assertThat(storedAtBind).`as`("heads after the bind, which stored a new answer").isEqualTo(2)
        assertThat(beforeExpiry).`as`("heads at 299 seconds, served from the cache").isEqualTo(2)
        assertThat(client.heads.get()).`as`("heads at 301 seconds, which expired the answer").isEqualTo(3)
    }

    @Test
    fun `invalidate drops the key it names from the head cache`() {
        // given: an answer in the cache
        val client = HeadCountingS3Client()
        val provider = providerWith(client, ManualTicker())
        provider.getFileDetails(Location.of("com/example/lib/1.0/lib-1.0.jar"))

        // when: another replica writes that key and the write is announced
        (provider as ClusterCacheAware).invalidate(listOf("com/example/lib/1.0/lib-1.0.jar"))
        provider.getFileDetails(Location.of("com/example/lib/1.0/lib-1.0.jar"))

        // then: the answer was read from the bucket again
        assertThat(client.heads.get()).`as`("heads after the invalidation").isEqualTo(2)
    }

    @Test
    fun `invalidate drops the related keys that the format names`() {
        // given: an answer for a key that the format names as related to every write below its directory
        val client = HeadCountingS3Client()
        val provider = providerWith(client, ManualTicker())
        (provider as RelatedKeyAware).useRelatedKeys { _ -> listOf("com/example/index.html") }
        provider.getFileDetails(Location.of("com/example/index.html"))

        // when: a different key in that directory is announced as written
        (provider as ClusterCacheAware).invalidate(listOf("com/example/lib/1.0/lib-1.0.jar"))
        provider.getFileDetails(Location.of("com/example/index.html"))

        // then: the related answer was read from the bucket again
        assertThat(client.heads.get()).`as`("heads of the related key after the invalidation").isEqualTo(2)
    }

}
