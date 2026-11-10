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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * Characterises which proxy a mirror client without `httpProxy` uses. Each probe runs in a child JVM, because the
 * environment of the JVM cannot be changed from inside it. The child makes one https request to a host name under
 * `.invalid`, which never resolves, so no request leaves the machine. A fake proxy on a loopback port records whether the
 * request reached it.
 */
internal class MirrorProxyEnvironmentCharacterisationIntegrationTest {

    @Test
    fun `should not send a mirror request through HTTPS_PROXY when the mirror has no httpProxy`() {
        FakeProxy().use { proxy ->
            // when: only the standard variable names a proxy
            val probe = runProbe(environment = mapOf("HTTPS_PROXY" to proxy.url()), jvmArguments = emptyList())

            // then: the probe ran, and the request did not reach the proxy
            assertThat(probe.exitCode).describedAs("exit code of the probe, output: ${probe.output}").isZero()
            assertThat(proxy.requests).describedAs("requests that reached the fake proxy when only HTTPS_PROXY is set").isEmpty()
        }
    }

    @Test
    fun `should send a mirror request through the https proxy host system property when the mirror has no httpProxy`() {
        FakeProxy().use { proxy ->
            // when: the JVM system properties name the proxy
            val probe = runProbe(
                environment = emptyMap(),
                jvmArguments = listOf("-Dhttps.proxyHost=127.0.0.1", "-Dhttps.proxyPort=${proxy.port}"),
            )

            // then: the probe ran, and the request reached the proxy
            assertThat(probe.exitCode).describedAs("exit code of the probe, output: ${probe.output}").isZero()
            assertThat(proxy.requests).describedAs("requests that reached the fake proxy with https.proxyHost set").hasSize(1)
        }
    }

    private fun runProbe(environment: Map<String, String>, jvmArguments: List<String>): ProbeResult {
        val javaBinary = System.getProperty("java.home") + "/bin/java"
        val command = listOf(javaBinary) + jvmArguments + listOf(
            "-cp", System.getProperty("java.class.path"),
            MirrorProxyProbe::class.java.name,
            // The JDK never proxies loopback targets, so the target is a name under .invalid, which never resolves.
            "https://upstream.invalid/probe",
        )
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        // Proxy variables inherited from the machine running the tests would make the probe depend on the host.
        PROXY_VARIABLES.forEach { builder.environment().remove(it) }
        builder.environment().putAll(environment)

        val process = builder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "probe did not finish within $PROBE_TIMEOUT_SECONDS seconds" }
        return ProbeResult(process.exitValue(), output)
    }

    private data class ProbeResult(val exitCode: Int, val output: String)

    /** A loopback proxy that records the first line of every request, answers 502 and closes the connection. */
    private class FakeProxy : Closeable {

        private val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        val port: Int = server.localPort
        val requests = ConcurrentLinkedQueue<String>()

        private val acceptor = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        val requestLine = BufferedReader(InputStreamReader(socket.getInputStream())).readLine() ?: ""
                        requests += requestLine
                        socket.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray())
                    }
                } catch (closed: Exception) {
                    // The server socket was closed by close(), which ends the loop.
                }
            }
        }.apply { isDaemon = true; start() }

        fun url(): String = "http://127.0.0.1:$port"

        override fun close() {
            server.close()
            acceptor.join(PROBE_TIMEOUT_SECONDS * 1000)
        }

    }

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 60L
        val PROXY_VARIABLES = listOf("HTTP_PROXY", "http_proxy", "HTTPS_PROXY", "https_proxy", "NO_PROXY", "no_proxy")
    }

}
