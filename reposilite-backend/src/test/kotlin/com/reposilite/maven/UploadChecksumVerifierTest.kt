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
package com.reposilite.maven

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.api.Checksum
import com.reposilite.maven.api.UploadChecksumMismatchEvent
import com.reposilite.maven.api.UploadChecksumOutcome
import com.reposilite.storage.api.Location
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.api.toLocation
import io.javalin.http.HttpStatus.BAD_REQUEST
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError
import panda.std.Result
import panda.std.ResultAssertions.assertOk
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * The decisions of the upload checksum verifier, without HTTP and without a storage provider. The stored artifacts are a
 * map held by the test, and every read of the map is counted, so a test can state whether a digest was computed.
 */
internal class UploadChecksumVerifierTest {

    private val artifactPath = "com/example/lib/1.0.0/lib-1.0.0.jar"
    private val artifact: Location = artifactPath.toLocation()
    private val originalBytes = "original bytes of the library"
    private val originalSha1 = DigestUtils.sha1Hex(originalBytes)

    private val stored = mutableMapOf<String, ByteArray>()
    private val reads = mutableListOf<String>()
    private val events = mutableListOf<UploadChecksumMismatchEvent>()
    private val logger = InMemoryLogger()

    private val storedFile = StoredFileReader { location ->
        reads += location.toString()
        stored[location.toString()]?.inputStream()
    }

    private val verifier = UploadChecksumVerifier(journalist = logger, publish = { events += it })

    init {
        stored[artifactPath] = originalBytes.toByteArray()
    }

    private fun verify(policy: UploadChecksumPolicy, path: String, body: String): Result<InputStream, ErrorResponse> =
        verifier.verify(
            repository = "releases",
            policy = policy,
            gav = path.toLocation(),
            by = "deployer",
            content = ByteArrayInputStream(body.toByteArray()),
            storedFile = storedFile,
        )

    @Test
    fun `should accept a matching checksum file under reject without an event`() {
        // given: an artifact that is stored and a sha1 file that matches it
        // when: the checksum file is verified under reject
        val result = verifier.verify("releases", UploadChecksumPolicy.REJECT, "$artifactPath.sha1".toLocation(), "deployer", originalSha1.byteInputStream(), storedFile)

        // then: the file is accepted and no mismatch is recorded
        assertOk(result)
        assertThat(events).describedAs("events for a matching checksum file").isEmpty()
    }

    @Test
    fun `should accept a matching checksum file under warn without an event`() {
        // when: the checksum file is verified under warn
        val result = verifier.verify("releases", UploadChecksumPolicy.WARN, "$artifactPath.sha1".toLocation(), "deployer", originalSha1.byteInputStream(), storedFile)

        // then: the file is accepted and no mismatch is recorded
        assertOk(result)
        assertThat(events).describedAs("events for a matching checksum file under warn").isEmpty()
    }

    @Test
    fun `should accept the sha1sum form of a matching digest`() {
        // given: an upload in the form sha1sum writes, with the digest in upper case
        val body = "${originalSha1.uppercase()}  lib-1.0.0.jar\n"

        // when: the checksum file is verified under reject
        val result = verify(UploadChecksumPolicy.REJECT, "$artifactPath.sha1", body)

        // then: the file is accepted
        assertThat(result.isOk).describedAs("sha1sum form of the matching digest accepted").isTrue
    }

    @Test
    fun `should refuse a mismatching checksum file under reject with 400 and an event`() {
        // given: an artifact and a sha1 file with another digest
        val wrong = DigestUtils.sha1Hex("other bytes")

        // when: the checksum file is verified under reject
        val result = verifier.verify("releases", UploadChecksumPolicy.REJECT, "$artifactPath.sha1".toLocation(), "deployer", wrong.byteInputStream(), storedFile)

        // then: the answer is 400 and names the algorithm
        val error = assertError(result)
        assertThat(error.status).describedAs("status of a mismatch under reject").isEqualTo(BAD_REQUEST.code)
        assertThat(error.message).describedAs("message of a mismatch under reject").contains("SHA1")
        assertThat(error.type).describedAs("problem type of a mismatch under reject").isEqualTo(UPLOAD_CHECKSUM_MISMATCH_PROBLEM_TYPE)

        // and: one event records the refusal with the algorithm
        assertThat(events).describedAs("events of a refused mismatch").hasSize(1)
        assertThat(events.single().outcome).describedAs("outcome of a refused mismatch").isEqualTo(UploadChecksumOutcome.REJECTED)
        assertThat(events.single().algorithm).describedAs("algorithm of a refused mismatch").isEqualTo(Checksum.SHA1)
    }

    @Test
    fun `should store a mismatching checksum file under warn and record it as accepted`() {
        // given: an artifact and a sha256 file with another digest
        val wrong = DigestUtils.sha256Hex("other bytes")

        // when: the checksum file is verified under warn
        val result = verifier.verify("releases", UploadChecksumPolicy.WARN, "$artifactPath.sha256".toLocation(), "deployer", wrong.byteInputStream(), storedFile)

        // then: the content is passed on for storage, unchanged
        assertThat(assertOk(result).readBytes().toString(Charsets.UTF_8)).describedAs("content passed on under warn").isEqualTo(wrong)

        // and: the mismatch is recorded as accepted and logged
        assertThat(events.single().outcome).describedAs("outcome under warn").isEqualTo(UploadChecksumOutcome.ACCEPTED)
        assertThat(events.single().algorithm).describedAs("algorithm under warn").isEqualTo(Checksum.SHA256)
        assertThat(logger.messages.map { it.value }.filter { "checksum" in it.lowercase() })
            .describedAs("log lines about the mismatch under warn").hasSize(1)
    }

    @Test
    fun `should refuse a body that is not a digest under reject`() {
        // when: a sha1 file holds text that is not a digest
        val result = verify(UploadChecksumPolicy.REJECT, "$artifactPath.sha1", "not-a-digest")

        // then: the upload is refused
        assertThat(result.isErr).describedAs("refusal of a body that is not a digest").isTrue
    }

    @Test
    fun `should refuse a body longer than 1024 bytes under reject`() {
        // given: a sha1 file that is far longer than any digest
        val body = originalSha1 + "\n" + "x".repeat(UPLOAD_CHECKSUM_MAX_BYTES)

        // when: the checksum file is verified under reject
        val result = verify(UploadChecksumPolicy.REJECT, "$artifactPath.sha1", body)

        // then: the upload is refused, even though its first word is the right digest
        assertThat(result.isErr).describedAs("refusal of a body over the limit").isTrue
    }

    @Test
    fun `should accept a checksum file whose artifact is not stored, under every policy`() {
        // given: a sha1 file for an artifact that has not been uploaded yet, with a wrong digest
        val missingPath = "com/example/other/1.0.0/other-1.0.0.jar"
        val wrong = DigestUtils.sha1Hex("other bytes")

        // when: the checksum file is verified under reject
        val result = verify(UploadChecksumPolicy.REJECT, "$missingPath.sha1", wrong)

        // then: the file is accepted without a check and no event is recorded
        assertThat(result.isOk).describedAs("checksum file that precedes its artifact").isTrue
        assertThat(events).describedAs("events for a checksum file that precedes its artifact").isEmpty()
    }

    @Test
    fun `should not compute a digest for an artifact file`() {
        // when: a jar is verified under reject
        verify(UploadChecksumPolicy.REJECT, artifactPath, "any content")

        // then: no stored file was read
        assertThat(reads).describedAs("stored reads for a jar").isEmpty()
    }

    @Test
    fun `should not compute a digest under the ignore policy`() {
        // when: a mismatching checksum file is verified under ignore
        val result = verify(UploadChecksumPolicy.IGNORE, "$artifactPath.sha1", "0".repeat(40))

        // then: the file is stored and no stored file was read
        assertThat(result.isOk).describedAs("mismatch under ignore").isTrue
        assertThat(reads).describedAs("stored reads under ignore").isEmpty()
        assertThat(events).describedAs("events under ignore").isEmpty()
    }

    @Test
    fun `should give back the whole content of a checksum file that is stored`() {
        // given: a checksum file whose body is longer than the limit of a digest, under warn
        val body = "0".repeat(UPLOAD_CHECKSUM_MAX_BYTES + 100)

        // when: the checksum file is verified under warn
        val result = verify(UploadChecksumPolicy.WARN, "$artifactPath.sha1", body)

        // then: every byte is passed on for storage, not only the part that was read
        assertThat(assertOk(result).readBytes().toString(Charsets.UTF_8)).describedAs("content passed on for storage").isEqualTo(body)
    }
}
