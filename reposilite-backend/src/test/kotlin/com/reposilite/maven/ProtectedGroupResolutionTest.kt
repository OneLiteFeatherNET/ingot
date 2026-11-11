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
import com.reposilite.maven.application.MavenSettings
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

private const val CENTRAL = "https://central.example/releases"

/**
 * A group that protects `net.onelitefeather` serves those coordinates from its hosted members only. The upstream of the
 * proxy member is an in-memory map that records every request, so a test can show that the proxy was never asked.
 */
internal class ProtectedGroupResolutionTest : MavenSpecification() {

    private val protectedPath = "net/onelitefeather/ingot/1.0/ingot-1.0.jar"
    private val protectedGav: Location = protectedPath.toLocation()
    private val hostedCopy = "copy held by the hosted member"
    private val proxyCopy = "copy held by the public proxy"

    private val upstream = ConcurrentHashMap<String, String>()
    private val requestedUris = ConcurrentLinkedQueue<String>()

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
            id = "open",
            type = RepositoryType.GROUP,
            proxied = listOf(MirroredRepositorySettings(reference = "releases"), MirroredRepositorySettings(reference = "central")),
        ),
        RepositorySettings(
            id = "nested",
            type = RepositoryType.GROUP,
            proxied = listOf(MirroredRepositorySettings(reference = "central")),
        ),
        RepositorySettings(
            id = "outer",
            type = RepositoryType.GROUP,
            proxied = listOf(MirroredRepositorySettings(reference = "releases"), MirroredRepositorySettings(reference = "nested")),
            protectedGroupPrefixes = listOf("net.onelitefeather"),
        ),
        // Untyped groups that reference each other: a loop that must end, and is refused for a protected coordinate
        RepositorySettings(
            id = "loop-a",
            proxied = listOf(MirroredRepositorySettings(reference = "loop-b"), MirroredRepositorySettings(reference = "releases")),
            protectedGroupPrefixes = listOf("net.onelitefeather"),
        ),
        RepositorySettings(id = "loop-b", proxied = listOf(MirroredRepositorySettings(reference = "loop-a"))),
    )

    override fun remoteClientProvider(): RemoteClientProvider = FakeRemoteClientProvider(
        headHandler = { uri, _, _, _ ->
            requestedUris += uri
            if (upstream.containsKey(uri)) DocumentInfo(name = uri.substringAfterLast('/'), contentType = TEXT_XML).asSuccess()
            else notFoundError("Not found")
        },
        getHandler = { uri, _, _, _ ->
            requestedUris += uri
            upstream[uri]?.byteInputStream()?.asSuccess() ?: notFoundError("Not found")
        },
    )

    @Test
    fun `should serve a protected coordinate from the hosted member and never ask the proxy`() {
        // given: the hosted member holds the artifact, and the proxy holds another copy of it
        deploy("releases", protectedPath, hostedCopy)
        upstream["$CENTRAL/$protectedPath"] = proxyCopy

        // when: the artifact is requested through the guarded group
        val result = mavenFacade.findFile(lookup("guarded", protectedGav))

        // then: the hosted copy is served, and the proxy received no request
        assertThat(assertOk(result).content.readBytes().toString(Charsets.UTF_8)).describedAs("served copy").isEqualTo(hostedCopy)
        assertThat(requestsTo("central.example")).describedAs("requests to the proxy member").isEmpty()
    }

    @Test
    fun `should answer a protected coordinate that no hosted member holds with the same not found as a plain miss`() {
        // given: only the proxy holds the protected artifact
        upstream["$CENTRAL/$protectedPath"] = proxyCopy
        val plainPath = "com/example/absent/1.0/absent-1.0.jar"

        // when: the protected coordinate and an unprotected one that exists nowhere are requested
        val protectedMiss = assertError(mavenFacade.findFile(lookup("guarded", protectedGav)))
        val plainMiss = assertError(mavenFacade.findFile(lookup("guarded", plainPath.toLocation())))

        // then: both answers have the same status and the same body, once the requested path is taken out
        assertThat(protectedMiss.status).describedAs("status of a refused coordinate").isEqualTo(plainMiss.status)
        assertThat(protectedMiss.message.replace(protectedPath, "<path>"))
            .describedAs("body of a refused coordinate, without its path")
            .isEqualTo(plainMiss.message.replace(plainPath, "<path>"))

        // and: the proxy was never asked for the protected coordinate, though it was asked for the plain one
        assertThat(requestedUris.filter { it.endsWith(protectedPath) }).describedAs("requests to the proxy for the protected coordinate").isEmpty()
        assertThat(requestedUris.filter { it.endsWith("com/example/absent/1.0/absent-1.0.jar") })
            .describedAs("requests to the proxy for the plain coordinate")
            .isNotEmpty()
    }

    @Test
    fun `should skip a member group that holds a proxy at any depth for a protected coordinate`() {
        // given: a nested group holds the proxy, and the outer protected group only sees the nested group
        upstream["$CENTRAL/$protectedPath"] = proxyCopy

        // when: the protected coordinate is requested through the outer group
        val result = mavenFacade.findFile(lookup("outer", protectedGav))

        // then: the coordinate is not found, and the proxy below the nested group was not asked
        assertError(result)
        assertThat(requestsTo("central.example")).describedAs("requests to the proxy below a nested group").isEmpty()
    }

    @Test
    fun `should end a loop of groups and refuse a protected coordinate in it`() {
        // given: two untyped groups that reference each other, and no hosted member holds the artifact
        upstream["$CENTRAL/$protectedPath"] = proxyCopy

        // when: the protected coordinate is requested through the first group of the loop
        val result = mavenFacade.findFile(lookup("loop-a", protectedGav))

        // then: the request ends with a not found, and the proxy was not asked
        assertError(result)
        assertThat(requestsTo("central.example")).describedAs("requests to the proxy through a loop").isEmpty()
    }

    @Test
    fun `should serve an unprotected coordinate of a protected group from the proxy member`() {
        // given: the proxy holds an artifact outside the protected prefix
        val path = "com/example/lib/1.0/lib-1.0.jar"
        upstream["$CENTRAL/$path"] = proxyCopy

        // when: the artifact is requested through the guarded group
        val result = mavenFacade.findFile(lookup("guarded", path.toLocation()))

        // then: the proxy copy is served as before
        assertThat(assertOk(result).content.readBytes().toString(Charsets.UTF_8)).describedAs("served copy").isEqualTo(proxyCopy)
    }

    @Test
    fun `should serve a protected looking coordinate from the proxy when the group sets no prefix`() {
        // given: the proxy holds an artifact under the namespace, and the group sets no prefix
        upstream["$CENTRAL/$protectedPath"] = proxyCopy

        // when: the artifact is requested through the group without protected prefixes
        val result = mavenFacade.findFile(lookup("open", protectedGav))

        // then: the proxy copy is served, as it was before the setting existed
        assertThat(assertOk(result).content.readBytes().toString(Charsets.UTF_8)).describedAs("served copy").isEqualTo(proxyCopy)
    }

    @Test
    fun `should apply a changed list of protected prefixes without a restart`() {
        // given: the group does not protect the namespace yet, and the proxy holds two versions under it
        val first = "net/onelitefeather/ingot/1.0/ingot-1.0.jar"
        val second = "net/onelitefeather/ingot/2.0/ingot-2.0.jar"
        upstream["$CENTRAL/$first"] = proxyCopy
        upstream["$CENTRAL/$second"] = proxyCopy
        assertOk(mavenFacade.findFile(lookup("open", first.toLocation())))

        // when: the operator protects the namespace on the open group, and the second version is requested
        mavenSettings.update(
            MavenSettings(
                repositories = repositories().map { if (it.id == "open") it.copy(protectedGroupPrefixes = listOf("net.onelitefeather")) else it }
            )
        )
        val result = mavenFacade.findFile(lookup("open", second.toLocation()))

        // then: the changed group no longer asks the proxy for the namespace
        assertError(result)
    }

    private fun lookup(repository: String, gav: Location): LookupRequest =
        LookupRequest(UNAUTHORIZED, repository, gav)

    private fun deploy(repository: String, path: String, content: String) {
        assertOk(
            mavenFacade.deployFile(
                DeployRequest(
                    repository = mavenFacade.getRepository(repository)!!,
                    gav = path.toLocation(),
                    by = "tester",
                    content = content.byteInputStream(),
                    generateChecksums = false,
                )
            )
        )
    }

    private fun requestsTo(host: String): List<String> =
        requestedUris.filter { it.contains(host) }

}
