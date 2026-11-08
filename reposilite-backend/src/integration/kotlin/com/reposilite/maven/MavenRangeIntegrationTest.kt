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

@file:Suppress("FunctionName")

package com.reposilite.maven

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.RecommendedRemoteSpecificationJunitExtension
import com.reposilite.maven.specification.MavenIntegrationSpecification
import kong.unirest.core.HeaderNames.CACHE_CONTROL
import kong.unirest.core.HeaderNames.CONTENT_LENGTH
import kong.unirest.core.HeaderNames.ETAG
import kong.unirest.core.HeaderNames.LAST_MODIFIED
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.head
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalMavenRangeIntegrationTest : MavenRangeIntegrationTest()

@ExtendWith(RecommendedRemoteSpecificationJunitExtension::class)
internal class RemoteMavenRangeIntegrationTest : MavenRangeIntegrationTest()

/**
 * Range requests and conditional downloads of artifacts, run once against the file system and once against S3 (Floci).
 */
internal abstract class MavenRangeIntegrationTest : MavenIntegrationSpecification() {

    private val content = (0 until 1000).joinToString("") { ('a' + it % 26).toString() }
    private val path = "com/reposilite/ranged/ranged.jar"

    private fun url(repository: String = "immutable", file: String = path) = "$base/$repository/$file"

    private fun deploy(repository: String = "immutable", file: String = path) {
        val gav = file.substringBeforeLast('/')
        val name = file.substringAfterLast('/')
        useDocument(repository, gav, name, content, store = true)
    }

    @Test
    fun `should advertise byte ranges and a strong entity tag on get and head`() {
        deploy()

        val getResponse = get(url()).asBytes()
        val headResponse = head(url()).asEmpty()

        listOf(getResponse.headers, headResponse.headers).forEach { headers ->
            assertThat(headers.getFirst("Accept-Ranges")).isEqualTo("bytes")
            assertThat(headers.getFirst(ETAG)).startsWith("\"").endsWith("\"")
        }
        assertThat(headResponse.headers.getFirst(ETAG)).isEqualTo(getResponse.headers.getFirst(ETAG))
    }

    @Test
    fun `should serve the whole artifact unchanged when no conditional or range header is sent`() {
        deploy()

        val response = get(url()).asBytes()

        assertThat(response.status).isEqualTo(200)
        assertThat(String(response.body)).isEqualTo(content)
        assertThat(response.headers.getFirst(CONTENT_LENGTH)).isEqualTo(content.length.toString())
        assertThat(response.headers.getFirst("Content-Range")).isNullOrEmpty()
        assertThat(response.headers.getFirst(CACHE_CONTROL)).startsWith("public, max-age=")
    }

    @Test
    fun `should answer a closed range with 206 and the matching content range`() {
        deploy()

        val response = get(url()).header("Range", "bytes=0-99").asBytes()

        assertThat(response.status).isEqualTo(206)
        assertThat(String(response.body)).isEqualTo(content.substring(0, 100))
        assertThat(response.headers.getFirst("Content-Range")).isEqualTo("bytes 0-99/1000")
        assertThat(response.headers.getFirst(CONTENT_LENGTH)).isEqualTo("100")
    }

    @Test
    fun `should answer a range from the middle of the artifact`() {
        deploy()

        val response = get(url()).header("Range", "bytes=250-299").asBytes()

        assertThat(response.status).isEqualTo(206)
        assertThat(String(response.body)).isEqualTo(content.substring(250, 300))
        assertThat(response.headers.getFirst("Content-Range")).isEqualTo("bytes 250-299/1000")
    }

    @Test
    fun `should answer a suffix range with the last bytes`() {
        deploy()

        val response = get(url()).header("Range", "bytes=-100").asBytes()

        assertThat(response.status).isEqualTo(206)
        assertThat(String(response.body)).isEqualTo(content.substring(900))
        assertThat(response.headers.getFirst("Content-Range")).isEqualTo("bytes 900-999/1000")
    }

    @Test
    fun `should answer an open range with everything from the start offset`() {
        deploy()

        val response = get(url()).header("Range", "bytes=100-").asBytes()

        assertThat(response.status).isEqualTo(206)
        assertThat(String(response.body)).isEqualTo(content.substring(100))
        assertThat(response.headers.getFirst("Content-Range")).isEqualTo("bytes 100-999/1000")
        assertThat(response.headers.getFirst(CONTENT_LENGTH)).isEqualTo("900")
    }

    @Test
    fun `should answer an unsatisfiable range with 416 and the size`() {
        deploy()

        val response = get(url()).header("Range", "bytes=5000-").asBytes()

        assertThat(response.status).isEqualTo(416)
        assertThat(response.headers.getFirst("Content-Range")).isEqualTo("bytes */1000")
        assertThat(response.body ?: ByteArray(0)).isEmpty()
    }

    @Test
    fun `should serve the whole artifact when several ranges are requested`() {
        deploy()

        val response = get(url()).header("Range", "bytes=0-9,20-29").asBytes()

        assertThat(response.status).isEqualTo(200)
        assertThat(String(response.body)).isEqualTo(content)
    }

    @Test
    fun `should answer a range when if-range holds the current entity tag`() {
        deploy()
        val etag = get(url()).asBytes().headers.getFirst(ETAG)

        val response = get(url()).header("Range", "bytes=0-9").header("If-Range", etag).asBytes()

        assertThat(response.status).isEqualTo(206)
        assertThat(String(response.body)).isEqualTo(content.substring(0, 10))
    }

    @Test
    fun `should serve the whole artifact when if-range holds a stale entity tag`() {
        deploy()

        val response = get(url()).header("Range", "bytes=0-9").header("If-Range", "\"stale\"").asBytes()

        assertThat(response.status).isEqualTo(200)
        assertThat(String(response.body)).isEqualTo(content)
    }

    @Test
    fun `should answer 304 without a body when if-none-match holds the current entity tag`() {
        deploy()
        val etag = get(url()).asBytes().headers.getFirst(ETAG)

        val response = get(url()).header("If-None-Match", etag).asBytes()

        assertThat(response.status).isEqualTo(304)
        assertThat(response.body ?: ByteArray(0)).isEmpty()
        assertThat(response.headers.getFirst(ETAG)).isEqualTo(etag)
        assertThat(response.headers.getFirst(CACHE_CONTROL)).startsWith("public, max-age=")
    }

    @Test
    fun `should answer 200 when if-none-match holds another entity tag`() {
        deploy()

        val response = get(url()).header("If-None-Match", "\"other\"").asBytes()

        assertThat(response.status).isEqualTo(200)
        assertThat(String(response.body)).isEqualTo(content)
    }

    @Test
    fun `should answer 304 when the artifact is not modified since the given date`() {
        deploy()
        val lastModified = get(url()).asBytes().headers.getFirst(LAST_MODIFIED)

        val response = get(url()).header("If-Modified-Since", lastModified).asBytes()

        assertThat(response.status).isEqualTo(304)
        assertThat(response.body ?: ByteArray(0)).isEmpty()
    }

    @Test
    fun `should answer 304 to a conditional head request`() {
        deploy()
        val etag = head(url()).asEmpty().headers.getFirst(ETAG)

        val response = head(url()).header("If-None-Match", etag).asEmpty()

        assertThat(response.status).isEqualTo(304)
    }

    @Test
    fun `should keep the no-cache headers of a snapshot repository`() {
        val snapshot = "net/onelitefeather/ranged/1.0-SNAPSHOT/ranged-1.0-SNAPSHOT.jar"
        deploy("snapshots", snapshot)

        val full = get(url("snapshots", snapshot)).asBytes()
        val partial = get(url("snapshots", snapshot)).header("Range", "bytes=0-9").asBytes()
        val notModified = get(url("snapshots", snapshot)).header("If-None-Match", full.headers.getFirst(ETAG)).asBytes()

        assertThat(full.headers.getFirst(CACHE_CONTROL)).isEqualTo("no-cache, no-store, max-age=0")
        assertThat(partial.status).isEqualTo(206)
        assertThat(partial.headers.getFirst(CACHE_CONTROL)).isEqualTo("no-cache, no-store, max-age=0")
        assertThat(notModified.status).isEqualTo(304)
        assertThat(notModified.headers.getFirst(CACHE_CONTROL)).isEqualTo("no-cache, no-store, max-age=0")
    }

    @Test
    fun `should keep the no-cache headers of maven metadata`() {
        val metadata = "com/reposilite/ranged/maven-metadata.xml"
        deploy(file = metadata)

        val response = get(url(file = metadata)).asBytes()
        val notModified = get(url(file = metadata)).header("If-None-Match", response.headers.getFirst(ETAG)).asBytes()

        assertThat(response.status).isEqualTo(200)
        assertThat(response.headers.getFirst(CACHE_CONTROL)).isEqualTo("no-cache, no-store, max-age=0")
        assertThat(notModified.status).isEqualTo(304)
        assertThat(notModified.headers.getFirst(CACHE_CONTROL)).isEqualTo("no-cache, no-store, max-age=0")
    }

    @Test
    fun `should answer a head request with a range like the matching get without a body`() {
        deploy()

        val response = head(url()).header("Range", "bytes=0-9").asEmpty()

        assertThat(response.status).isEqualTo(206)
        assertThat(response.headers.getFirst(CONTENT_LENGTH)).isEqualTo("10")
        assertThat(response.headers.getFirst("Content-Range")).isEqualTo("bytes 0-9/1000")
    }

    @Test
    fun `should derive the entity tag from the stored checksum`() {
        deploy()
        useDocument("immutable", "com/reposilite/ranged", "ranged.jar.sha256", "abc123", store = true)

        val etag = get(url()).asBytes().headers.getFirst(ETAG)

        assertThat(etag).isEqualTo("\"abc123\"")
    }

    @Test
    fun `should change the entity tag when the stored checksum changes`() {
        deploy("immutable", "com/reposilite/ranged/other.jar")
        useDocument("immutable", "com/reposilite/ranged", "other.jar.sha1", "aaa111", store = true)
        val before = get(url(file = "com/reposilite/ranged/other.jar")).asBytes().headers.getFirst(ETAG)
        val second = "com/reposilite/ranged2/other.jar"
        deploy("immutable", second)
        useDocument("immutable", "com/reposilite/ranged2", "other.jar.sha1", "bbb222", store = true)

        val after = get(url(file = second)).asBytes().headers.getFirst(ETAG)

        assertThat(before).isEqualTo("\"aaa111\"")
        assertThat(after).isEqualTo("\"bbb222\"").isNotEqualTo(before)
    }

}
