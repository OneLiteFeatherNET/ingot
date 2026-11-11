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

package net.onelitefeather.ingot.nexusimport.nexus

import net.onelitefeather.ingot.nexusimport.fake.AdvancingClock
import net.onelitefeather.ingot.nexusimport.fake.FakeNexus
import net.onelitefeather.ingot.nexusimport.fake.FakeReply
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

internal class NexusClientTest {

    private lateinit var nexus: FakeNexus
    private lateinit var other: FakeNexus
    private val clock = AdvancingClock()
    private val sleeps = mutableListOf<Duration>()

    @BeforeEach
    fun setUp() {
        nexus = FakeNexus()
        other = FakeNexus()
    }

    @AfterEach
    fun tearDown() {
        nexus.close()
        other.close()
    }

    private fun client(): NexusClient {
        val connection = NexusConnection(nexus.baseUri, "importer", Secret("canary-password"))
        return NexusClient(
            connection = connection,
            transport = JdkNexusTransport(),
            clock = clock,
            sleeper = { duration ->
                sleeps += duration
                clock.advance(duration)
            },
        )
    }

    private fun asset(downloadUrl: String) = NexusAsset(
        repository = "releases",
        path = "com/example/app/1.0/app-1.0.jar",
        downloadUrl = downloadUrl,
        fileSize = 5,
        checksums = NexusChecksums(sha1 = null, md5 = null, sha256 = null, sha512 = null),
        lastModified = null,
    )

    @Test
    fun `should follow the continuation token and never send a page size`() {
        // given: two asset pages, the first one with a continuation token
        nexus.on(NexusClient.ASSETS_PATH) { request ->
            if (request.query["continuationToken"] == null) FakeReply.json(nexus.fixture("assets-page-1.json"))
            else FakeReply.json(nexus.fixture("assets-page-2.json"))
        }
        val client = client()

        // when: both pages are read by following the token
        val first = client.assetPage("releases", null)
        val second = client.assetPage("releases", first.continuationToken)

        // then: the second request carries the token and no page size parameter
        assertThat(first.continuationToken).describedAs("the first page must announce a next page").isNotNull
        assertThat(second.continuationToken).describedAs("the last page has no token").isNull()
        val assetRequests = nexus.requests().filter { it.path == NexusClient.ASSETS_PATH }
        assertThat(assetRequests.map { it.query["continuationToken"] })
            .describedAs("the first request has no token, the second one echoes the first token")
            .containsExactly(null, first.continuationToken)
        assertThat(assetRequests.flatMap { it.query.keys })
            .describedAs("no page size may be sent, Nexus decides it")
            .doesNotContain("limit", "pageSize", "max")
    }

    @Test
    fun `should send the Authorization header only to the connection origin`() {
        // given: a repository list on the connection origin
        nexus.serveFixture(NexusClient.REPOSITORIES_PATH, "repositories.json")

        // when: the list is read
        client().repositories()

        // then: the request carries the Basic credential
        val request = nexus.requests().single()
        assertThat(request.headers["authorization"])
            .describedAs("the connection origin must receive the credential")
            .startsWith("Basic ")
    }

    @Test
    fun `should not send the Authorization header to another origin after a redirect`() {
        // given: a download on the connection origin that redirects to another server
        other.on("/content/app.jar") { FakeReply.bytes("hello".toByteArray()) }
        nexus.on("/repository/releases/app.jar") { FakeReply.redirect(other.baseUri.resolve("/content/app.jar").toString()) }

        // when: the content is downloaded
        val reply = client().download(asset("${nexus.baseUri}/repository/releases/app.jar"))

        // then: the bytes arrive, the first origin got the credential and the other origin did not
        assertThat(reply.body.readBytes()).describedAs("the redirect must be followed").isEqualTo("hello".toByteArray())
        assertThat(nexus.requests().single().headers["authorization"]).isNotNull
        assertThat(other.requests().single().headers["authorization"])
            .describedAs("the credential must not follow a redirect to another origin")
            .isNull()
    }

    @Test
    fun `should refuse a download URL on another origin without sending any request`() {
        // given: an asset whose download URL points to a host that is not the connection origin
        val foreign = asset("http://198.51.100.7/repository/releases/app.jar")

        // when / then: the download is refused and nothing reached the fake Nexus
        assertThatThrownBy { client().download(foreign) }
            .describedAs("a foreign download URL must be refused")
            .isInstanceOf(NexusRefusedException::class.java)
        assertThat(nexus.requests()).describedAs("the connection origin must see no request for a foreign URL").isEmpty()
    }

    @Test
    fun `should wait for the Retry-After delay and then succeed`() {
        // given: a status endpoint that throttles once with Retry-After 2 seconds
        nexus.script(NexusClient.STATUS_PATH, FakeReply.status(429, mapOf("Retry-After" to "2")))
        nexus.on(NexusClient.STATUS_PATH) { FakeReply.status(200) }

        // when: the status is requested
        val reply = client().status()

        // then: the client waited exactly the announced time on the injected sleeper and retried once
        assertThat(reply.status).describedAs("the retry must succeed").isEqualTo(200)
        assertThat(sleeps).describedAs("the wait must follow Retry-After, not the default backoff").contains(Duration.ofSeconds(2))
        assertThat(nexus.requests()).describedAs("one throttled request and one retry").hasSize(2)
    }

    @Test
    fun `should send only GET requests`() {
        // given: the documented answers for the read endpoints
        nexus.serveFixture(NexusClient.STATUS_PATH, "status.json")
        nexus.serveFixture(NexusClient.REPOSITORIES_PATH, "repositories.json")
        nexus.on(NexusClient.ASSETS_PATH) { FakeReply.json(nexus.fixture("assets-page-2.json")) }
        nexus.on("/repository/releases/app.jar") { FakeReply.bytes("hello".toByteArray()) }
        val client = client()

        // when: every read the importer performs is executed
        client.status()
        client.repositories()
        client.assetPage("releases", null)
        client.download(asset("${nexus.baseUri}/repository/releases/app.jar")).body.close()

        // then: the fake saw only read methods
        nexus.assertOnlyReadMethods()
        assertThat(nexus.requests().map { it.method }).describedAs("every request is a GET").containsOnly("GET")
    }
}
