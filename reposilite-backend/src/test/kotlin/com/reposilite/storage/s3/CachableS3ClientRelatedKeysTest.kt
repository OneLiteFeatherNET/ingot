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

import com.github.benmanes.caffeine.cache.Caffeine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse

/**
 * The head cache drops the entries that the related-key rule names after a write. The cache is in memory and the S3 client is a
 * stub, so no bucket is needed. Each test fills its own cache and reads back the keys that are left.
 */
internal class CachableS3ClientRelatedKeysTest {

    private val s3 = object : S3Client {
        override fun deleteObject(request: DeleteObjectRequest): DeleteObjectResponse =
            DeleteObjectResponse.builder().build()

        override fun headObject(request: HeadObjectRequest): HeadObjectResponse =
            HeadObjectResponse.builder().build()

        override fun putObject(request: PutObjectRequest, requestBody: RequestBody?): PutObjectResponse =
            PutObjectResponse.builder().build()

        override fun serviceName(): String = S3Client.SERVICE_NAME

        override fun close() = Unit
    }

    private fun newCache() = Caffeine.newBuilder().maximumSize(100).build<String, HeadObjectResponse>()

    @Test
    fun `a related-key rule drops exactly the keys it names after a put`() {
        // given: cached answers for the written key's neighbours and for two keys the rule names
        val cache = newCache()
        cache.put("keep/x.txt", HeadObjectResponse.builder().build())
        cache.put("drop/one.txt", HeadObjectResponse.builder().build())
        cache.put("drop/two.txt", HeadObjectResponse.builder().build())
        val client = CachableS3Client(s3, cache, relatedKeys = { _ -> listOf("drop/one.txt") })

        // when: an object is written
        client.putObject(PutObjectRequest.builder().key("written/a.jar").build(), RequestBody.empty())

        // then: only the named key is gone, and the written key was not cached before the write
        assertThat(cache.asMap().keys)
            .describedAs("the named key must be dropped and every other entry must stay")
            .containsExactlyInAnyOrder("keep/x.txt", "drop/two.txt")
    }

    @Test
    fun `without a related-key rule the maven-metadata entries two levels up are dropped after a put`() {
        // given: a metadata answer two levels above the written key, and a cached answer that is not related to it
        val cache = newCache()
        cache.put("com/example/maven-metadata.xml", HeadObjectResponse.builder().build())
        cache.put("com/other/maven-metadata.xml", HeadObjectResponse.builder().build())
        cache.put("com/example/1.0/other.txt", HeadObjectResponse.builder().build())
        val client = CachableS3Client(s3, cache)

        // when: an artifact of that version is written
        client.putObject(PutObjectRequest.builder().key("com/example/1.0/a.jar").build(), RequestBody.empty())

        // then: the metadata entry of com/example is dropped, as it always was, and the unrelated entries stay
        assertThat(cache.asMap().keys)
            .describedAs("the default rule drops maven-metadata.xml two levels up and nothing else")
            .containsExactlyInAnyOrder("com/other/maven-metadata.xml", "com/example/1.0/other.txt")
    }

    @Test
    fun `a related-key rule that names nothing drops only the written key after a put`() {
        // given: a metadata answer two levels above the written key and a cached answer in the same version
        val cache = newCache()
        cache.put("com/example/maven-metadata.xml", HeadObjectResponse.builder().build())
        cache.put("com/example/1.0/other.txt", HeadObjectResponse.builder().build())
        val client = CachableS3Client(s3, cache, relatedKeys = { _ -> emptyList() })

        // when: an artifact of that version is written
        client.putObject(PutObjectRequest.builder().key("com/example/1.0/a.jar").build(), RequestBody.empty())

        // then: no other entry is dropped, so the default rule does not apply once a format supplies its own
        assertThat(cache.asMap().keys)
            .describedAs("a format that names no related keys must not lose the metadata answer")
            .containsExactlyInAnyOrder("com/example/maven-metadata.xml", "com/example/1.0/other.txt")
    }

}
