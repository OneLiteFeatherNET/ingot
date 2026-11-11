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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Every fixture the spike wrote is read by the production client, so a fixture that drifts from the model fails here. */
internal class FixtureParsingTest {

    private lateinit var nexus: FakeNexus

    @BeforeEach
    fun setUp() {
        nexus = FakeNexus()
    }

    @AfterEach
    fun tearDown() {
        nexus.close()
    }

    private fun client() = NexusClient(
        NexusConnection(nexus.baseUri, "importer", Secret("fixture-password")),
        JdkNexusTransport(),
        AdvancingClock(),
        sleeper = {},
    )

    @Test
    fun `should parse the repository list fixture into repositories of every format`() {
        // given: the repository fixture
        nexus.serveFixture(NexusClient.REPOSITORIES_PATH, "repositories.json")

        // when: the list is read
        val repositories = client().repositories()

        // then: the formats and types of the fixture are all present
        assertThat(repositories.map { it.format }).contains("maven2", "npm", "docker")
        assertThat(repositories.map { it.type }).contains("hosted", "proxy", "group")
    }

    @Test
    fun `should parse both asset pages and follow the token from the first to the last`() {
        // given: both asset pages
        nexus.on(NexusClient.ASSETS_PATH) { request ->
            FakeReply.json(nexus.fixture(if (request.query["continuationToken"] == null) "assets-page-1.json" else "assets-page-2.json"))
        }
        val client = client()

        // when: both pages are read
        val first = client.assetPage("releases", null)
        val last = client.assetPage("releases", first.continuationToken)

        // then: the items have paths and checksums, and the last page ends the listing
        assertThat(first.items).isNotEmpty
        assertThat(first.items.first().checksums.sha1).describedAs("the fixture carries a sha1").isNotNull
        assertThat(last.continuationToken).isNull()
    }

    @Test
    fun `should read the maven settings fixtures as JSON with the documented keys`() {
        // given: the hosted, proxy and group settings fixtures
        nexus.serveFixture("/service/rest/v1/repositories/maven/hosted/releases", "maven-hosted-releases.json")
        nexus.serveFixture("/service/rest/v1/repositories/maven/proxy/snapshots-proxy", "maven-proxy.json")
        nexus.serveFixture("/service/rest/v1/repositories/maven/group/public-group", "maven-group.json")
        val client = client()

        // when: the settings are read
        val hosted = client.mavenSettings("hosted", "releases")
        val proxy = client.mavenSettings("proxy", "snapshots-proxy")
        val group = client.mavenSettings("group", "public-group")

        // then: the keys the importer reads are present
        assertThat(hosted.path("storage").path("writePolicy").isMissingNode).describedAs("hosted settings carry the write policy").isFalse
        assertThat(proxy.path("proxy").path("remoteUrl").isMissingNode).describedAs("proxy settings carry the remote URL").isFalse
        assertThat(group.path("group").path("memberNames").isArray).describedAs("group settings carry the members").isTrue
    }

    @Test
    fun `should parse the status fixture as an answer of the status endpoint`() {
        // given: the status fixture
        nexus.serveFixture(NexusClient.STATUS_PATH, "status.json")

        // when: the status is requested
        val reply = client().status()
        reply.body.close()

        // then: the endpoint answered 200
        assertThat(reply.status).isEqualTo(200)
    }
}
