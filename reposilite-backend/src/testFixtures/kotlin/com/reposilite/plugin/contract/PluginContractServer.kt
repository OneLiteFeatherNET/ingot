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

package com.reposilite.plugin.contract

import com.reposilite.Reposilite
import com.reposilite.ReposiliteFactory
import com.reposilite.ReposiliteParameters
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.token.AccessTokenSecurityProvider
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.createDirectories

/**
 * Boots a fresh Ingot instance for a plugin contract test. The instance gets its own working directory, an OS-chosen port
 * and one manager token, and the given plugin jars are copied into its plugins directory before it starts, so the server
 * loads them the way it does in production. Stop it with [close].
 */
class PluginContractServer(
    private val workingDirectory: Path,
    private val pluginJars: List<Path>,
) : AutoCloseable {

    private val client = HttpClient.newHttpClient()

    lateinit var reposilite: Reposilite
        private set

    val base: String
        get() = "http://localhost:${reposilite.parameters.port}"

    /**
     * Starts the instance. [beforeLaunch] receives the working directory, so a test can place files there that the plugin
     * reads during its initialisation.
     */
    fun start(beforeLaunch: (Path) -> Unit = {}): PluginContractServer {
        val parameters = ReposiliteParameters()
        parameters.tokenEntries = arrayOf("$MANAGER:$SECRET")
        parameters.workingDirectoryName = workingDirectory.toAbsolutePath().toString()
        parameters.testEnv = true
        // Port 0: the OS picks a free port and the server reports it back.
        parameters.port = 0
        parameters.tokenHashStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH
        parameters.run()

        val pluginsDirectory = parameters.pluginDirectory.createDirectories()
        pluginJars.forEach { jar -> Files.copy(jar, pluginsDirectory.resolve(jar.fileName.toString())) }
        beforeLaunch(workingDirectory)

        val logger = PrintStreamLogger(System.out, System.err, Channel.WARN, false)
        reposilite = ReposiliteFactory.createReposilite(parameters, logger)
        check(reposilite.launch().isOk) { "The Ingot instance did not start, see the log above" }
        return this
    }

    /** Uploads [content] to [path] below the server root with the manager token. */
    fun deploy(path: String, content: String): HttpResponse<String> =
        client.send(
            authorized(HttpRequest.newBuilder(URI.create("$base/$path")))
                .PUT(HttpRequest.BodyPublishers.ofString(content))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    /** Requests [path] below the server root without credentials. */
    fun get(path: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI.create("$base/$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    override fun close() {
        if (::reposilite.isInitialized) {
            reposilite.shutdown()
        }
    }

    private fun authorized(builder: HttpRequest.Builder): HttpRequest.Builder =
        builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("$MANAGER:$SECRET".toByteArray()))

    private companion object {
        const val MANAGER = "manager"
        const val SECRET = "manager-secret"
    }

}
