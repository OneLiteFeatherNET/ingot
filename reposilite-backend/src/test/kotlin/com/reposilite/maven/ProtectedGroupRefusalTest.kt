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

import com.reposilite.maven.api.DeployRequest
import com.reposilite.maven.api.LookupRequest
import com.reposilite.maven.api.ProxyRefusedEvent
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.shared.http.FakeRemoteClientProvider
import com.reposilite.shared.http.RemoteClientProvider
import com.reposilite.shared.notFoundError
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.toLocation
import io.javalin.http.ContentType.TEXT_XML
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import panda.std.asSuccess
import java.util.concurrent.ConcurrentHashMap

private const val CENTRAL = "https://central.example/releases"

/**
 * The refusal of a protected group is written once as an event and once in the counters of the group. Hosted answers
 * and plain proxy answers write neither.
 */
internal class ProtectedGroupRefusalTest : MavenSpecification() {

    private val protectedPath = "net/onelitefeather/ingot/1.0/ingot-1.0.jar"
    private val plainPath = "com/example/lib/1.0/lib-1.0.jar"

    private val upstream = ConcurrentHashMap<String, String>()

    override fun repositories(): List<RepositorySettings> = listOf(
        RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
        RepositorySettings(
            id = "central",
            type = RepositoryType.PROXY,
            proxied = listOf(MirroredRepositorySettings(reference = CENTRAL)),
        ),
        RepositorySettings(
            id = "guarded",
            type = RepositoryType.GROUP,
            proxied = listOf(MirroredRepositorySettings(reference = "releases"), MirroredRepositorySettings(reference = "central")),
            protectedGroupPrefixes = listOf("net.onelitefeather"),
        ),
        RepositorySettings(
            id = "guarded-cached",
            type = RepositoryType.GROUP,
            proxied = listOf(MirroredRepositorySettings(reference = "releases"), MirroredRepositorySettings(reference = "central")),
            protectedGroupPrefixes = listOf("net.onelitefeather"),
            resolutionCacheMaxEntries = 128,
        ),
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
    fun `should raise one refusal event for a protected coordinate that only the proxy holds`() {
        // given: the proxy holds the protected artifact and a listener records the refusals
        upstream["$CENTRAL/$protectedPath"] = "proxy copy"
        val refusals = mutableListOf<ProxyRefusedEvent>()
        extensions.registerEvent<ProxyRefusedEvent> { refusals += it }

        // when: the artifact is requested through the guarded group
        assertError(mavenFacade.findFile(LookupRequest(UNAUTHORIZED, "guarded", protectedPath.toLocation())))

        // then: exactly one refusal is raised, naming the repository, the prefix and the skipped proxy
        assertThat(refusals).describedAs("refusal events of one refused request").hasSize(1)
        val refusal = refusals.single()
        assertThat(refusal.repository).describedAs("repository of the refusal").isEqualTo("guarded")
        assertThat(refusal.prefix).describedAs("matched prefix").isEqualTo("net.onelitefeather")
        assertThat(refusal.skippedMembers).describedAs("skipped members").containsExactly("central")
        assertThat(refusal.gav.toString()).describedAs("refused coordinate").isEqualTo(protectedPath)
    }

    @Test
    fun `should raise no refusal event when a hosted member serves the protected coordinate`() {
        // given: the hosted member holds the protected artifact
        assertOk(
            mavenFacade.deployFile(
                DeployRequest(
                    repository = mavenFacade.getRepository("releases")!!,
                    gav = protectedPath.toLocation(),
                    by = "tester",
                    content = "hosted copy".byteInputStream(),
                    generateChecksums = false,
                )
            )
        )
        val refusals = mutableListOf<ProxyRefusedEvent>()
        extensions.registerEvent<ProxyRefusedEvent> { refusals += it }

        // when: the artifact is requested through the guarded group
        assertOk(mavenFacade.findFile(LookupRequest(UNAUTHORIZED, "guarded", protectedPath.toLocation())))

        // then: nothing was prevented, so no refusal is raised
        assertThat(refusals).describedAs("refusal events of a served request").isEmpty()
    }

    @Test
    fun `should count one refusal of the group in the operation totals`() {
        // given: the proxy holds the protected artifact
        upstream["$CENTRAL/$protectedPath"] = "proxy copy"

        // when: the protected artifact is requested once
        assertError(mavenFacade.findFile(LookupRequest(UNAUTHORIZED, "guarded", protectedPath.toLocation())))

        // then: the refusal is counted for the group
        assertThat(mavenFacade.operationTotals().namespaceRefusals)
            .describedAs("refusals by repository after one refused request")
            .containsExactly(entry("guarded", 1L))
    }

    @Test
    fun `should count every refused metadata request, also one answered from the miss cache`() {
        // given: the proxy holds the protected metadata, and the group caches its routing decisions
        val metadata = "net/onelitefeather/maven-metadata.xml".toLocation()
        upstream["$CENTRAL/net/onelitefeather/maven-metadata.xml"] = "<metadata/>"
        val refusals = mutableListOf<ProxyRefusedEvent>()
        extensions.registerEvent<ProxyRefusedEvent> { refusals += it }

        // when: the same metadata is requested twice, the second time from the cached miss
        assertError(mavenFacade.findFile(LookupRequest(UNAUTHORIZED, "guarded-cached", metadata)))
        assertError(mavenFacade.findFile(LookupRequest(UNAUTHORIZED, "guarded-cached", metadata)))

        // then: each request is a refusal, so each one is counted and audited
        assertThat(refusals).describedAs("refusal events of two refused requests").hasSize(2)
        assertThat(mavenFacade.operationTotals().namespaceRefusals)
            .describedAs("refusals by repository after two refused metadata requests")
            .containsExactly(entry("guarded-cached", 2L))
    }

    @Test
    fun `should not count a coordinate that the proxy serves outside the protected prefix`() {
        // given: the proxy holds an artifact outside the protected namespace
        upstream["$CENTRAL/$plainPath"] = "proxy copy"

        // when: the artifact is requested through the guarded group
        assertOk(mavenFacade.findFile(LookupRequest(UNAUTHORIZED, "guarded", plainPath.toLocation())))

        // then: no refusal is counted
        assertThat(mavenFacade.operationTotals().namespaceRefusals)
            .describedAs("refusals by repository for a served plain coordinate")
            .isEmpty()
    }

}
