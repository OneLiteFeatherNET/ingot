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

package com.reposilite.maven.specification

import com.reposilite.journalist.Journalist
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.api.LookupRequest
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.http.RemoteClient
import com.reposilite.shared.http.RemoteClientProvider
import com.reposilite.shared.http.RemoteCredentials
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import org.junit.jupiter.api.AfterEach
import panda.std.Result
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import java.io.InputStream
import java.net.Proxy

/**
 * Proxy repositories that fetch from a real [UpstreamStub] through the real HTTP client, so the statuses a client gets are
 * the production ones and not those of a fake. Every repository is public and has its timeouts shortened to 100 ms.
 */
internal abstract class UpstreamStatusSpecification : MavenSpecification() {

    protected companion object {
        const val ARTIFACT = "com/example/lib/1.0.0/lib-1.0.0.jar"
        const val POM = "com/example/lib/1.0.0/lib-1.0.0.pom"
        const val METADATA = "com/example/lib/maven-metadata.xml"
    }

    protected val upstream = UpstreamStub()

    /** The real provider under test; the specification wraps it so a path containing `/boom/` throws. */
    protected abstract fun realProvider(): RemoteClientProvider

    override fun remoteClientProvider(): RemoteClientProvider = object : RemoteClientProvider {
        override fun createClient(journalist: Journalist, proxy: Proxy?): RemoteClient =
            ThrowingOnBoom(realProvider().createClient(journalist, proxy))
    }

    @AfterEach
    fun stopUpstream() {
        upstream.close()
    }

    private fun proxy(id: String, host: String, store: Boolean = false, cacheEntries: Int = 0) =
        RepositorySettings(
            id = id,
            visibility = PUBLIC,
            resolutionCacheMaxEntries = cacheEntries,
            proxied = listOf(mirror(host, store)),
        )

    private fun mirror(reference: String, store: Boolean = false) =
        MirroredRepositorySettings(reference = reference, store = store, connectTimeout = 1, readTimeout = 1)

    private fun group(id: String, vararg members: String) =
        RepositorySettings(id = id, visibility = PUBLIC, proxied = members.map { MirroredRepositorySettings(reference = it) })

    override fun repositories() = listOf(
        proxy("ok", "${upstream.baseUrl}/ok"),
        proxy("p404", "${upstream.baseUrl}/s404"),
        proxy("p410", "${upstream.baseUrl}/s410"),
        proxy("p500", "${upstream.baseUrl}/s500"),
        proxy("html", "${upstream.baseUrl}/html"),
        proxy("refused", "${upstream.refusedBaseUrl}/x"),
        proxy("slow", "${upstream.baseUrl}/slow"),
        proxy("boom", "${upstream.baseUrl}/boom", store = true),
        proxy("flaky", "${upstream.baseUrl}/flaky", cacheEntries = 16),
        proxy("cached404", "${upstream.baseUrl}/s404", cacheEntries = 16),
        group("g500-404", "p500", "p404"),
        group("g404-ok", "p404", "ok"),
        group("g500-ok", "p500", "ok"),
        group("grefused-ok", "refused", "ok"),
        group("gslow-ok", "slow", "ok"),
        group("g404-410", "p404", "p410"),
    )

    protected fun artifact(repository: String): Result<*, ErrorResponse> =
        mavenFacade.findFile(LookupRequest(UNAUTHORIZED, repository, ARTIFACT.toLocation()))

    protected fun metadata(repository: String): Result<*, ErrorResponse> =
        mavenFacade.findFile(LookupRequest(UNAUTHORIZED, repository, METADATA.toLocation()))

    protected fun artifactDetails(repository: String): Result<*, ErrorResponse> =
        mavenFacade.findDetails(LookupRequest(UNAUTHORIZED, repository, ARTIFACT.toLocation()))

    protected fun metadataDetails(repository: String): Result<*, ErrorResponse> =
        mavenFacade.findDetails(LookupRequest(UNAUTHORIZED, repository, METADATA.toLocation()))

    protected fun lookup(repository: String, path: String): Result<*, ErrorResponse> =
        mavenFacade.findFile(LookupRequest(UNAUTHORIZED, repository, path.toLocation()))

    protected fun errorOf(result: Result<*, ErrorResponse>): ErrorResponse =
        assertError(result)

    protected fun statusOf(result: Result<*, ErrorResponse>): Int =
        assertError(result).status

    protected fun assertServed(result: Result<*, ErrorResponse>) {
        assertOk(result)
    }

    protected fun location(path: String): Location =
        path.toLocation()

    private class ThrowingOnBoom(private val delegate: RemoteClient) : RemoteClient {

        override fun head(uri: String, credentials: RemoteCredentials?, connectTimeoutInSeconds: Int, readTimeoutInSeconds: Int) =
            delegate.head(uri, credentials, connectTimeoutInSeconds, readTimeoutInSeconds)

        override fun get(uri: String, credentials: RemoteCredentials?, connectTimeoutInSeconds: Int, readTimeoutInSeconds: Int): Result<InputStream, ErrorResponse> {
            if ("/boom/" in uri) {
                throw IllegalStateException("simulated failure inside the fetch")
            }
            return delegate.get(uri, credentials, connectTimeoutInSeconds, readTimeoutInSeconds)
        }

    }

}
