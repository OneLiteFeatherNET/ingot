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

import com.reposilite.ReposiliteObjectMapper
import com.reposilite.maven.api.LookupRequest
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.shared.notFoundError
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import io.javalin.http.ContentType.TEXT_XML
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import com.reposilite.shared.http.FakeRemoteClientProvider
import com.reposilite.shared.http.RemoteClientProvider
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import panda.std.asSuccess
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The checksum policy of a mirror with `store` enabled. The upstream is an in-memory map of URI to body, served through
 * [FakeRemoteClientProvider], so no request leaves the test. Every test publishes its own files and reads what the
 * facade answers and what the storage holds.
 */
internal class MirrorChecksumPolicyTest : MavenSpecification() {

    private val gavPath = "com/example/lib/1.0.0/lib-1.0.0.jar"
    private val gav: Location = gavPath.toLocation()
    private val originalBody = "original bytes of the library"
    private val corruptedBody = "corrupted bytes served by an upstream"

    private val upstream = ConcurrentHashMap<String, String>()
    private val requestedUris = ConcurrentLinkedQueue<String>()

    override fun repositories() = listOf(
        mirror("absent-policy", "https://absent.example/releases", policy = null),
        mirror("explicit-ignore", "https://explicit.example/releases", policy = "ignore"),
        mirror("warn-mirror", "https://warn.example/releases", policy = "warn"),
        mirror("fail-single", "https://single.example/releases", policy = "fail"),
        RepositorySettings(
            id = "fail-mirror",
            proxied = listOf(
                mirrorSettings("https://primary.example/releases", policy = "fail"),
                mirrorSettings("https://secondary.example/releases", policy = "fail"),
            ),
        ),
    )

    override fun remoteClientProvider(): RemoteClientProvider = FakeRemoteClientProvider(
        headHandler = { uri, _, _, _ ->
            if (upstream.containsKey(uri)) DocumentInfo(name = uri.substringAfterLast('/'), contentType = TEXT_XML).asSuccess()
            else notFoundError("Not found")
        },
        getHandler = { uri, _, _, _ ->
            requestedUris += uri
            val body = upstream[uri]
            if (body != null) body.byteInputStream().asSuccess()
            else notFoundError("Not found")
        },
    )

    @Test
    fun `should store an artifact without requesting a checksum file when the mirror has no checksum policy`() {
        // given: an upstream that serves the artifact and a checksum file that does not match it
        publish("https://absent.example/releases", gavPath, originalBody)
        publish("https://absent.example/releases", "$gavPath.sha256", sha256(corruptedBody))

        // when: the artifact is requested through the mirror
        val result = mavenFacade.findFile(lookup("absent-policy"))

        // then: the artifact is served and no checksum file was requested
        assertThat(assertOk(result).content.readBytes().toString(Charsets.UTF_8)).describedAs("served content").isEqualTo(originalBody)
        assertThat(requestedUris.filter { it.endsWith(".sha256") || it.endsWith(".sha1") })
            .describedAs("checksum files requested from upstream without a checksum policy")
            .isEmpty()
    }

    @Test
    fun `should store an artifact without requesting a checksum file when the checksum policy is ignore`() {
        // given: an upstream that serves the artifact and a checksum file that does not match it
        publish("https://explicit.example/releases", gavPath, originalBody)
        publish("https://explicit.example/releases", "$gavPath.sha256", sha256(corruptedBody))

        // when: the artifact is requested through the mirror
        val result = mavenFacade.findFile(lookup("explicit-ignore"))

        // then: the artifact is served and no checksum file was requested
        assertThat(assertOk(result).content.readBytes().toString(Charsets.UTF_8)).describedAs("served content").isEqualTo(originalBody)
        assertThat(requestedUris.filter { it.endsWith(".sha256") || it.endsWith(".sha1") })
            .describedAs("checksum files requested from upstream under the ignore policy")
            .isEmpty()
    }

    @Test
    fun `should store and log a mismatching artifact when the checksum policy is warn`() {
        // given: an upstream whose artifact does not match its checksum file
        publish("https://warn.example/releases", gavPath, corruptedBody)
        publish("https://warn.example/releases", "$gavPath.sha256", sha256(originalBody))

        // when: the artifact is requested through the mirror
        val result = mavenFacade.findFile(lookup("warn-mirror"))

        // then: the copy is stored and served as before
        assertThat(assertOk(result).content.readBytes().toString(Charsets.UTF_8)).describedAs("served content").isEqualTo(corruptedBody)
        assertThat(storedContent("warn-mirror", gav)).describedAs("stored content under warn").isEqualTo(corruptedBody)

        // and: the mismatch is logged with the artifact and the host
        val warnings = logger.messages.map { it.value }.filter { "checksum" in it }
        assertThat(warnings).describedAs("log lines about the checksum mismatch").hasSize(1)
        assertThat(warnings.single()).describedAs("log line naming the artifact and the host")
            .contains("lib-1.0.0.jar", "warn.example")
    }

    @Test
    fun `should store and serve a matching artifact when the checksum policy is fail`() {
        // given: an upstream whose artifact matches its sha256 file
        publish("https://single.example/releases", gavPath, originalBody)
        publish("https://single.example/releases", "$gavPath.sha256", sha256(originalBody))

        // when: the artifact is requested through the mirror
        val result = mavenFacade.findFile(lookup("fail-single"))

        // then: the artifact is served and stored
        assertThat(assertOk(result).content.readBytes().toString(Charsets.UTF_8)).describedAs("served content").isEqualTo(originalBody)
        assertThat(storedContent("fail-single", gav)).describedAs("stored content under fail").isEqualTo(originalBody)
    }

    @Test
    fun `should refuse a mismatching host under fail and store the matching copy of the next host`() {
        // given: the first host serves a corrupted artifact, the second serves the original
        publish("https://primary.example/releases", gavPath, corruptedBody)
        publish("https://primary.example/releases", "$gavPath.sha256", sha256(originalBody))
        publish("https://secondary.example/releases", gavPath, originalBody)
        publish("https://secondary.example/releases", "$gavPath.sha256", sha256(originalBody))

        // when: the artifact is requested through the mirror
        val result = mavenFacade.findFile(lookup("fail-mirror"))

        // then: the copy of the second host is served and stored, and the corrupted copy never reached storage
        assertThat(assertOk(result).content.readBytes().toString(Charsets.UTF_8)).describedAs("served content").isEqualTo(originalBody)
        assertThat(storedContent("fail-mirror", gav)).describedAs("stored content under fail").isEqualTo(originalBody)
    }

    @Test
    fun `should verify an artifact against the sha1 file of its host under fail when the sha256 file is missing`() {
        // given: a host without a sha256 file whose sha1 file matches the artifact
        publish("https://single.example/releases", gavPath, originalBody)
        publish("https://single.example/releases", "$gavPath.sha1", sha1(originalBody))

        // when: the artifact is requested through the mirror
        val result = mavenFacade.findFile(lookup("fail-single"))

        // then: the artifact is served and stored
        assertThat(assertOk(result).content.readBytes().toString(Charsets.UTF_8)).describedAs("served content").isEqualTo(originalBody)
        assertThat(storedContent("fail-single", gav)).describedAs("stored content under fail").isEqualTo(originalBody)
    }

    @Test
    fun `should refuse a host without any checksum file under fail`() {
        // given: a host that serves the artifact but neither a sha256 nor a sha1 file
        publish("https://single.example/releases", gavPath, originalBody)

        // when: the artifact is requested through the mirror
        val result = mavenFacade.findFile(lookup("fail-single"))

        // then: the host is refused as an upstream failure and nothing is stored
        assertThat(assertError(result).status).describedAs("status of the refused host").isEqualTo(502)
        assertThat(storedContent("fail-single", gav)).describedAs("stored content of a refused host").isNull()
    }

    @Test
    fun `should answer bad gateway and store nothing when every host serves a mismatching artifact under fail`() {
        // given: both hosts serve an artifact that does not match their checksum file
        publish("https://primary.example/releases", gavPath, corruptedBody)
        publish("https://primary.example/releases", "$gavPath.sha256", sha256(originalBody))
        publish("https://secondary.example/releases", gavPath, corruptedBody)
        publish("https://secondary.example/releases", "$gavPath.sha256", sha256(originalBody))

        // when: the artifact is requested through the mirror
        val result = mavenFacade.findFile(lookup("fail-mirror"))

        // then: the answer is a bad gateway, and the path is not in storage
        assertThat(assertError(result).status).describedAs("status when every host mismatches").isEqualTo(502)
        assertThat(storedContent("fail-mirror", gav)).describedAs("stored content when every host mismatches").isNull()
    }

    @Test
    fun `should request a refused artifact from upstream again on the next request under fail`() {
        // given: the only host serves a corrupted artifact
        publish("https://single.example/releases", gavPath, corruptedBody)
        publish("https://single.example/releases", "$gavPath.sha256", sha256(originalBody))

        // when: the artifact is requested twice
        val first = mavenFacade.findFile(lookup("fail-single"))
        val second = mavenFacade.findFile(lookup("fail-single"))

        // then: both requests are refused, and the second one went to upstream again
        assertThat(assertError(first).status).describedAs("status of the first request").isEqualTo(502)
        assertThat(assertError(second).status).describedAs("status of the second request").isEqualTo(502)
        assertThat(requestedUris.count { it == "https://single.example/releases/$gavPath" })
            .describedAs("artifact requests to upstream after a refused copy")
            .isEqualTo(2)
        val directory = gav.getParent()
        assertThat(storedContent("fail-single", gav)).describedAs("stored content after a refused copy").isNull()
        assertThat(mavenFacade.getRepository("fail-single")!!.storageProvider.getFiles(directory).get().map { it.getSimpleName() })
            .describedAs("directory listing after a refused copy")
            .doesNotContain("lib-1.0.0.jar")
    }

    @Test
    fun `should store metadata and checksum files without checking them under fail`() {
        // given: an upstream that serves a metadata file and a checksum file without any checksum of their own
        publish("https://single.example/releases", "com/example/lib/maven-metadata.xml", "<metadata/>")
        publish("https://single.example/releases", "$gavPath.sha256", "not a checksum at all")

        // when: both files are requested through the mirror
        val metadata = mavenFacade.findFile(LookupRequest(UNAUTHORIZED, "fail-single", "com/example/lib/maven-metadata.xml".toLocation()))
        val checksum = mavenFacade.findFile(LookupRequest(UNAUTHORIZED, "fail-single", "$gavPath.sha256".toLocation()))

        // then: both are served and no checksum of a checksum file was requested
        assertThat(assertOk(metadata).content.readBytes().toString(Charsets.UTF_8)).describedAs("served metadata").isEqualTo("<metadata/>")
        assertThat(assertOk(checksum).content.readBytes().toString(Charsets.UTF_8)).describedAs("served checksum file").isEqualTo("not a checksum at all")
        assertThat(requestedUris.filter { it.endsWith(".sha256.sha256") || it.endsWith(".sha1") })
            .describedAs("checksum requests made for metadata or checksum files")
            .isEmpty()
    }

    private fun lookup(repository: String): LookupRequest =
        LookupRequest(UNAUTHORIZED, repository, gav)

    private fun publish(host: String, path: String, body: String) {
        upstream["$host/$path"] = body
    }

    private fun storedContent(repository: String, location: Location): String? {
        val result = mavenFacade.getRepository(repository)!!.storageProvider.getFile(location)
        return if (result.isOk) result.get().use { it.readBytes().toString(Charsets.UTF_8) } else null
    }

    private fun mirror(id: String, reference: String, policy: String?): RepositorySettings =
        RepositorySettings(id = id, proxied = listOf(mirrorSettings(reference, policy)))

    /** Built from JSON, the way a configuration file states the policy, so the key and its values are part of the test. */
    private fun mirrorSettings(reference: String, policy: String? = null): MirroredRepositorySettings {
        val policyEntry = policy?.let { ""","checksumPolicy":"$it"""" }.orEmpty()
        return ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readValue(
            """{"reference":"$reference","store":true$policyEntry}""",
            MirroredRepositorySettings::class.java,
        )
    }

    private fun sha256(text: String): String = digest("SHA-256", text)

    private fun sha1(text: String): String = digest("SHA-1", text)

    private fun digest(algorithm: String, text: String): String =
        MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

}
