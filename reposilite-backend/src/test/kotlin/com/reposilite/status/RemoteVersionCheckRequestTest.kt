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

package com.reposilite.status

import com.reposilite.VERSION
import com.reposilite.journalist.backend.InMemoryLogger
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs the status facade against a local server that records every request, so the tests see what would leave the
 * process without calling the real release endpoint. The server binds an ephemeral port and every test owns its server.
 */
internal class RemoteVersionCheckRequestTest {

    private val received = CopyOnWriteArrayList<RecordedRequest>()
    private lateinit var server: HttpServer

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val query: String?,
        val headers: Map<String, List<String>>,
        val body: String,
    )

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            received.add(
                RecordedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    query = exchange.requestURI.query,
                    headers = exchange.requestHeaders.toMap(),
                    body = body,
                )
            )
            val response = RELEASE_DOCUMENT.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            exchange.close()
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private val releaseUrl: String
        get() = "http://127.0.0.1:${server.address.port}/repos/OneLiteFeatherNET/ingot/releases/latest"

    private fun status(checkEnabled: Boolean) =
        StatusFacade(
            testEnv = false,
            maxThreads = Reference.reference(40),
            status = { true },
            remoteVersionUrl = releaseUrl,
            failureFacade = FailureFacade(InMemoryLogger()),
            remoteVersionCheck = { checkEnabled },
        )

    @Test
    fun `should open no connection and report the running version when the check is off`() {
        // when: the instance status is requested with the check switched off
        val latestVersion = status(checkEnabled = false).fetchInstanceStatus().latestVersion

        // then: the release endpoint is not contacted and the running version is reported as latest
        assertThat(received).`as`("requests to the release endpoint with the check off").isEmpty()
        assertThat(latestVersion).`as`("latest version with the check off").isEqualTo(VERSION)
    }

    @Test
    fun `should ask the release endpoint once for two status requests when the check is on`() {
        // given: a status facade with the check switched on
        val facade = status(checkEnabled = true)

        // when: the instance status is requested twice
        val first = facade.fetchInstanceStatus().latestVersion
        val second = facade.fetchInstanceStatus().latestVersion

        // then: both answers come from one request and carry the version from the release document
        assertThat(received).`as`("requests to the release endpoint within the hour").hasSize(1)
        assertThat(first).`as`("latest version from the release document").isEqualTo("9.9.9")
        assertThat(second).`as`("latest version on the second status request").isEqualTo("9.9.9")
    }

    @Test
    fun `should send a plain get without body, query or a value that names the instance or its version`() {
        // given: a status facade with the check switched on
        val facade = status(checkEnabled = true)

        // when: the instance status is requested
        facade.fetchInstanceStatus()

        // then: the one recorded request carries nothing but the GET to the release path
        assertThat(received).`as`("recorded requests").hasSize(1)
        val request = received.single()
        assertThat(request.method).`as`("method").isEqualTo("GET")
        assertThat(request.path).`as`("path").isEqualTo("/repos/OneLiteFeatherNET/ingot/releases/latest")
        assertThat(request.query).`as`("query").isNull()
        assertThat(request.body).`as`("body").isEmpty()
        assertThat(request.headers.values.flatten())
            .`as`("header values")
            .noneMatch { it.contains(VERSION) }
    }

    private companion object {
        const val RELEASE_DOCUMENT = """{"url":"https://api.github.com/repos/OneLiteFeatherNET/ingot/releases/1","tag_name":"v9.9.9","draft":false}"""
    }

}
