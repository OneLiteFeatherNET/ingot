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

package com.reposilite.image

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.ToStringConsumer
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy

/**
 * The dashboard without a server behind it: the health endpoint answers on its own, and a proxied path reports the failure.
 * The restart of the server must not restart the dashboard, which is the reason the health endpoint does not ask the server.
 */
class DashboardWithoutServerTest {

    companion object {
        private lateinit var dashboard: GenericContainer<*>

        @JvmStatic
        @BeforeAll
        fun start() {
            // Port 9 on the loopback address of the container has no listener, so the connection is refused.
            dashboard = dashboardContainer(network = null, environment = mapOf("INGOT_BACKEND" to "http://127.0.0.1:9")).startOrExplain()
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            dashboard.stop()
        }
    }

    @Test
    @DisplayName("it answers the health endpoint while the server is unreachable")
    fun `answers the health endpoint while the server is unreachable`() {
        val reply = DashboardHttp.send(dashboard.dashboardUrl("/healthz"), accept = "*/*")

        assertThat(reply.status).describedAs("health status with no server").isEqualTo(200)
    }

    @Test
    @DisplayName("it still answers the document while the server is unreachable")
    fun `answers the document while the server is unreachable`() {
        val reply = DashboardHttp.send(dashboard.dashboardUrl("/browse/releases"))

        assertThat(reply.status).describedAs("the document does not depend on the server").isEqualTo(200)
    }

    @Test
    @DisplayName("it reports a proxied path as a bad gateway while the server is unreachable")
    fun `reports a proxied path as a bad gateway`() {
        val reply = DashboardHttp.send(dashboard.dashboardUrl("/api/status/health"), accept = "*/*")

        assertThat(reply.status).describedAs("a proxied path with no server").isEqualTo(502)
    }
}

/** A request body above the configured limit is refused by the dashboard, before the server sees it. */
class DashboardUploadLimitTest {

    companion object {
        private lateinit var network: Network
        private lateinit var backend: GenericContainer<*>
        private lateinit var dashboard: GenericContainer<*>

        @JvmStatic
        @BeforeAll
        fun start() {
            network = Network.newNetwork()
            backend = startBackend(network)
            dashboard = dashboardContainer(
                network,
                environment = mapOf("INGOT_BACKEND" to "http://server:8080", "INGOT_MAX_UPLOAD_SIZE" to "1k"),
            ).startOrExplain()
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            dashboard.stop()
            backend.stop()
            network.close()
        }
    }

    @Test
    @DisplayName("it answers 413 to a body above INGOT_MAX_UPLOAD_SIZE without forwarding it")
    fun `refuses a body above the limit`() {
        val reply = DashboardHttp.send(
            dashboard.dashboardUrl("/releases/com/example/app/1.0/app-1.0.jar"),
            method = "PUT",
            body = ByteArray(2048),
            accept = "*/*",
        )

        assertThat(reply.status).describedAs("a body above the limit").isEqualTo(413)
    }

    @Test
    @DisplayName("it forwards a body within INGOT_MAX_UPLOAD_SIZE")
    fun `forwards a body within the limit`() {
        val reply = DashboardHttp.send(
            dashboard.dashboardUrl("/releases/com/example/app/1.0/app-1.0.jar"),
            method = "PUT",
            body = ByteArray(16),
            accept = "*/*",
        )

        assertThat(reply.status).describedAs("a body within the limit reaches the server, which answers it").isNotEqualTo(413)
    }
}

/**
 * The dashboard below a sub-path, with INGOT_RESOLVER still set as the previous configuration had it. The base path is
 * applied at start, and the proxy strips it, so the server sees its own paths.
 */
class DashboardSubPathTest {

    companion object {
        private lateinit var network: Network
        private lateinit var backend: GenericContainer<*>
        private lateinit var dashboard: GenericContainer<*>

        @JvmStatic
        @BeforeAll
        fun start() {
            network = Network.newNetwork()
            backend = startBackend(network)
            dashboard = dashboardContainer(
                network,
                environment = mapOf(
                    "INGOT_BACKEND" to "http://server:8080",
                    "INGOT_BASE_PATH" to "/ingot/",
                    "INGOT_RESOLVER" to "127.0.0.11",
                ),
            ).startOrExplain()
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            dashboard.stop()
            backend.stop()
            network.close()
        }
    }

    @Test
    @DisplayName("it writes the base path into the document and answers a nested reload below it")
    fun `applies the base path to the document`() {
        val reply = DashboardHttp.send(dashboard.dashboardUrl("/ingot/settings/x"))

        assertThat(reply.status).describedAs("a nested reload below the base path").isEqualTo(200)
        assertThat(reply.body).describedAs("the base element of the document").contains("<base href=\"/ingot/\"")
    }

    @Test
    @DisplayName("it serves the bundle the document names below the base path")
    fun `serves the bundle below the base path`() {
        val document = DashboardHttp.send(dashboard.dashboardUrl("/ingot/"))
        val bundle = Regex("src=\"\\./(assets/[^\"]+\\.js)\"").find(document.body)?.groupValues?.get(1)
        assertThat(bundle).describedAs("the document names a bundle with a relative address").isNotNull()

        val reply = DashboardHttp.send(dashboard.dashboardUrl("/ingot/$bundle"), accept = "*/*")

        assertThat(reply.status).describedAs("the bundle below the base path").isEqualTo(200)
    }

    @Test
    @DisplayName("it proxies the api below the base path with the prefix removed")
    fun `proxies the api below the base path`() {
        val direct = DashboardHttp.send(backend.baseUrl() + "/api/status/health", accept = "*/*")
        val proxied = DashboardHttp.send(dashboard.dashboardUrl("/ingot/api/status/health"), accept = "*/*")

        assertThat(proxied.status).describedAs("status of the proxied api").isEqualTo(direct.status)
        assertThat(proxied.body).describedAs("body of the proxied api").isEqualTo(direct.body)
    }
}

/**
 * A compose file written for the nginx image mounts tmpfs owned by UID 101 on the dashboard. The image keeps no writable
 * state, so the mounts are unused and the container still starts and serves the dashboard.
 */
class DashboardLegacyTmpfsTest {

    companion object {
        private lateinit var dashboard: GenericContainer<*>

        @JvmStatic
        @BeforeAll
        fun start() {
            dashboard = dashboardContainer(network = null, tmpfs = LEGACY_NGINX_TMPFS).startOrExplain()
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            dashboard.stop()
        }
    }

    @Test
    @DisplayName("it starts with the tmpfs mounts of the nginx compose file and serves the document")
    fun `starts with the legacy tmpfs mounts`() {
        val reply = DashboardHttp.send(dashboard.dashboardUrl("/"))

        assertThat(reply.status).describedAs("the document with the legacy mounts present").isEqualTo(200)
    }
}

/** An invalid INGOT_BACKEND stops the start before the dashboard listens, and the message names the variable. */
class DashboardRefusalTest {

    @Test
    @DisplayName("it refuses a backend without a scheme and names INGOT_BACKEND")
    fun `refuses a backend without a scheme`() {
        // The output is followed from the start, because a container that exits during the start has no logs
        // left to read afterwards.
        val output = ToStringConsumer()
        val dashboard = GenericContainer(ImageUnderTest.dashboard)
            .withEnv("INGOT_BACKEND", "ingot-backend:8080")
            .withLogConsumer(output)
            .withStartupCheckStrategy(OneShotStartupCheckStrategy())
            .withStartupTimeout(java.time.Duration.ofMinutes(2))

        assertThrows<Exception>(message = "the container must not stay up with an invalid backend") { dashboard.start() }
        dashboard.stop()

        assertThat(output.toUtf8String()).describedAs("the reason the start was refused").contains("INGOT_BACKEND")
    }
}
