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

package net.onelitefeather.ingot.nexusimport.copy

import net.onelitefeather.ingot.nexusimport.fake.FakeReply
import net.onelitefeather.ingot.nexusimport.jobs.AssetResult
import net.onelitefeather.ingot.nexusimport.nexus.NexusAsset
import net.onelitefeather.ingot.nexusimport.nexus.NexusChecksums
import net.onelitefeather.ingot.nexusimport.nexus.NexusHttpException
import net.onelitefeather.ingot.nexusimport.nexus.NexusReply
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.Path
import java.security.MessageDigest

internal class FileCopierTest {

    @TempDir
    lateinit var root: Path

    private val target = MemoryTarget()
    private val downloads = mutableListOf<String>()
    private var served: ByteArray = ByteArray(0)
    private var status = 200

    private fun copier(): FileCopier = FileCopier(
        open = { asset ->
            downloads += asset.path
            if (status != 200) throw NexusHttpException(status, URI.create("http://nexus.test/${asset.path}"))
            NexusReply(200, emptyMap(), ByteArrayInputStream(served))
        },
        target = target,
        tempDir = root,
    )

    private fun asset(bytes: ByteArray, path: String = "com/example/app/1.0/app-1.0.jar", size: Long = bytes.size.toLong()) = NexusAsset(
        repository = "releases",
        path = path,
        downloadUrl = "http://nexus.test/$path",
        fileSize = size,
        checksums = NexusChecksums(sha1 = hex("SHA-1", bytes), md5 = hex("MD5", bytes), sha256 = null, sha512 = null),
        lastModified = null,
    )

    private fun hex(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `should copy a file whose bytes equal the listed checksums`() {
        // given: a file that Nexus serves intact
        val bytes = "jar-bytes".toByteArray()
        served = bytes

        // when: the file is copied
        val result = copier().copy(asset(bytes), ConflictPolicy.REFUSE, "releases")

        // then: the target holds the same bytes and the result is copied
        assertThat(result).isEqualTo(AssetResult.COPIED)
        assertThat(target.read("releases", asset(bytes).path)).isEqualTo(bytes)
    }

    @Test
    fun `should leave no file when the body does not match the listed sha1`() {
        // given: a body that differs from the listing
        val listed = "expected".toByteArray()
        served = "corrupt!".toByteArray()

        // when: the copy runs
        val result = copier().copy(asset(listed), ConflictPolicy.REFUSE, "releases")

        // then: the checksum failure is recorded and nothing is stored
        assertThat(result).isEqualTo(AssetResult.CHECKSUM_FAILED)
        assertThat(target.read("releases", asset(listed).path)).describedAs("a corrupt body must not reach the target").isNull()
    }

    @Test
    fun `should leave no file when the body is shorter than the listed size`() {
        // given: a truncated body
        val listed = "complete-file".toByteArray()
        served = "complete".toByteArray()

        // when / then: the size mismatch is a checksum failure and nothing is stored
        assertThat(copier().copy(asset(listed), ConflictPolicy.REFUSE, "releases")).isEqualTo(AssetResult.CHECKSUM_FAILED)
        assertThat(target.read("releases", asset(listed).path)).isNull()
    }

    @Test
    fun `should skip a second copy of a file with the same checksum without downloading it`() {
        // given: the file is already in the target with the listed sha1
        val bytes = "same".toByteArray()
        target.write("releases", asset(bytes).path, bytes)

        // when: the copy runs again
        val result = copier().copy(asset(bytes), ConflictPolicy.REFUSE, "releases")

        // then: the file is skipped and Nexus sees no download
        assertThat(result).isEqualTo(AssetResult.SKIPPED_SAME)
        assertThat(downloads).isEmpty()
    }

    @Test
    fun `should refuse a differing existing file and keep it unchanged`() {
        // given: the target holds a different file at the same path
        val listed = "from-nexus".toByteArray()
        target.write("releases", asset(listed).path, "local".toByteArray())
        served = listed

        // when: the default policy meets the conflict
        val result = copier().copy(asset(listed), ConflictPolicy.REFUSE, "releases")

        // then: the conflict is recorded and the local file stays
        assertThat(result).isEqualTo(AssetResult.CONFLICT)
        assertThat(target.read("releases", asset(listed).path)).isEqualTo("local".toByteArray())
        assertThat(downloads).describedAs("refuse must not download the conflicting file").isEmpty()
    }

    @Test
    fun `should replace a differing existing file under the overwrite policy`() {
        // given: a differing file at the same path
        val listed = "from-nexus".toByteArray()
        target.write("releases", asset(listed).path, "local".toByteArray())
        served = listed

        // when: the overwrite policy meets the conflict
        val result = copier().copy(asset(listed), ConflictPolicy.OVERWRITE, "releases")

        // then: the file is replaced by the Nexus bytes
        assertThat(result).isEqualTo(AssetResult.COPIED)
        assertThat(target.read("releases", asset(listed).path)).isEqualTo(listed)
    }

    @Test
    fun `should record a file that Nexus no longer has as gone`() {
        // given: Nexus answers 404 for the file after it was listed
        status = 404

        // when / then: the file is GONE and the copy does not fail
        assertThat(copier().copy(asset("x".toByteArray()), ConflictPolicy.REFUSE, "releases")).isEqualTo(AssetResult.GONE)
    }

    @Test
    fun `should let a credential refusal reach the caller so the job can pause`() {
        // given: Nexus refuses the download with 401
        status = 401

        // when / then: the refusal is raised, not recorded as a file result
        org.assertj.core.api.Assertions.assertThatThrownBy {
            copier().copy(asset("x".toByteArray()), ConflictPolicy.REFUSE, "releases")
        }.isInstanceOf(NexusHttpException::class.java)
    }
}
