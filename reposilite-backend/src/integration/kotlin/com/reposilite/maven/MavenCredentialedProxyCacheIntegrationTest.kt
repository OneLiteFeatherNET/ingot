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

@file:Suppress("FunctionName")

package com.reposilite.maven

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteRunner
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirrorCredentials
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenIntegrationSpecification
import com.reposilite.shared.extensions.maxAge
import com.reposilite.shared.http.AuthenticationMethod
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.util.Base64
import kong.unirest.core.HeaderNames.ACCEPT_ENCODING
import kong.unirest.core.HeaderNames.CACHE_CONTROL
import kong.unirest.core.HeaderNames.CONTENT_ENCODING
import kong.unirest.core.HeaderNames.VARY
import kong.unirest.core.Unirest.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import panda.std.reactive.ReferenceUtils

private const val CREDENTIALED_PROXY = "cache-credentialed-proxy"
private const val OPEN_PROXY = "cache-open-proxy"
private const val UPSTREAM_LOGIN = "cache-upstream"
private const val UPSTREAM_SECRET = "cache-upstream-secret"
private const val GUARDED_PATH = "/guarded"
private const val OPEN_PATH = "/open"
// Javalin compresses an answer only above its minimum size, so the content is long enough to be compressed
private val FILE_CONTENT = "upstream-content ".repeat(512)

/**
 * An anonymous download through a PUBLIC proxy is answered by the scope of the mirror. A mirror with basic credentials makes
 * the answer private, because the upstream refuses the anonymous readers that the proxy serves.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class MavenCredentialedProxyCacheIntegrationTest : MavenIntegrationSpecification() {

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        // Compression is the strategy that makes a file answer carry Vary, so the tests run with it on
        ReferenceUtils.setValue(localConfiguration.compressionStrategy, "gzip")
    }

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(
                repositories = settings.repositories + listOf(
                    RepositorySettings(
                        id = CREDENTIALED_PROXY,
                        visibility = RepositoryVisibility.PUBLIC,
                        proxied = mutableListOf(
                            MirroredRepositorySettings(
                                reference = ReposiliteRunner.upstreamReference(ReposiliteRunner.UNSTARTED_UPSTREAM_PORT),
                                authorization = MirrorCredentials(AuthenticationMethod.BASIC, UPSTREAM_LOGIN, UPSTREAM_SECRET),
                            )
                        ),
                    ),
                    RepositorySettings(
                        id = OPEN_PROXY,
                        visibility = RepositoryVisibility.PUBLIC,
                        proxied = mutableListOf(
                            MirroredRepositorySettings(reference = ReposiliteRunner.upstreamReference(ReposiliteRunner.UNSTARTED_UPSTREAM_PORT)),
                        ),
                    ),
                )
            )
        }
    }

    @Test
    fun `should answer private to an anonymous download through a proxy with upstream credentials`() {
        // given: an upstream that refuses requests without the mirror credentials, and a proxy that carries them
        withUpstream { port ->
            pointProxiesAt(port)

            // when: the file is downloaded through the credentialed proxy without credentials
            val response = get("$base/$CREDENTIALED_PROXY/gav/artifact.jar").asString()

            // then: the file is served, and the answer is private, so a shared cache does not keep it
            assertThat(response.status).describedAs("status of the anonymous download").isEqualTo(200)
            assertThat(response.body).isEqualTo(FILE_CONTENT)
            assertThat(response.headers.getFirst(CACHE_CONTROL)).isEqualTo("private, max-age=$maxAge")
        }
    }

    @Test
    fun `should keep the public value for an anonymous download through a proxy without upstream credentials`() {
        // given: an upstream path that serves anonymous readers, and a proxy without credentials
        withUpstream { port ->
            pointProxiesAt(port)

            // when: the file is downloaded through the open proxy without credentials
            val response = get("$base/$OPEN_PROXY/gav/artifact.jar").asString()

            // then: the file is served, and the answer stays public
            assertThat(response.status).describedAs("status of the anonymous download").isEqualTo(200)
            assertThat(response.body).isEqualTo(FILE_CONTENT)
            assertThat(response.headers.getFirst(CACHE_CONTROL)).isEqualTo("public, max-age=$maxAge")
        }
    }

    @Test
    fun `should send vary with accept encoding on a compressed file answer`() {
        // given: an upstream path that serves anonymous readers, and a client that accepts gzip
        withUpstream { port ->
            pointProxiesAt(port)

            // when: the file is downloaded through the open proxy with gzip accepted, without decoding the body on the way
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("$base/$OPEN_PROXY/gav/artifact.jar")).header(ACCEPT_ENCODING, "gzip").GET().build(),
                BodyHandlers.discarding(),
            )

            // then: the answer is compressed, and it names its encoding in Vary, so a shared cache keys it by the encoding
            assertThat(response.headers().firstValue(CONTENT_ENCODING).orElse(""))
                .describedAs("content encoding of the answer").isEqualTo("gzip")
            assertThat(response.headers().firstValue(VARY).orElse(""))
                .describedAs("Vary of a compressed answer").containsIgnoringCase("Accept-Encoding")
        }
    }

    /** Starts an in-process upstream on a port the OS assigns, and stops it after [block]. */
    private fun withUpstream(block: (Int) -> Unit) {
        val upstream = Javalin.create { config ->
            config.routes.before { ctx ->
                if (ctx.path().startsWith("$GUARDED_PATH/") && ctx.header("Authorization") != expectedAuthorization()) {
                    throw UnauthorizedResponse()
                }
            }
            config.routes.get("/{repository}/{gav}/{file}") { ctx -> ctx.result(FILE_CONTENT) }
        }.start(0)

        try {
            block(upstream.port())
        } finally {
            upstream.stop()
        }
    }

    private fun expectedAuthorization(): String =
        "Basic " + Base64.getEncoder().encodeToString("$UPSTREAM_LOGIN:$UPSTREAM_SECRET".toByteArray())

    /** Points the credentialed proxy at the guarded path and the open proxy at the anonymous path of the upstream on [port]. */
    private fun pointProxiesAt(port: Int) {
        useFacade<SharedConfigurationFacade>()
            .getDomainSettings<MavenSettings>()
            .update { settings ->
                settings.copy(
                    repositories = settings.repositories.map { repository ->
                        when (repository.id) {
                            CREDENTIALED_PROXY -> repository.copy(
                                proxied = repository.proxied.map { it.copy(reference = "http://localhost:$port$GUARDED_PATH") }
                            )
                            OPEN_PROXY -> repository.copy(
                                proxied = repository.proxied.map { it.copy(reference = "http://localhost:$port$OPEN_PATH") }
                            )
                            else -> repository
                        }
                    }
                )
            }
    }

}
