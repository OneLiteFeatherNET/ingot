package com.reposilite.storage.s3

import com.reposilite.storage.api.Location
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertOk
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.S3Object
import java.time.Instant

private const val BUCKET = "ingot"

// A fixed instant rather than the clock, so the expectation holds on every machine at any time of day.
private val MODIFIED_AT: Instant = Instant.parse("2026-10-15T12:00:00.123Z")

internal class S3EntrySourceTest {

    private val s3 = FakeS3Bucket(BUCKET)

    @Test
    fun `should list the keys in key order with the size and the last modified time of each`() {
        // given: two objects, one of them nested
        s3.put("a/x.jar", size = 5, lastModified = MODIFIED_AT)
        s3.put("a.txt", size = 2, lastModified = MODIFIED_AT.plusSeconds(60))

        // when: the bucket is listed
        val entries = assertOk(source().listEntries(Location.empty(), after = null, limit = 100)).entries

        // then: keys come back in key order, which puts "a.txt" before "a/x.jar" because '.' sorts before '/'
        assertThat(entries.map { it.path.toString() }).describedAs("paths in key order").containsExactly("a.txt", "a/x.jar")

        // and: each entry carries its size and its LastModified
        val jar = entries.single { it.path.toString() == "a/x.jar" }
        assertThat(jar.size).describedAs("size in bytes").isEqualTo(5L)
        assertThat(jar.lastModified).describedAs("last modified").isEqualTo(MODIFIED_AT)
    }

    @Test
    fun `should send no delimiter so that nested keys come back in one flat listing`() {
        // given: an object two directories deep
        s3.put("a/x/y/deep.jar")

        // when: the bucket is listed
        val page = assertOk(source().listEntries(Location.empty(), after = null, limit = 100))

        // then: the request has no delimiter and the nested key is returned directly
        assertThat(s3.requests.single().delimiter()).describedAs("delimiter of the request").isNull()
        assertThat(page.entries.map { it.path.toString() }).containsExactly("a/x/y/deep.jar")
    }

    @Test
    fun `should start each page after the last key of the previous page`() {
        // given: five objects
        listOf("k1.jar", "k2.jar", "k3.jar", "k4.jar", "k5.jar").forEach { s3.put(it) }
        val source = source()

        // when: the bucket is read in pages of two
        val first = assertOk(source.listEntries(Location.empty(), after = null, limit = 2))
        val second = assertOk(source.listEntries(Location.empty(), after = first.next, limit = 2))
        val third = assertOk(source.listEntries(Location.empty(), after = second.next, limit = 2))

        // then: the pages hold the keys in order and the cursor is the last key of each full page
        assertThat(first.entries.map { it.path.toString() }).containsExactly("k1.jar", "k2.jar")
        assertThat(second.entries.map { it.path.toString() }).containsExactly("k3.jar", "k4.jar")
        assertThat(third.entries.map { it.path.toString() }).containsExactly("k5.jar")
        assertThat(third.next).describedAs("cursor after the last page").isNull()

        // and: each request sends StartAfter with the cursor of the page before it
        assertThat(s3.requests.map { it.startAfter() })
            .describedAs("StartAfter of each request")
            .containsExactly(null, "k2.jar", "k4.jar")
        assertThat(s3.requests.map { it.maxKeys() }).describedAs("MaxKeys of each request").containsOnly(2)
    }

    @Test
    fun `should ask for at most 1000 keys per page`() {
        // given: an object in the bucket
        s3.put("a.jar")

        // when: a page larger than the S3 maximum is requested
        assertOk(source().listEntries(Location.empty(), after = null, limit = 5000))

        // then: the request asks for the S3 maximum
        assertThat(s3.requests.single().maxKeys()).describedAs("MaxKeys").isEqualTo(1000)
    }

    @Test
    fun `should skip keys that end with a slash`() {
        // given: a folder object written by a console next to a real file
        s3.put("group/")
        s3.put("group/app-1.0.jar")

        // when: the bucket is listed
        val page = assertOk(source().listEntries(Location.empty(), after = null, limit = 100))

        // then: only the file is an entry
        assertThat(page.entries.map { it.path.toString() }).containsExactly("group/app-1.0.jar")
    }

    @Test
    fun `should list only the keys below the prefix`() {
        // given: a key that starts with the prefix name without being below it
        s3.put("a/x.jar")
        s3.put("ab.jar")

        // when: the keys below "a" are listed
        val page = assertOk(source().listEntries(Location.of("a"), after = null, limit = 100))

        // then: "ab.jar" is not below "a" and is left out
        assertThat(page.entries.map { it.path.toString() }).containsExactly("a/x.jar")
    }

    @Test
    fun `should list only the keys under the key prefix and strip the key prefix from the paths`() {
        // given: two repositories sharing one bucket under different key prefixes
        s3.put("repos/alpha/a.jar")
        s3.put("repos/beta/b.jar")

        // when: the repository alpha is listed
        val page = assertOk(source(keyPrefix = "repos/alpha/").listEntries(Location.empty(), after = null, limit = 100))

        // then: only the key of alpha is listed, with its path relative to the repository
        assertThat(page.entries.map { it.path.toString() }).containsExactly("a.jar")
        assertThat(s3.requests.single().prefix()).describedAs("listed prefix").isEqualTo("repos/alpha/")
    }

    @Test
    fun `should resume after a cursor with the key prefix applied to StartAfter`() {
        // given: keys in the repository and in another one
        s3.put("repos/alpha/a.jar")
        s3.put("repos/alpha/b.jar")
        s3.put("repos/beta/c.jar")

        // when: the listing resumes after "a.jar" of the repository
        val page = assertOk(source(keyPrefix = "repos/alpha/").listEntries(Location.empty(), after = "a.jar", limit = 100))

        // then: StartAfter is the full key and only the later key of the repository is returned
        assertThat(s3.requests.single().startAfter()).describedAs("StartAfter").isEqualTo("repos/alpha/a.jar")
        assertThat(page.entries.map { it.path.toString() }).containsExactly("b.jar")
    }

    private fun source(keyPrefix: String = ""): S3EntrySource =
        S3EntrySource(s3 = s3, bucket = BUCKET, keyPrefix = keyPrefix)

}

/**
 * An in-memory bucket that answers ListObjectsV2 the way S3 does for the parameters the source sends:
 * keys in key order, filtered by Prefix and StartAfter, cut at MaxKeys, with IsTruncated set when more follow.
 * Every request is recorded so that tests can assert on the parameters.
 */
private class FakeS3Bucket(private val bucket: String) : S3Client {

    private val objects = sortedMapOf<String, S3Object>()

    val requests = mutableListOf<ListObjectsV2Request>()

    fun put(key: String, size: Long = 1, lastModified: Instant = MODIFIED_AT) {
        objects[key] = S3Object.builder().key(key).size(size).lastModified(lastModified).build()
    }

    override fun serviceName(): String = S3Client.SERVICE_NAME

    override fun close() {}

    override fun listObjectsV2(request: ListObjectsV2Request): ListObjectsV2Response {
        check(request.bucket() == bucket) { "request for unknown bucket ${request.bucket()}" }
        requests += request

        val prefix = request.prefix().orEmpty()
        val startAfter = request.startAfter()
        val pageSize = request.maxKeys() ?: 1000
        val matching = objects.values.filter { entry ->
            entry.key().startsWith(prefix) && (startAfter == null || entry.key() > startAfter)
        }

        return ListObjectsV2Response.builder()
            .contents(matching.take(pageSize))
            .isTruncated(matching.size > pageSize)
            .build()
    }

}
