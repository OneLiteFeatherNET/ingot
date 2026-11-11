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

import com.reposilite.maven.api.Checksum
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk

/** The policy of uploaded checksum files: which value applies, and how the digest in an upload is read. */
internal class UploadChecksumPolicyTest {

    private val sha1Hex = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun `should use the repository value over the instance variables and the system property`() {
        // given: a repository that sets ignore, and an instance that says reject everywhere
        val environment = mapOf("INGOT_UPLOAD_CHECKSUM_POLICY" to "reject", "REPOSILITE_UPLOAD_CHECKSUM_POLICY" to "reject")

        // when: the policy is resolved
        val policy = resolveUploadChecksumPolicy(UploadChecksumPolicy.IGNORE, environment, systemProperty = "reject")

        // then: the repository value wins
        assertThat(assertOk(policy)).describedAs("policy of a repository that sets ignore").isEqualTo(UploadChecksumPolicy.IGNORE)
    }

    @Test
    fun `should use the INGOT variable over the REPOSILITE fallback`() {
        // given: both spellings of the instance variable, with different values
        val environment = mapOf("INGOT_UPLOAD_CHECKSUM_POLICY" to "warn", "REPOSILITE_UPLOAD_CHECKSUM_POLICY" to "reject")

        // when: a repository without a value resolves the policy
        val policy = resolveUploadChecksumPolicy(repositoryValue = null, environment = environment, systemProperty = null)

        // then: the INGOT value applies
        assertThat(assertOk(policy)).describedAs("policy with both variables set").isEqualTo(UploadChecksumPolicy.WARN)
    }

    @Test
    fun `should read the REPOSILITE fallback when the INGOT variable is not set`() {
        // given: only the fallback spelling is set
        val environment = mapOf("REPOSILITE_UPLOAD_CHECKSUM_POLICY" to "reject")

        // when: a repository without a value resolves the policy
        val policy = resolveUploadChecksumPolicy(repositoryValue = null, environment = environment, systemProperty = null)

        // then: the fallback applies
        assertThat(assertOk(policy)).describedAs("policy from the fallback variable").isEqualTo(UploadChecksumPolicy.REJECT)
    }

    @Test
    fun `should use the system property when no variable is set`() {
        // given: a system property and no environment variable

        // when: a repository without a value resolves the policy
        val policy = resolveUploadChecksumPolicy(repositoryValue = null, environment = emptyMap(), systemProperty = "warn")

        // then: the system property applies
        assertThat(assertOk(policy)).describedAs("policy from the system property").isEqualTo(UploadChecksumPolicy.WARN)
    }

    @Test
    fun `should default to ignore when nothing is set`() {
        // given: no repository value, no variable and no system property

        // when: the policy is resolved
        val policy = resolveUploadChecksumPolicy(repositoryValue = null, environment = emptyMap(), systemProperty = null)

        // then: the policy is ignore, so nothing changes for existing installations
        assertThat(assertOk(policy)).describedAs("default policy").isEqualTo(UploadChecksumPolicy.IGNORE)
    }

    @Test
    fun `should refuse an instance variable with an unknown value and name the variable`() {
        // given: an instance variable with a value that is not a policy
        val environment = mapOf("INGOT_UPLOAD_CHECKSUM_POLICY" to "strict")

        // when: a repository without a value resolves the policy
        val policy = resolveUploadChecksumPolicy(repositoryValue = null, environment = environment, systemProperty = null)

        // then: the refusal names the variable and the accepted values
        assertThat(assertError(policy)).describedAs("refusal of an unknown instance value")
            .contains("INGOT_UPLOAD_CHECKSUM_POLICY", "strict", "ignore", "warn", "reject")
    }

    @Test
    fun `should read the policy values regardless of letter case`() {
        // given: a variable written in upper case
        val environment = mapOf("INGOT_UPLOAD_CHECKSUM_POLICY" to "REJECT")

        // when: a repository without a value resolves the policy
        val policy = resolveUploadChecksumPolicy(repositoryValue = null, environment = environment, systemProperty = null)

        // then: the value is read as reject
        assertThat(assertOk(policy)).describedAs("policy written in upper case").isEqualTo(UploadChecksumPolicy.REJECT)
    }

    @Test
    fun `should accept a bare digest of the length of the algorithm`() {
        // given: an upload whose content is only the digest
        val body = sha1Hex.toByteArray()

        // when: the digest is read for sha1
        val digest = parseUploadedDigest(body, Checksum.SHA1)

        // then: the digest is returned
        assertThat(digest).describedAs("bare sha1 digest").isEqualTo(sha1Hex)
    }

    @Test
    fun `should accept the sha1sum form of a digest followed by two spaces and a file name`() {
        // given: an upload in the form sha1sum writes
        val body = "$sha1Hex  lib-1.0.0.jar\n".toByteArray()

        // when: the digest is read for sha1
        val digest = parseUploadedDigest(body, Checksum.SHA1)

        // then: only the digest is returned
        assertThat(digest).describedAs("digest of the sha1sum form").isEqualTo(sha1Hex)
    }

    @Test
    fun `should read an upper case digest as lower case`() {
        // given: an upload whose digest is upper case
        val body = sha1Hex.uppercase().toByteArray()

        // when: the digest is read for sha1
        val digest = parseUploadedDigest(body, Checksum.SHA1)

        // then: the digest is returned in lower case
        assertThat(digest).describedAs("upper case digest read in lower case").isEqualTo(sha1Hex)
    }

    @Test
    fun `should refuse a digest that has the wrong length for the algorithm`() {
        // given: a sha1 digest that is one character short
        val body = sha1Hex.dropLast(1).toByteArray()

        // when: the digest is read for sha1
        val digest = parseUploadedDigest(body, Checksum.SHA1)

        // then: no digest is returned
        assertThat(digest).describedAs("digest of the wrong length").isNull()
    }

    @Test
    fun `should refuse a digest that contains characters outside hex`() {
        // given: a sha1 length value with a character that is not hex
        val body = sha1Hex.replaceRange(0, 1, "g").toByteArray()

        // when: the digest is read for sha1
        val digest = parseUploadedDigest(body, Checksum.SHA1)

        // then: no digest is returned
        assertThat(digest).describedAs("digest with a non hex character").isNull()
    }

    @Test
    fun `should refuse an upload longer than 1024 bytes`() {
        // given: a checksum file that is too long to be a digest
        val body = ("0".repeat(UPLOAD_CHECKSUM_MAX_BYTES) + "\n").toByteArray()

        // when: the digest is read for sha1
        val digest = parseUploadedDigest(body, Checksum.SHA1)

        // then: no digest is returned
        assertThat(digest).describedAs("digest of an upload over the limit").isNull()
    }

    @Test
    fun `should read the digest of md5 with its own length`() {
        // given: an md5 digest, which is shorter than a sha1 digest
        val md5Hex = "0123456789abcdef0123456789abcdef"

        // when: the digest is read for md5
        val digest = parseUploadedDigest(md5Hex.toByteArray(), Checksum.MD5)

        // then: the digest is returned
        assertThat(digest).describedAs("md5 digest").isEqualTo(md5Hex)
    }
}
