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

package net.onelitefeather.ingot.nexusimport.plan

import net.onelitefeather.ingot.nexusimport.fake.AdvancingClock
import net.onelitefeather.ingot.nexusimport.fake.FakeNexus
import net.onelitefeather.ingot.nexusimport.fake.FakeReply
import net.onelitefeather.ingot.nexusimport.nexus.JdkNexusTransport
import net.onelitefeather.ingot.nexusimport.nexus.NexusClient
import net.onelitefeather.ingot.nexusimport.nexus.NexusConnection
import net.onelitefeather.ingot.nexusimport.nexus.Secret
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class NexusDiscoveryTest {

    private lateinit var nexus: FakeNexus

    @BeforeEach
    fun setUp() {
        nexus = FakeNexus()
        nexus.serveFixture(NexusClient.REPOSITORIES_PATH, "repositories.json")
        nexus.serveFixture("/service/rest/v1/repositories/maven/hosted/releases", "maven-hosted-releases.json")
        nexus.serveFixture("/service/rest/v1/repositories/maven/proxy/snapshots-proxy", "maven-proxy.json")
        nexus.serveFixture("/service/rest/v1/repositories/maven/group/public-group", "maven-group.json")
        nexus.on(NexusClient.ASSETS_PATH) { request ->
            val repository = request.query["repository"]
            when {
                repository != "releases" -> FakeReply.json("""{"items":[],"continuationToken":null}""")
                request.query["continuationToken"] == null -> FakeReply.json(nexus.fixture("assets-page-1.json"))
                else -> FakeReply.json(nexus.fixture("assets-page-2.json"))
            }
        }
    }

    @AfterEach
    fun tearDown() {
        nexus.close()
    }

    private fun discovery() = NexusDiscovery(
        client = NexusClient(NexusConnection(nexus.baseUri, "importer", Secret("fixture-password")), JdkNexusTransport(), AdvancingClock(), sleeper = {}),
    )

    @Test
    fun `should read the write policy and version policy of a hosted repository`() {
        val releases = discovery().discover().single { it.name == "releases" }

        assertThat(releases.type).isEqualTo("hosted")
        assertThat(releases.writePolicy).isEqualTo("ALLOW_ONCE")
        assertThat(releases.versionPolicy).isEqualTo("RELEASE")
        assertThat(releases.settingsReadable).isTrue
    }

    @Test
    fun `should read the remote URL of a proxy and the members of a group in order`() {
        val facts = discovery().discover()

        assertThat(facts.single { it.name == "snapshots-proxy" }.remoteUrl).isEqualTo("https://repo.example.test/maven2/")
        assertThat(facts.single { it.name == "public-group" }.memberNames).containsExactly("releases", "snapshots-proxy")
    }

    @Test
    fun `should mark the settings unreadable when Nexus answers 403 for them`() {
        nexus.on("/service/rest/v1/repositories/maven/hosted/releases") { FakeReply.status(403) }

        val releases = discovery().discover().single { it.name == "releases" }

        assertThat(releases.settingsReadable).describedAs("a 403 on settings is recorded, not fatal").isFalse
    }

    @Test
    fun `should count the assets and their bytes of every repository across the pages`() {
        val releases = discovery().discover().single { it.name == "releases" }

        assertThat(releases.components).isGreaterThan(0)
        assertThat(releases.bytes).isGreaterThan(0)
    }

    @Test
    fun `should run the dry run with GET requests only and plan the maven repositories`() {
        val plan = NexusDryRun(discovery()).run(selection = null, existingTargets = setOf("releases"))

        assertThat(plan.repositories.map { it.targetName }).contains("releases-nexus")
        assertThat(plan.transferBytes).isGreaterThan(0)
        nexus.assertOnlyReadMethods()
    }
}
