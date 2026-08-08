/*
 * Copyright (c) 2023 dzikoysk
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

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/**
 * The images these tests run. They are built by CI before the suite starts, and named by
 * system property so the same tests work against a local build.
 */
object ImageUnderTest {
    val server: DockerImageName = named("ingot.image")
    val dashboard: DockerImageName = named("ingot.dashboard.image")

    /**
     * The Reposilite release this fork is expected to replace. Raising it is how the
     * upgrade rehearsal is pointed at a newer starting point.
     */
    val upstream: DockerImageName = named("ingot.upstream.image")

    private fun named(property: String): DockerImageName =
        DockerImageName.parse(
            System.getProperty(property)
                ?: error("System property '$property' is not set. These tests run an image that was built beforehand, for example: ./gradlew :reposilite-backend:containerTest -D$property=ingot:ci")
        )
}

/** The port the server listens on unless something tells it otherwise. */
const val DEFAULT_SERVER_PORT: Int = 8080

/**
 * Starts the server image the way a deployment would, and waits until it actually answers.
 *
 * [port] is the port the server is expected to listen on, which is not always the default:
 * a test passing `--port` has to expose the port it asked for, or it waits on one nothing
 * is bound to.
 */
fun serverContainer(
    image: DockerImageName,
    data: DataDirectory? = null,
    port: Int = DEFAULT_SERVER_PORT,
    environment: Map<String, String> = emptyMap(),
    runAs: String? = null,
): GenericContainer<*> =
    GenericContainer(image)
        .withExposedPorts(port)
        .withEnv(environment)
        .apply {
            data?.let { withFileSystemBind(it.path.toString(), "/app/data", BindMode.READ_WRITE) }
            runAs?.let { user -> withCreateContainerCmdModifier { it.withUser(user) } }
        }
        .waitingFor(Wait.forHttp("/").forPort(port).forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(3))

/** Where a started container can be reached from the test process. */
fun GenericContainer<*>.baseUrl(port: Int = DEFAULT_SERVER_PORT): String =
    "http://${host}:${getMappedPort(port)}"

/**
 * A data directory on the host that a container writes into.
 *
 * The server runs as a uid that belongs to nobody on this machine, so everything it leaves
 * behind is unreadable and undeletable here. Both problems are solved the same way, by
 * borrowing a container: [chownTo] to set up the case under test, and [close] to hand the
 * files back so the test framework can clean up after itself.
 */
class DataDirectory(val path: Path, private val image: DockerImageName) : AutoCloseable {
    private val ownUid = Files.getAttribute(path, "unix:uid") as Int
    private val ownGid = Files.getAttribute(path, "unix:gid") as Int

    fun chownTo(uid: Int, gid: Int) {
        runAsRoot("chown", "-R", "$uid:$gid", MOUNT_POINT)
    }

    /** The `uid:gid` a file inside the directory ended up with. */
    fun ownerOf(relativePath: String): String {
        val file = path.resolve(relativePath)
        val uid = Files.getAttribute(file, "unix:uid") as Int
        val gid = Files.getAttribute(file, "unix:gid") as Int
        return "$uid:$gid"
    }

    /**
     * Reads a file the server wrote. That happens through a container too: the file belongs
     * to the service account, and this process is not it.
     */
    fun read(relativePath: String): String =
        GenericContainer(image)
            .withCreateContainerCmdModifier { it.withUser("0:0").withEntrypoint("cat") }
            .withCommand("$MOUNT_POINT/$relativePath")
            .withFileSystemBind(path.toString(), MOUNT_POINT, BindMode.READ_ONLY)
            .withStartupCheckStrategy(OneShotStartupCheckStrategy())
            .use { container ->
                container.start()
                container.logs
            }

    override fun close() {
        runAsRoot("chown", "-R", "$ownUid:$ownGid", MOUNT_POINT)
    }

    private fun runAsRoot(vararg command: String) {
        // Root is pinned deliberately rather than inherited: what the image starts as is
        // itself under test, and a helper that inherited it would turn a failed assertion
        // into a confusing setup error.
        GenericContainer(image)
            .withCreateContainerCmdModifier { it.withUser("0:0").withEntrypoint(command.first()) }
            .withCommand(*command.drop(1).toTypedArray())
            .withFileSystemBind(path.toString(), MOUNT_POINT, BindMode.READ_WRITE)
            .withStartupCheckStrategy(OneShotStartupCheckStrategy())
            .use { it.start() }
    }

    private companion object {
        const val MOUNT_POINT = "/mnt/data"
    }
}

/**
 * Just enough HTTP for these tests. They speak to the image the way a build tool does, so
 * anything richer would be testing a client library instead.
 */
object Http {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun get(url: String, credentials: Pair<String, String>? = null): Response =
        send(request(url, credentials).GET().build())

    fun put(url: String, body: String, credentials: Pair<String, String>? = null): Response =
        send(
            request(url, credentials)
                .header("Content-Type", "application/octet-stream")
                .PUT(BodyPublishers.ofString(body))
                .build()
        )

    fun putJson(url: String, json: String, credentials: Pair<String, String>? = null): Response =
        send(
            request(url, credentials)
                .header("Content-Type", "application/json")
                .PUT(BodyPublishers.ofString(json))
                .build()
        )

    private fun request(url: String, credentials: Pair<String, String>?): HttpRequest.Builder =
        // Normalised, because the server writes its own asset URLs as "/./assets/...". A
        // browser and curl collapse that; HttpClient sends it verbatim and gets a 404, which
        // would fail a test over a difference no user can observe.
        HttpRequest.newBuilder(URI.create(url).normalize())
            .timeout(Duration.ofSeconds(30))
            .apply {
                credentials?.let { (name, secret) ->
                    val encoded = Base64.getEncoder().encodeToString("$name:$secret".toByteArray())
                    header("Authorization", "Basic $encoded")
                }
            }

    private fun send(request: HttpRequest): Response =
        client.send(request, BodyHandlers.ofString()).let { Response(it.statusCode(), it.body()) }

    data class Response(val status: Int, val body: String) {
        val isSuccessful: Boolean get() = status in 200..299
    }
}
