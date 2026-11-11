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

import com.reposilite.maven.ChecksumPolicy.FAIL
import com.reposilite.maven.ChecksumPolicy.IGNORE
import com.reposilite.maven.ChecksumPolicy.WARN
import com.reposilite.maven.ChecksumResult.MISMATCH
import com.reposilite.maven.ChecksumResult.MISSING
import com.reposilite.maven.ChecksumResult.VERIFIED
import com.reposilite.maven.CopyOutcome.REFUSED
import com.reposilite.maven.CopyOutcome.STORED
import com.reposilite.maven.api.LookupRequest
import com.reposilite.maven.api.MirrorIntegrityEvent
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.shared.http.FakeRemoteClientProvider
import com.reposilite.shared.http.RemoteClientProvider
import com.reposilite.shared.notFoundError
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import io.javalin.http.ContentType.TEXT_XML
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import panda.std.asSuccess
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * The checksum results of mirrors with store enabled are written as events and counted. The upstream is an in-memory map,
 * so no request leaves the test, and every test reads the events it raised and the totals of the facade.
 */
internal class MirrorIntegrityEventsTest : MavenSpecification() {

    private val gavPath = "com/example/lib/1.0.0/lib-1.0.0.jar"
    private val gav: Location = gavPath.toLocation()
    private val originalBody = "original bytes of the library"
    private val corruptedBody = "corrupted bytes served by an upstream"

    private val upstream = ConcurrentHashMap<String, String>()

    override fun repositories() = listOf(
        RepositorySettings(id = "warn-mirror", proxied = listOf(mirror("https://warn.example/releases", WARN))),
        RepositorySettings(id = "fail-mirror", proxied = listOf(mirror("https://single.example/releases", FAIL))),
        RepositorySettings(id = "ignore-mirror", proxied = listOf(mirror("https://ignore.example/releases", IGNORE))),
    )

    override fun remoteClientProvider(): RemoteClientProvider = FakeRemoteClientProvider(
        headHandler = { uri, _, _, _ ->
            if (upstream.containsKey(uri)) DocumentInfo(name = uri.substringAfterLast('/'), contentType = TEXT_XML).asSuccess()
            else notFoundError("Not found")
        },
        getHandler = { uri, _, _, _ ->
            upstream[uri]?.byteInputStream()?.asSuccess() ?: notFoundError("Not found")
        },
    )

    @Test
    fun `should raise a mismatch event with outcome stored when the copy does not match under warn`() {
        // given: an upstream whose artifact does not match its checksum file
        publish("https://warn.example/releases", gavPath, corruptedBody)
        publish("https://warn.example/releases", "$gavPath.sha256", sha256(originalBody))
        val events = listenForEvents()

        // when: the artifact is requested through the warn mirror
        assertOk(mavenFacade.findFile(lookup("warn-mirror")))

        // then: the copy is stored and one mismatch event records it
        assertThat(storedContent("warn-mirror")).describedAs("stored copy under warn").isEqualTo(corruptedBody)
        assertThat(events).describedAs("integrity events of one mismatch").hasSize(1)
        with(events.single()) {
            assertThat(result).describedAs("result of a warn mismatch").isEqualTo(MISMATCH)
            assertThat(policy).describedAs("policy of a warn mismatch").isEqualTo(WARN)
            assertThat(outcome).describedAs("outcome of a warn mismatch").isEqualTo(STORED)
            assertThat(algorithm).describedAs("algorithm of a sha256 mismatch").isEqualTo("sha256")
            assertThat(host).describedAs("host of the mismatch").isEqualTo("https://warn.example/releases")
            assertThat(repository).describedAs("repository of the mismatch").isEqualTo("warn-mirror")
            assertThat(path).describedAs("path of the mismatch").isEqualTo(gavPath)
        }
    }

    @Test
    fun `should raise a mismatch event with outcome refused when the copy does not match under fail`() {
        // given: an upstream whose artifact does not match its checksum file
        publish("https://single.example/releases", gavPath, corruptedBody)
        publish("https://single.example/releases", "$gavPath.sha256", sha256(originalBody))
        val events = listenForEvents()

        // when: the artifact is requested through the fail mirror
        val result = mavenFacade.findFile(lookup("fail-mirror"))

        // then: the copy is refused, nothing is stored, and one mismatch event records the refusal
        assertError(result)
        assertThat(storedContent("fail-mirror")).describedAs("stored copy under fail").isNull()
        assertThat(events).describedAs("integrity events of one refused mismatch").hasSize(1)
        assertThat(events.single().result).describedAs("result of a fail mismatch").isEqualTo(MISMATCH)
        assertThat(events.single().outcome).describedAs("outcome of a fail mismatch").isEqualTo(REFUSED)
    }

    @Test
    fun `should raise a missing event and refuse the host when it has no checksum file under fail`() {
        // given: a host that serves the artifact but neither a sha256 nor a sha1 file
        publish("https://single.example/releases", gavPath, originalBody)
        val events = listenForEvents()

        // when: the artifact is requested through the fail mirror
        assertError(mavenFacade.findFile(lookup("fail-mirror")))

        // then: the host is refused and one missing event records it, without an algorithm
        assertThat(events).describedAs("integrity events of a host without checksums").hasSize(1)
        with(events.single()) {
            assertThat(result).describedAs("result of a missing checksum").isEqualTo(MISSING)
            assertThat(outcome).describedAs("outcome of a missing checksum under fail").isEqualTo(REFUSED)
            assertThat(algorithm).describedAs("algorithm of a missing checksum").isEqualTo("none")
        }
    }

    @Test
    fun `should raise a missing event and store the copy when it has no checksum file under warn`() {
        // given: a host that serves the artifact but no checksum file
        publish("https://warn.example/releases", gavPath, originalBody)
        val events = listenForEvents()

        // when: the artifact is requested through the warn mirror
        assertOk(mavenFacade.findFile(lookup("warn-mirror")))

        // then: the copy is stored and one missing event records it
        assertThat(storedContent("warn-mirror")).describedAs("stored copy without checksum under warn").isEqualTo(originalBody)
        assertThat(events).describedAs("integrity events of a host without checksums under warn").hasSize(1)
        assertThat(events.single().result).describedAs("result under warn").isEqualTo(MISSING)
        assertThat(events.single().outcome).describedAs("outcome under warn").isEqualTo(STORED)
    }

    @Test
    fun `should raise no integrity event under the ignore policy`() {
        // given: an ignore mirror whose copy does not match its checksum file
        publish("https://ignore.example/releases", gavPath, corruptedBody)
        publish("https://ignore.example/releases", "$gavPath.sha256", sha256(originalBody))
        val events = listenForEvents()

        // when: the artifact is requested through the ignore mirror
        assertOk(mavenFacade.findFile(lookup("ignore-mirror")))

        // then: nothing is raised and no counter moves
        assertThat(events).describedAs("integrity events under ignore").isEmpty()
        assertThat(mavenFacade.operationTotals().checksumResults.values.flatMap { it.values }.sum())
            .describedAs("counted results under ignore")
            .isZero()
    }

    @Test
    fun `should raise no integrity event for a metadata or checksum file under fail`() {
        // given: a host that serves a metadata file and a checksum file that does not match anything
        publish("https://single.example/releases", "com/example/lib/maven-metadata.xml", "<metadata/>")
        publish("https://single.example/releases", "$gavPath.sha256", "not a checksum at all")
        val events = listenForEvents()

        // when: both files are requested through the fail mirror
        assertOk(mavenFacade.findFile(LookupRequest(UNAUTHORIZED, "fail-mirror", "com/example/lib/maven-metadata.xml".toLocation())))
        assertOk(mavenFacade.findFile(LookupRequest(UNAUTHORIZED, "fail-mirror", "$gavPath.sha256".toLocation())))

        // then: neither file is checked, so neither raises an event
        assertThat(events).describedAs("integrity events of metadata and checksum files").isEmpty()
    }

    @Test
    fun `should count a verified copy under fail without raising an event`() {
        // given: a host whose artifact matches its sha256 file
        publish("https://single.example/releases", gavPath, originalBody)
        publish("https://single.example/releases", "$gavPath.sha256", sha256(originalBody))
        val events = listenForEvents()

        // when: the artifact is requested through the fail mirror
        assertOk(mavenFacade.findFile(lookup("fail-mirror")))

        // then: the verified copy is counted and no event is raised
        assertThat(events).describedAs("integrity events of a verified copy").isEmpty()
        assertThat(mavenFacade.operationTotals().checksumResults.getValue(FAIL).getValue(VERIFIED))
            .describedAs("verified copies under fail")
            .isEqualTo(1L)
    }

    @Test
    fun `should count each checksum result under its policy label`() {
        // given: a warn mismatch and a fail mismatch from two hosts
        publish("https://warn.example/releases", gavPath, corruptedBody)
        publish("https://warn.example/releases", "$gavPath.sha256", sha256(originalBody))
        publish("https://single.example/releases", gavPath, corruptedBody)
        publish("https://single.example/releases", "$gavPath.sha256", sha256(originalBody))

        // when: both mirrors are asked
        assertOk(mavenFacade.findFile(lookup("warn-mirror")))
        assertError(mavenFacade.findFile(lookup("fail-mirror")))

        // then: each result is counted under its own policy, and the other series stay at zero
        val totals = mavenFacade.operationTotals().checksumResults
        assertThat(totals.getValue(WARN).getValue(MISMATCH)).describedAs("warn mismatches").isEqualTo(1L)
        assertThat(totals.getValue(FAIL).getValue(MISMATCH)).describedAs("fail mismatches").isEqualTo(1L)
        assertThat(totals.getValue(WARN).getValue(VERIFIED)).describedAs("warn verified").isZero()
    }

    @Test
    fun `should report all six checksum series at zero before the first fetch`() {
        // when: the totals are read before any fetch
        val totals = mavenFacade.operationTotals().checksumResults

        // then: warn and fail carry every result, each at zero, so the series exist from the first scrape
        assertThat(totals).describedAs("policies with checksum series").containsOnlyKeys(WARN, FAIL)
        listOf(WARN, FAIL).forEach { policy ->
            assertThat(totals.getValue(policy))
                .describedAs("results of policy $policy")
                .containsOnlyKeys(*ChecksumResult.entries.toTypedArray())
                .containsValues(0L)
        }
    }

    private fun listenForEvents(): MutableList<MirrorIntegrityEvent> {
        val events = mutableListOf<MirrorIntegrityEvent>()
        extensions.registerEvent<MirrorIntegrityEvent> { events += it }
        return events
    }

    private fun lookup(repository: String): LookupRequest =
        LookupRequest(UNAUTHORIZED, repository, gav)

    private fun publish(host: String, path: String, body: String) {
        upstream["$host/$path"] = body
    }

    private fun storedContent(repository: String): String? {
        val result = mavenFacade.getRepository(repository)!!.storageProvider.getFile(gav)
        return if (result.isOk) result.get().use { it.readBytes().toString(Charsets.UTF_8) } else null
    }

    private fun mirror(reference: String, policy: ChecksumPolicy): MirroredRepositorySettings =
        MirroredRepositorySettings(reference = reference, store = true, checksumPolicy = policy)

    private fun sha256(text: String): String = digest("SHA-256", text)

    private fun digest(algorithm: String, text: String): String =
        MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

}
